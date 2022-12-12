package org.folio.okapi.managers;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.bean.RoutingEntry;
import org.folio.okapi.bean.TimerDescriptor;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.service.TimerStore;
import org.folio.okapi.util.LockedTypedMap1;
import org.folio.okapi.util.OkapiError;

public class TimerManager {

  private final Logger logger = OkapiLogger.get();
  private static final String TIMER_ENTRY_SEP = "_";
  private static final String MAP_NAME = "org.folio.okapi.timer.map";
  private static final String EVENT_NAME = "org.folio.okapi.timer.event";
  private final Map<String,LockedTypedMap1<TimerDescriptor>> tenantTimers = new HashMap<>();
  private final Map<String,Long> timerRunning = new HashMap<>();
  private final TimerStore timerStore;

  private final boolean local;
  private TenantManager tenantManager;
  private DiscoveryManager discoveryManager;
  private ProxyService proxyService;
  private Vertx vertx;

  public TimerManager(TimerStore timerStore, boolean local) {
    this.timerStore = timerStore;
    this.local = local;
  }

  /**
   * Initialize timer manager.
   * @param vertx Vert.x handle
   * @return future result
   */
  public Future<Void> init(Vertx vertx, TenantManager tenantManager,
                           DiscoveryManager discoveryManager, ProxyService proxyService) {
    this.vertx = vertx;
    this.tenantManager = tenantManager;
    this.discoveryManager = discoveryManager;
    this.proxyService = proxyService;
    consumePatchTimer();
    tenantManager.setTenantChange(this::tenantChange);
    return tenantManager.allTenants().compose(list -> {
      Future<Void> future = Future.succeededFuture();
      for (String id : list) {
        future = future.compose(y -> startTimers(id, true));
      }
      return future;
    });
  }

  /**
   * Handle module change for tenant.
   * @param tenantId tenant identifier
   */
  private void tenantChange(String tenantId) {
    startTimers(tenantId, false);
  }

  private Future<Void> loadFromStorage(String tenantId) {
    final LockedTypedMap1<TimerDescriptor> timerMap = tenantTimers.get(tenantId);
    return timerStore.getAll().compose(list -> {
          List<Future<Void>> futures = new LinkedList<>();
          String prefix = tenantId + TIMER_ENTRY_SEP;
          for (TimerDescriptor timerDescriptor : list) {
            String tenantTimerId = timerDescriptor.getId();
            if (tenantTimerId.startsWith(prefix)) {
              timerDescriptor.setId(tenantTimerId.substring(prefix.length()));
              if (timerDescriptor.isModified()) {
                futures.add(timerMap.put(timerDescriptor.getId(), timerDescriptor));
              }
            }
          }
          return GenericCompositeFuture.all(futures).mapEmpty();
        }
    );
  }

  private Future<Void> removeStale(String tenantId, List<ModuleDescriptor> mdList) {
    final LockedTypedMap1<TimerDescriptor> timerMap = tenantTimers.get(tenantId);
    return timerMap.getAll().compose(list -> {
      List<Future<Void>> futures = new LinkedList<>();
      for (String timerId : list.keySet()) {
        ModuleDescriptor md = getModuleForTimer(mdList, timerId);
        if (md == null) {
          final String runId = tenantId + TIMER_ENTRY_SEP + timerId;
          Long id = timerRunning.remove(runId);
          if (id != null) {
            vertx.cancelTimer(id);
          }
          timerMap.remove(timerId);
          futures.add(timerStore.delete(runId).mapEmpty());
        }
      }
      return GenericCompositeFuture.all(futures).mapEmpty();
    });
  }

  private Future<Void> handleNew(String tenantId, List<ModuleDescriptor> mdList) {
    final LockedTypedMap1<TimerDescriptor> timerMap = tenantTimers.get(tenantId);
    Future<Void> future = Future.succeededFuture();
    for (ModuleDescriptor md : mdList) {
      InterfaceDescriptor timerInt = md.getSystemInterface("_timer");
      if (timerInt != null) {
        List<RoutingEntry> routingEntries = timerInt.getAllRoutingEntries();
        int seq = 0;
        for (RoutingEntry re : routingEntries) {
          String timerId = md.getProduct() + TIMER_ENTRY_SEP + seq;
          future = future
              .compose(y -> timerMap.get(timerId))
              .compose(existing -> {
                // patched timer descriptor takes precedence over updated module
                final String runId = tenantId + TIMER_ENTRY_SEP + timerId;
                if (existing != null && existing.isModified()) {
                  // see if timers already going for this one.
                  if (timerRunning.containsKey(runId)) {
                    return Future.succeededFuture();
                  }
                  return waitTimer(tenantId, existing);
                }
                // non-patched timer descriptor for module's routing entry
                TimerDescriptor newTimerDescriptor = new TimerDescriptor();
                newTimerDescriptor.setId(timerId);
                newTimerDescriptor.setRoutingEntry(re);
                if (timerRunning.containsKey(runId)) {
                  if (isSimilar(existing, newTimerDescriptor)) {
                    return Future.succeededFuture();
                  }
                  vertx.cancelTimer(timerRunning.get(runId));
                }
                return timerMap.put(timerId, newTimerDescriptor)
                    .compose(x -> waitTimer(tenantId, newTimerDescriptor));
              });
          seq++;
        }
      }
    }
    return future;
  }

