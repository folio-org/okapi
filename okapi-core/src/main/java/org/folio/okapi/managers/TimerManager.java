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
import java.util.Objects;
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
import org.folio.okapi.util.JsonDecoder;
import org.folio.okapi.util.LockedTypedMap1;
import org.folio.okapi.util.OkapiError;
import org.folio.okapi.util.TenantProductSeq;

public class TimerManager {
  private static final Logger logger = OkapiLogger.get();
  private static final String MAP_NAME = "org.folio.okapi.timer.map";
  private static final String EVENT_NAME = "org.folio.okapi.timer.event";
  /**
   * Maps tenantId to {@code Map<tenant_product_seq, TimerDescriptor>}.
   * tenant_product_seq is like "test_tenant_mod-foo_2".
   */
  private final Map<String,LockedTypedMap1<TimerDescriptor>> tenantTimers = new HashMap<>();
  /**
   * Maps tenant_product_seq to timer id, tenantid_product_seq is like "test_tenant_mod-foo_2".
   */
  private final Map<String,Long> timerRunning = new HashMap<>();
  /**
   * TimerDescriptor database storage, the id is tenantid_product_seq like "test_tenant_mod-foo_2".
   */
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
      for (String tenantId : list) {
        future = future.compose(y -> startTimers(tenantId, true));
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
    return get(timerStore, tenantId).compose(list -> {
          List<Future<Void>> futures = new LinkedList<>();
          for (TimerDescriptor timerDescriptor : list) {
            if (timerDescriptor.isModified()) {
              futures.add(timerMap.put(timerDescriptor.getId(), timerDescriptor));
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
      for (String tenantPoductSeq : list.keySet()) {
        ModuleDescriptor md = getModuleForTimer(mdList, tenantPoductSeq);
        if (md == null) {
          Long id = timerRunning.remove(tenantPoductSeq);
          if (id != null) {
            vertx.cancelTimer(id);
          }
          timerMap.remove(tenantPoductSeq);
          futures.add(timerStore.delete(tenantPoductSeq).mapEmpty());
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
      if (timerInt == null) {
        continue;
      }
      List<RoutingEntry> routingEntries = timerInt.getAllRoutingEntries();
      int seq = 0;
      for (RoutingEntry re : routingEntries) {
        var tenantProductSeq = new TenantProductSeq(tenantId, md.getProduct(), seq).toString();
        future = future
            .compose(y -> timerMap.get(tenantProductSeq))
            .compose(existing -> {
              // patched timer descriptor takes precedence over updated module
              if (existing != null && existing.isModified()) {
                // see if timers already going for this one.
                if (timerRunning.containsKey(tenantProductSeq)) {
                  return Future.succeededFuture();
                }
                waitTimer(tenantId, existing);
                return Future.succeededFuture();
              }
              // non-patched timer descriptor for module's routing entry
              TimerDescriptor newTimerDescriptor = new TimerDescriptor();
              newTimerDescriptor.setId(tenantProductSeq);
              newTimerDescriptor.setRoutingEntry(re);
              if (timerRunning.containsKey(tenantProductSeq)) {
                if (isSimilar(existing, newTimerDescriptor)) {
                  return Future.succeededFuture();
                }
                vertx.cancelTimer(timerRunning.get(tenantProductSeq));
              }
              return timerMap.put(tenantProductSeq, newTimerDescriptor)
                  .map(x -> {
                    waitTimer(tenantId, newTimerDescriptor);
                    return null;
                  });
            });
        seq++;
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
            logger.info("timer {} call failed to module {} for tenant {} : {}",
                timerDescriptor.getId(), md.getId(), tenantId, cause.getMessage()))
        .onSuccess(res ->
            logger.info("timer {} call succeeded to module {} for tenant {}",
                timerDescriptor.getId(), md.getId(), tenantId));
  }

  Future<ModuleDescriptor> getModuleForTimer(String tenantId, String tenantProductSeq) {
    return tenantManager.getEnabledModules(tenantId)
        .map(list -> getModuleForTimer(list, tenantProductSeq))
        .recover(cause -> Future.succeededFuture(null));
  }

  ModuleDescriptor getModuleForTimer(List<ModuleDescriptor> list, String tenantProductSeqString) {
    TenantProductSeq tenantProductSeq;
    try {
      tenantProductSeq = new TenantProductSeq(tenantProductSeqString);
    } catch (RuntimeException e) {
      logger.error("Invalid id of timer: {}", tenantProductSeqString, e);
      return null;
    }
    for (ModuleDescriptor md : list) {
      if (tenantProductSeq.getProduct().equals(md.getProduct())) {
        InterfaceDescriptor timerInt = md.getSystemInterface("_timer");
        if (timerInt != null) {
          List<RoutingEntry> routingEntries = timerInt.getAllRoutingEntries();
          if (tenantProductSeq.getSeq() < routingEntries.size()) {
            return md;
          }
        }
      }
    }
    return null;
  }

  /**
   * Handle a timer.
   *
   * <p>This method is called for each timer in each tenant and for each instance in
   * the Okapi cluster.
   * @param tenantId tenant identifier
   * @param timerId timer identifier
   */
  private void handleTimer(String tenantId, String tenantProductSeq) {
    logger.info("timer {} handle for tenant {}", tenantProductSeq, tenantId);
    tenantTimers.get(tenantId).get(tenantProductSeq)
        .compose(timerDescriptor ->
            // this timer is latest and current ... do the work ...
            // find module for this timer. If module is not found, it was disabled
            // in the meantime and timer is stopped.
            getModuleForTimer(tenantId, tenantProductSeq).onSuccess(md -> {
              if (md == null) {
                timerRunning.remove(tenantProductSeq);
                return;
              }
              if (discoveryManager.isLeader()) {
                // only fire timer in one instance (of the Okapi cluster)
                fireTimer(tenantId, md, timerDescriptor);
              }
              // roll on.. wait and redo..
              waitTimer(tenantId, timerDescriptor);
            })
        )
        .onFailure(cause -> logger.warn("handleTimer id={} {}", tenantProductSeq,
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
  private void waitTimer(String tenantId, TimerDescriptor timerDescriptor) {
    RoutingEntry routingEntry = timerDescriptor.getRoutingEntry();
    final long delay = routingEntry.getDelayMilliSeconds();
    final String tenantProductSeq = timerDescriptor.getId();
    logger.info("waitTimer {} delay {} for tenant {}", tenantProductSeq, delay, tenantId);
    if (delay > 0) {
      long timer = vertx.setTimer(delay, res -> handleTimer(tenantId, tenantProductSeq));
      timerRunning.put(tenantProductSeq, timer);
    } else {
      var timer = timerRunning.remove(tenantProductSeq);
      if (timer != null) {
        vertx.cancelTimer(timer);
      }
    }
  }

  /**
   * get timer descriptor.
   * @param tenantId tenant identifier
   * @param productSeq timer identifier like mod-foo_0
   * @return timer descriptor with id as productSeq like mod-foo_0
   */
  public Future<TimerDescriptor> getTimer(String tenantId, String productSeq) {
    LockedTypedMap1<TimerDescriptor> timerMap = tenantTimers.get(tenantId);
    if (timerMap == null) {
      return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND, tenantId));
    }
    var tenantProductSeq = new TenantProductSeq(tenantId, productSeq).toString();
    return timerMap.getNotFound(tenantProductSeq)
        .map(timerDescriptor -> {
          if (!productSeq.equals(timerDescriptor.getId())) {
            timerDescriptor = timerDescriptor.copy();  // shallow copy
            // replace [tenant]_[product]_[seq] with [product]_[seq]
            timerDescriptor.setId(productSeq);
          }
          return timerDescriptor;
        });
  }

  /**
   * timer list.
   * @param tenantId tenant identifier
   * @return timer descriptors for the tenant with id as productSeq like mod-foo_0
   */
  public Future<Collection<TimerDescriptor>> listTimers(String tenantId) {
    LockedTypedMap1<TimerDescriptor> timerMap = tenantTimers.get(tenantId);
    if (timerMap == null) {
      return Future.succeededFuture(Collections.emptyList());
    }
    return timerMap.getAll()
        .map(LinkedHashMap::values)
        .map(TenantProductSeq::stripTenantIdFromTimerId);
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
   * @param timerDescriptor timer descriptor; the id is [product]_[seq], for example mod_foo_2
   */
  public Future<Void> patchTimer(String tenantId, TimerDescriptor timerDescriptor) {
    return getTimer(tenantId, timerDescriptor.getId())
        .compose(existing -> {
          final var tenantProductSeq = new TenantProductSeq(tenantId, timerDescriptor.getId());
          timerDescriptor.setId(tenantProductSeq.toString());
          final String existingJson = Json.encode(existing);
          RoutingEntry patchEntry = timerDescriptor.getRoutingEntry();
          Future<TimerDescriptor> future;
          if (isPatchReset(patchEntry)) {
            // reset to original value of module descriptor
            future = tenantManager.getEnabledModules(tenantId).compose(mdList -> {
              timerDescriptor.setModified(false);
              for (ModuleDescriptor md : mdList) {
                if (! md.getProduct().equals(tenantProductSeq.getProduct())) {
                  continue;
                }
                InterfaceDescriptor timerInt = md.getSystemInterface("_timer");
                if (timerInt == null) {
                  continue;
                }
                List<RoutingEntry> routingEntries = timerInt.getAllRoutingEntries();
                if (tenantProductSeq.getSeq() >= routingEntries.size()) {
                  continue;
                }
                RoutingEntry re = routingEntries.get(tenantProductSeq.getSeq());
                timerDescriptor.setRoutingEntry(re);
                return Future.succeededFuture(timerDescriptor);
              }
              return Future.failedFuture(
                  new OkapiError(ErrorType.NOT_FOUND, timerDescriptor.getId()));
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
   * Consume patch event and start a new timer ...
   */
  private void consumePatchTimer() {
    EventBus eb = vertx.eventBus();
    eb.consumer(EVENT_NAME, res -> {
      JsonObject o = new JsonObject((String) res.body());
      String tenantId = o.getString("tenantId");
      String timerDescriptorVal = o.getString("timerDescriptor");
      TimerDescriptor timerDescriptor =
          JsonDecoder.decode(timerDescriptorVal, TimerDescriptor.class);
      Long id = timerRunning.get(timerDescriptor.getId());
      if (id != null) {
        vertx.cancelTimer(id);
      }
      waitTimer(tenantId, timerDescriptor);
    });
  }

  static boolean belongs(TimerDescriptor timerDescriptor, String tenantId) {
    try {
      var tenantProductSeq = new TenantProductSeq(timerDescriptor.getId());
      return Objects.equals(tenantProductSeq.getTenantId(), tenantId);
    } catch (RuntimeException e) {
      var id = timerDescriptor == null ? "null" : timerDescriptor.getId();
      logger.warn("Comparing TimerDescriptor with id={} and tenantId={}", id, tenantId, e);
      return false;
    }
  }

  static Future<List<TimerDescriptor>> get(TimerStore timerStore, String tenantId) {
    return timerStore.getAll()
        .map(list -> {
          list.removeIf(timerDescriptor -> ! belongs(timerDescriptor, tenantId));
          return list;
        });
  }
}
