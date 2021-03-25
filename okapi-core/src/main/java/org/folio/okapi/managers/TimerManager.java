package org.folio.okapi.managers;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
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
  private static final String MAP_NAME = "timersMap";
  private final LockedTypedMap2<TimerDescriptor> tenantTimers
      = new LockedTypedMap2<>(TimerDescriptor.class);

  private final boolean local;
  private final Set<String> timers = new HashSet<>();
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
            String moduleKey = seq + TIMER_ENTRY_SEP + md.getId();
            String productKey = seq + TIMER_ENTRY_SEP + md.getProduct();
            if (!timers.contains(moduleKey)) {
              timers.add(moduleKey);
              future = future
                  .compose(y -> tenantTimers.get(tenantId, productKey))
                  .compose(timerDescriptor -> {
                    RoutingEntry newEntry = re.clone(); // do not mess with entries part of Modules
                    if (timerDescriptor == null) {
                      timerDescriptor = new TimerDescriptor();
                      timerDescriptor.setId(productKey);
                    } else {
                      RoutingEntry existingEntry = timerDescriptor.getRoutingEntry();
                      if (existingEntry.getStaticPath().equals(newEntry.getStaticPath())) {
                        // path is the same, so allow existing entries to pop over
                        // TODO: this should only be done if timers were changed by the timer API.
                        newEntry.setUnit(existingEntry.getUnit());
                        newEntry.setDelay(existingEntry.getDelay());
                        newEntry.setSchedule(existingEntry.getSchedule());
                      }
                    }
                    timerDescriptor.setRoutingEntry(newEntry);
                    final TimerDescriptor finalDescriptor = timerDescriptor;
                    return tenantTimers.put(tenantId, productKey, finalDescriptor)
                        .compose(x -> waitTimer(tenantId, finalDescriptor, moduleKey, productKey));
                  });
            }
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

  private void handleTimer(String tenantId, String moduleKey, String productKey) {
    tenantManager.get(tenantId)
        .compose(tenant -> tenantTimers.get(tenantId, productKey)
            .compose(timerDescriptor -> {
              if (timerDescriptor == null) {
                return Future.succeededFuture();
              }
              return tenantManager.getEnabledModules(tenant).compose(list -> {
                String moduleId = moduleKey.substring(moduleKey.indexOf(TIMER_ENTRY_SEP) + 1);
                for (ModuleDescriptor md : list) {
                  if (moduleId.equals(md.getId())) {
                    if (discoveryManager.isLeader()) {
                      fireTimer(tenant, md, timerDescriptor);
                    }
                    return waitTimer(tenantId, timerDescriptor, moduleKey, productKey);
                  }
                }
                // module is no longer enabled, stop waiting.
                timers.remove(moduleKey);
                return Future.succeededFuture();
              });
            })
        )
        .onFailure(cause -> logger.warn("handleTimer {}", cause.getMessage(), cause));
  }

  private Future<Void> waitTimer(String tenantId, TimerDescriptor timerDescriptor, String moduleKey,
                                 String productKey) {
    RoutingEntry routingEntry = timerDescriptor.getRoutingEntry();
    final long delay = routingEntry.getDelayMilliSeconds();
    vertx.setTimer(delay, res -> handleTimer(tenantId, moduleKey, productKey));
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

  /**
   * timer PATCH.
   * @param tenantId tenant identifier
   * @param timerDescriptor timer desecriptor
   * @return
   */
  public Future<Void> patchTimer(String tenantId, TimerDescriptor timerDescriptor) {
    return tenantTimers.getNotFound(tenantId, timerDescriptor.getId()).compose(existing -> {
      RoutingEntry patchEntry = timerDescriptor.getRoutingEntry();
      RoutingEntry existingEntry = existing.getRoutingEntry();
      existingEntry.setSchedule(patchEntry.getSchedule());
      existingEntry.setDelay(patchEntry.getDelay());
      existingEntry.setUnit(patchEntry.getUnit());
      // TODO: notify about change
      return tenantTimers.put(tenantId, timerDescriptor.getId(), existing);
    });
  }
}
