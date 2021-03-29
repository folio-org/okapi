package org.folio.okapi.managers;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.bean.RoutingEntry;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TimerDescriptor;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.util.LockedTypedMap2;

public class TimerManager {

  private final Logger logger = OkapiLogger.get();
  private static final String TIMER_ENTRY_SEP = "_";
  private static final String MAP_NAME = "org.folio.okapi.timer.map";
  private static final String EVENT_NAME = "org.folio.okapi.timer.event";
  private final LockedTypedMap2<TimerDescriptor> tenantTimers
      = new LockedTypedMap2<>(TimerDescriptor.class);
  private final Set<String> timerRunning = new HashSet<>();

  private final boolean local;
  private TenantManager tenantManager;
  private DiscoveryManager discoveryManager;
  private ProxyService proxyService;
  private Vertx vertx;

  public TimerManager(boolean local) {
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
    return tenantTimers.init(vertx, MAP_NAME, local)
        .compose(x ->
            tenantManager.allTenants().compose(list -> {
              Future<Void> future = Future.succeededFuture();
              for (String id : list) {
                future = future
                    .compose(y -> tenantManager.get(id))
                    .compose(this::startTimers);
              }
              return future;
            })
        );
  }

  /**
   * Handle module change for tenant.
   * @param tenantId tenant identifier
   */
  private void tenantChange(String tenantId) {
    tenantManager.get(tenantId).onSuccess(this::startTimers);
  }

  /**
   * enable timers for enabled modules for a tenant.
   * @param tenant Tenant
   * @return async result
   */
  private Future<Void> startTimers(Tenant tenant) {
    String tenantId = tenant.getId();
    return tenantManager.getEnabledModules(tenant).compose(mdList -> {
      Future<Void> future = Future.succeededFuture();
      for (ModuleDescriptor md : mdList) {
        InterfaceDescriptor timerInt = md.getSystemInterface("_timer");
        if (timerInt != null) {
          List<RoutingEntry> routingEntries = timerInt.getAllRoutingEntries();
          int seq = 0;
          for (RoutingEntry re : routingEntries) {
            String timerId = seq + TIMER_ENTRY_SEP + md.getProduct();
            future = future
                .compose(y -> tenantTimers.get(tenantId, timerId))
                .compose(existing -> {
                  // existing patched timer descriptor takes precedence over
                  // updated module
                  if (existing != null && existing.isModified()) {
                    // see if timers already going for this one.
                    if (timerRunning.contains(timerId)) {
                      return Future.succeededFuture();
                    }
                    timerRunning.add(timerId);
                    return waitTimer(tenantId, existing);
                  }
                  // new timer descriptor for module's routing entry
                  TimerDescriptor newTimerDescriptor = new TimerDescriptor();
                  newTimerDescriptor.setId(timerId);
                  newTimerDescriptor.setRoutingEntry(re);
                  // if it's the same and running, do nothing (timers already ongoing)
                  if (timerRunning.contains(timerId) && isSimilar(existing, newTimerDescriptor)) {
                    return Future.succeededFuture();
                  }
                  timerRunning.add(timerId);
                  return tenantTimers.put(tenantId, timerId, newTimerDescriptor)
                      .compose(x -> waitTimer(tenantId, newTimerDescriptor));
                });
            seq++;
          }
        }
      }
      return future;
    });
  }

  /**
   * Fire a timer - invoke module.
   * @param tenant tenant identifier
   * @param md module descriptor of module to invoke
   * @param timerDescriptor timer descriptor in use
   */
  private void fireTimer(Tenant tenant, ModuleDescriptor md, TimerDescriptor timerDescriptor) {
    RoutingEntry routingEntry = timerDescriptor.getRoutingEntry();
    String path = routingEntry.getStaticPath();
    String tenantId = tenant.getId();
    HttpMethod httpMethod = routingEntry.getDefaultMethod(HttpMethod.POST);
    ModuleInstance inst = new ModuleInstance(md, routingEntry, path, httpMethod, true);
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    logger.info("timer {} call start module {} for tenant {}",
        timerDescriptor.getId(), md.getId(), tenantId);
    proxyService.callSystemInterface(headers, tenant, inst, "")
        .onFailure(cause ->
            logger.info("timer call failed to module {} for tenant {} : {}",
                md.getId(), tenantId, cause.getMessage()))
        .onSuccess(res ->
            logger.info("timer call succeeded to module {} for tenant {}",
                md.getId(), tenantId));
  }