  /**
   * enable timers for enabled modules for a tenant.
   * @param tenantId Tenant identifier
   * @param load whether to load from storage
   * @return async result
   */
  private Future<Void> startTimers(String tenantId, boolean load) {
    return tenantManager.getEnabledModules(tenantId).compose(mdList -> {

      Future<Void> future = Future.succeededFuture();
      if (!tenantTimers.containsKey(tenantId)) {
        LockedTypedMap1<TimerDescriptor> timerMap = new LockedTypedMap1<>(TimerDescriptor.class);
        tenantTimers.put(tenantId, timerMap);
        future = future.compose(x -> timerMap.init(vertx, MAP_NAME + "." + tenantId, local));
      }
      if (load) {
        future = future.compose(x -> loadFromStorage(tenantId));
      }
      return future
          .compose(x -> removeStale(tenantId, mdList))
          .compose(x -> handleNew(tenantId, mdList));
    });
  }

  /**
   * Fire a timer - invoke module.
   * @param tenantId tenant identifier
   * @param md module descriptor of module to invoke
   * @param timerDescriptor timer descriptor in use
   */
  private void fireTimer(String tenantId, ModuleDescriptor md, TimerDescriptor timerDescriptor) {
    RoutingEntry routingEntry = timerDescriptor.getRoutingEntry();
    String path = routingEntry.getStaticPath();
    HttpMethod httpMethod = routingEntry.getDefaultMethod(HttpMethod.POST);
    ModuleInstance inst = new ModuleInstance(md, routingEntry, path, httpMethod, true);
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    logger.info("timer {} call start module {} for tenant {}",
        timerDescriptor.getId(), md.getId(), tenantId);
    proxyService.callSystemInterface(headers, tenantId, inst, "")
        .onFailure(cause ->
            logger.info("timer call failed to module {} for tenant {} : {}",
                md.getId(), tenantId, cause.getMessage()))
        .onSuccess(res ->
            logger.info("timer call succeeded to module {} for tenant {}",
                md.getId(), tenantId));
  }

  Future<ModuleDescriptor> getModuleForTimer(String tenantId, String timerId) {
    return tenantManager.getEnabledModules(tenantId)
        .map(list -> getModuleForTimer(list, timerId))
        .recover(cause -> Future.succeededFuture(null));
  }

  ModuleDescriptor getModuleForTimer(List<ModuleDescriptor> list, String timerId) {
    String product = timerId.substring(0, timerId.indexOf(TIMER_ENTRY_SEP));
    int seq = Integer.parseInt(timerId.substring(timerId.indexOf(TIMER_ENTRY_SEP) + 1));
    for (ModuleDescriptor md : list) {
      if (product.equals(md.getProduct())) {
        InterfaceDescriptor timerInt = md.getSystemInterface("_timer");
        if (timerInt != null) {
          List<RoutingEntry> routingEntries = timerInt.getAllRoutingEntries();
          if (seq < routingEntries.size()) {
            return md;
          }
        }
      }
    }
    return null;
  }

  /**
   * Handle a timer timer.
   *
   * <p>This method is called for each timer in each tenant and for each instance in
   * the Okapi cluster.
   * @param tenantId tenant identifier
   * @param timerId timer identifier
   */
  private void handleTimer(String tenantId, String timerId) {
    logger.info("timer {} handle for tenant {}", timerId, tenantId);
    tenantTimers.get(tenantId).get(timerId)
        .compose(timerDescriptor -> {
          // this timer is latest and current .. do the work ...
          // find module for this timer.. If module is not found, it was disabled
          // in the meantime and timer is stopped.
          return getModuleForTimer(tenantId, timerId).compose(md -> {
            if (md == null) {
              final String runId = tenantId + TIMER_ENTRY_SEP + timerId;
              timerRunning.remove(runId);
              return Future.succeededFuture();
            }
            if (discoveryManager.isLeader()) {
              // only fire timer in one instance (of the Okapi cluster)
              fireTimer(tenantId, md, timerDescriptor);
            }
            // roll on.. wait and redo..
            return waitTimer(tenantId, timerDescriptor);
          });
        })
        .onFailure(cause -> logger.warn("handleTimer id={} {}", timerId,
            cause.getMessage(), cause));
  }

  /**
   * Wait for timer.
   *
   * <p>This method is called for each timer in each tenant and for each instance in
   * the Okapi cluster. If the tenant descriptor has a zero delay, that will
   * stop/disable the timer.
   * @param tenantId tenant identifier
   * @param timerDescriptor descriptor that this handling
   */
  private Future<Void> waitTimer(String tenantId, TimerDescriptor timerDescriptor) {
    RoutingEntry routingEntry = timerDescriptor.getRoutingEntry();
    final long delay = routingEntry.getDelayMilliSeconds();
    final String runId = tenantId + TIMER_ENTRY_SEP + timerDescriptor.getId();
    final String timerId = timerDescriptor.getId();
    logger.info("waitTimer {} delay {} for tenant {}", timerDescriptor.getId(), delay, tenantId);
    if (delay > 0) {
      timerRunning.put(runId, vertx.setTimer(delay, res -> handleTimer(tenantId, timerId)));
    } else {
      timerRunning.remove(runId);
    }
    return Future.succeededFuture();
  }

