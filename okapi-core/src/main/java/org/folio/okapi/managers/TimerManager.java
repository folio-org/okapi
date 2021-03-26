package org.folio.okapi.managers;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import java.util.Collections;
import java.util.List;
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
  private static final String MAP_NAME = "timersMap";
  private final LockedTypedMap2<TimerDescriptor> tenantTimers
      = new LockedTypedMap2<>(TimerDescriptor.class);

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

  private void tenantChange(String tenantId) {
    tenantManager.get(tenantId).onSuccess(this::startTimers);
  }

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
                  TimerDescriptor newTimerDescriptor = new TimerDescriptor();
                  newTimerDescriptor.setId(timerId);
                  newTimerDescriptor.setRoutingEntry(re);
                  if (isSimilar(existing, newTimerDescriptor)) {
                    return Future.succeededFuture();
                  }
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

  private void fireTimer(Tenant tenant, ModuleDescriptor md, TimerDescriptor timerDescriptor) {
    RoutingEntry routingEntry = timerDescriptor.getRoutingEntry();
    String path = routingEntry.getStaticPath();
    String tenantId = tenant.getId();
    HttpMethod httpMethod = routingEntry.getDefaultMethod(HttpMethod.POST);
    ModuleInstance inst = new ModuleInstance(md, routingEntry, path, httpMethod, true);
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    logger.info("timer call start module {} for tenant {}", md.getId(), tenantId);
    proxyService.callSystemInterface(headers, tenant, inst, "")
        .onFailure(cause ->
            logger.info("timer call failed to module {} for tenant {} : {}",
                md.getId(), tenantId, cause.getMessage()))
        .onSuccess(res ->
            logger.info("timer call succeeded to module {} for tenant {}",
                md.getId(), tenantId));
  }

  private void handleTimer(String tenantId, TimerDescriptor timerDescriptor) {
    final String timerId = timerDescriptor.getId();
    tenantManager.get(tenantId)
        .compose(tenant -> tenantTimers.get(tenantId, timerId)
            .compose(currentDescriptor -> {
              if (!isSimilar(timerDescriptor, currentDescriptor)) {
                return Future.succeededFuture();
              }
              return tenantManager.getEnabledModules(tenant).compose(list -> {
                String product = timerId.substring(timerId.indexOf(TIMER_ENTRY_SEP) + 1);
                for (ModuleDescriptor md : list) {
                  if (product.equals(md.getProduct())) {
                    if (discoveryManager.isLeader()) {
                      fireTimer(tenant, md, currentDescriptor);
                    }
                    return waitTimer(tenantId, timerDescriptor);
                  }
                }
                // timer stopping...
                return Future.succeededFuture();
              });
            })
        )
        .onFailure(cause -> logger.warn("handleTimer id={} {}", timerId,
            cause.getMessage(), cause));
  }

  private Future<Void> waitTimer(String tenantId, TimerDescriptor timerDescriptor) {
    RoutingEntry routingEntry = timerDescriptor.getRoutingEntry();
    final long delay = routingEntry.getDelayMilliSeconds();
    if (delay > 0) {
      vertx.setTimer(delay, res -> handleTimer(tenantId, timerDescriptor));
    }
    return Future.succeededFuture();
  }

  /**
   * timer get.
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
          RoutingEntry patchEntry = timerDescriptor.getRoutingEntry();
          RoutingEntry existingEntry = existing.getRoutingEntry();

          patchEntry.setPathPattern(existingEntry.getPathPattern());
          patchEntry.setPath(existingEntry.getPath());
          patchEntry.setMethods(existingEntry.getMethods());
          patchEntry.setPermissionsRequired(existingEntry.getPermissionsRequired());
          patchEntry.setModulePermissions(existingEntry.getModulePermissions());

          if (isSimilar(existing, timerDescriptor)) {
            return Future.succeededFuture();
          }
          return tenantTimers.put(tenantId, timerDescriptor.getId(), timerDescriptor)
              .compose(x -> waitTimer(tenantId, timerDescriptor));
        });
  }
}