  /**
   * Handle a timer timer.
   *
   * <p>This method is called for each timer in each tenant and for each instance in
   * the Okapi cluster. We have no way of stopping a timer.. So in fact this call
   * may result in nothing.. because the timer descriptor is obsolete (patch or module update)
   * @param tenantId tenant identifier
   * @param timerDescriptor descriptor that this handling
   */
  private void handleTimer(String tenantId, TimerDescriptor timerDescriptor) {
    logger.info("timer {} handle for tenant {}", timerDescriptor.getId(), tenantId);
    final String timerId = timerDescriptor.getId();
    tenantManager.get(tenantId)
        .compose(tenant -> tenantTimers.get(tenantId, timerId)
            .compose(currentDescriptor -> {
              // if value has changed, then stop ..
              if (!isSimilar(timerDescriptor, currentDescriptor)) {
                return Future.succeededFuture();
              }
              // this timer is latest and current .. do the work..
              // find module for this timer.. If module is not found, it was disabled
              // in the meantime and timer is stopped.
              return tenantManager.getEnabledModules(tenant).compose(list -> {
                String product = timerId.substring(timerId.indexOf(TIMER_ENTRY_SEP) + 1);
                for (ModuleDescriptor md : list) {
                  if (product.equals(md.getProduct())) {
                    if (discoveryManager.isLeader()) {
                      // only fire timer in one instance (of the Okapi cluster)
                      fireTimer(tenant, md, currentDescriptor);
                    }
                    // roll on.. wait and redo..
                    return waitTimer(tenantId, timerDescriptor);
                  }
                }
                // no module enabled that has this timer entry.. stop this timer..
                return Future.succeededFuture();
              });
            })
        )
        .onFailure(cause -> logger.warn("handleTimer id={} {}", timerId,
            cause.getMessage(), cause));
  }

  /**
   * Handle a timer timer.
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
    if (delay > 0) {
      vertx.setTimer(delay, res -> handleTimer(tenantId, timerDescriptor));
    }
    return Future.succeededFuture();
  }

  /**
   * get timer descriptor.
   * @param tenantID tenant identifier
   * @param timerId timer identifier
   * @return timer descriptor
   */
  public Future<TimerDescriptor> getTimer(String tenantID, String timerId) {
    return tenantTimers.getNotFound(tenantID, timerId);
  }

  /**
   * timer list.
   * @param tenantId tenant identifier
   * @return timer descriptors for the tenant
   */
  public Future<List<TimerDescriptor>> listTimers(String tenantId) {
    return tenantTimers.get(tenantId).map(x -> x != null ? x : Collections.emptyList());
  }

  private static boolean isSimilar(TimerDescriptor a, TimerDescriptor b) {
    if (a == null) {
      return false;
    }
    return Json.encode(a).equals(Json.encode(b));
  }

  /**
   * timer PATCH.
   * @param tenantId tenant identifier
   * @param timerDescriptor timer descriptor
   * @return future
   */
  public Future<Void> patchTimer(String tenantId, TimerDescriptor timerDescriptor) {
    return tenantTimers.getNotFound(tenantId, timerDescriptor.getId())
        .compose(existing -> {
          final String existingJson = Json.encode(existing);

          RoutingEntry patchEntry = timerDescriptor.getRoutingEntry();
          RoutingEntry existingEntry = existing.getRoutingEntry();
          timerDescriptor.setRoutingEntry(existingEntry);
          timerDescriptor.setModified(true);
          existingEntry.setUnit(patchEntry.getUnit());
          existingEntry.setDelay(patchEntry.getDelay());
          existingEntry.setSchedule(patchEntry.getSchedule());

          // if the patch is a no-op, be sure to do nothing
          // if not there could be TWO timers for same "timer"
          String newJson = Json.encode(timerDescriptor);
          if (existingJson.equals(newJson)) {
            return Future.succeededFuture();
          }
          // announce to shared map, then publish so that all instances of Okapi
          // will get a new timer rolling
          // the existing timer will notice that its timerDescriptor's routing entry is
          // obsolete and terminate
          return tenantTimers.put(tenantId, timerDescriptor.getId(), timerDescriptor)
              .onSuccess(x -> {
                JsonObject o = new JsonObject();
                o.put("tenantId", tenantId);
                o.put("timerDescriptor", Json.encode(timerDescriptor));
                EventBus eb = vertx.eventBus();
                eb.publish(EVENT_NAME, o.encode());
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
      TimerDescriptor timerDescriptor =
          Json.decodeValue(o.getString("timerDescriptor"), TimerDescriptor.class);
      waitTimer(o.getString("tenantId"), timerDescriptor);
    });
  }
}