  /**
   * get timer descriptor.
   * @param tenantId tenant identifier
   * @param timerId timer identifier
   * @return timer descriptor
   */
  public Future<TimerDescriptor> getTimer(String tenantId, String timerId) {
    LockedTypedMap1<TimerDescriptor> timerMap = tenantTimers.get(tenantId);
    if (timerMap == null) {
      return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND, tenantId));
    }
    return timerMap.getNotFound(timerId);
  }

  /**
   * timer list.
   * @param tenantId tenant identifier
   * @return timer descriptors for the tenant
   */
  public Future<Collection<TimerDescriptor>> listTimers(String tenantId) {
    LockedTypedMap1<TimerDescriptor> timerMap = tenantTimers.get(tenantId);
    if (timerMap == null) {
      return Future.succeededFuture(Collections.emptyList());
    }
    return timerMap.getAll().map(LinkedHashMap::values);
  }

  static boolean isSimilar(TimerDescriptor a, TimerDescriptor b) {
    if (a == null) {
      return false;
    }
    return Json.encode(a).equals(Json.encode(b));
  }

  static boolean isPatchReset(RoutingEntry patchEntry) {
    return patchEntry.getDelay() == null && patchEntry.getUnit() == null
        && patchEntry.getSchedule() == null;
  }

  /**
   * timer PATCH.
   * @param tenantId tenant identifier
   * @param timerDescriptor timer descriptor
   * @return future
   */
  public Future<Void> patchTimer(String tenantId, TimerDescriptor timerDescriptor) {
    return getTimer(tenantId, timerDescriptor.getId())
        .compose(existing -> {
          final String existingJson = Json.encode(existing);
          final String timerId = timerDescriptor.getId();

          RoutingEntry patchEntry = timerDescriptor.getRoutingEntry();
          Future<TimerDescriptor> future;
          if (isPatchReset(patchEntry)) {
            // reset to original value of module descriptor
            future = tenantManager.getEnabledModules(tenantId).compose(mdList -> {
              timerDescriptor.setModified(false);
              for (ModuleDescriptor md : mdList) {
                InterfaceDescriptor timerInt = md.getSystemInterface("_timer");
                if (timerInt != null) {
                  List<RoutingEntry> routingEntries = timerInt.getAllRoutingEntries();
                  int seq = 0;
                  for (RoutingEntry re : routingEntries) {
                    String gotTimerId = md.getProduct() + TIMER_ENTRY_SEP + seq;
                    if (gotTimerId.equals(timerId)) {
                      timerDescriptor.setRoutingEntry(re);
                      return Future.succeededFuture(timerDescriptor);
                    }
                    seq++;
                  }
                }
              }
              return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND, timerId));
            });
          } else {
            RoutingEntry existingEntry = existing.getRoutingEntry();
            timerDescriptor.setRoutingEntry(existingEntry);
            timerDescriptor.setModified(true);
            existingEntry.setUnit(patchEntry.getUnit());
            existingEntry.setDelay(patchEntry.getDelay());
            existingEntry.setSchedule(patchEntry.getSchedule());
            future = Future.succeededFuture(timerDescriptor);
          }
          return future.compose(newDescriptor -> {
            String newJson = Json.encode(newDescriptor);
            if (existingJson.equals(newJson)) {
              return Future.succeededFuture();
            }
            TimerDescriptor newDescriptorStorage = new JsonObject(newJson)
                .mapTo(TimerDescriptor.class);
            String newId = tenantId + TIMER_ENTRY_SEP + newDescriptor.getId();
            newDescriptorStorage.setId(newId);
            return timerStore.put(newDescriptorStorage)
                .compose(y -> tenantTimers.get(tenantId).put(newDescriptor.getId(), newDescriptor)
                .onSuccess(x -> {
                  JsonObject o = new JsonObject();
                  o.put("tenantId", tenantId);
                  o.put("timerDescriptor", newJson);
                  EventBus eb = vertx.eventBus();
                  eb.publish(EVENT_NAME, o.encode());
                }));
          });
        });
  }

  /**
   * Consume patch event and start a new timer ..
   */
  private void consumePatchTimer() {
    EventBus eb = vertx.eventBus();
    eb.consumer(EVENT_NAME, res -> {
      JsonObject o = new JsonObject((String) res.body());
      String tenantId = o.getString("tenantId");
      String timerDescriptorVal = o.getString("timerDescriptor");
      TimerDescriptor timerDescriptor = Json.decodeValue(timerDescriptorVal, TimerDescriptor.class);
      final String runId = tenantId + TIMER_ENTRY_SEP + timerDescriptor.getId();
      Long id = timerRunning.get(runId);
      if (id != null) {
        vertx.cancelTimer(id);
      }
      waitTimer(tenantId, timerDescriptor);
    });
  }
}
