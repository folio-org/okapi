package org.folio.okapi.managers;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.bean.PermissionList;
import org.folio.okapi.bean.RoutingEntry;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor.Action;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.ModuleId;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;
import org.folio.okapi.service.TenantStore;
import org.folio.okapi.util.CompList;
import org.folio.okapi.util.DepResolution;
import org.folio.okapi.util.LockedTypedMap1;
import org.folio.okapi.util.ProxyContext;
import org.folio.okapi.util.TenantInstallOptions;

/**
 * Manages the tenants in the shared map, and passes updates to the database.
 */
@java.lang.SuppressWarnings({"squid:S1192"})
public class TenantManager {

  private final Logger logger = OkapiLogger.get();
  private ModuleManager moduleManager;
  private ProxyService proxyService = null;
  private DiscoveryManager discoveryManager;
  private final TenantStore tenantStore;
  private LockedTypedMap1<Tenant> tenants = new LockedTypedMap1<>(Tenant.class);
  private String mapName = "tenants";
  private static final String EVENT_NAME = "timer";
  private Set<String> timers = new HashSet<>();
  private Messages messages = Messages.getInstance();
  private Vertx vertx;

  /**
   * Create tenant manager.
   * @param moduleManager module manager
   * @param tenantStore tenant storage
   */
  public TenantManager(ModuleManager moduleManager, TenantStore tenantStore) {
    this.moduleManager = moduleManager;
    this.tenantStore = tenantStore;
  }

  void setTenantsMap(LockedTypedMap1<Tenant> tenants) {
    this.tenants = tenants;
  }

  /**
   * Force the map to be local. Even in cluster mode, will use a local memory
   * map. This way, the node will not share tenants with the cluster, and can
   * not proxy requests for anyone but the superTenant, to the InternalModule.
   * Which is just enough to act in the deployment mode.
   */
  public void forceLocalMap() {
    mapName = null;
  }

  /**
   * Initialize the TenantManager.
   *
   * @param vertx Vert.x handle
   * @param fut future
   */
  public void init(Vertx vertx, Handler<ExtendedAsyncResult<Void>> fut) {
    this.vertx = vertx;

    tenants.init(vertx, mapName, ires -> {
      if (ires.failed()) {
        fut.handle(new Failure<>(ires.getType(), ires.cause()));
      } else {
        loadTenants(fut);
      }
    });
  }

  /**
   * Set the proxyService. So that we can use it to call the tenant interface,
   * etc.
   *
   * @param px Proxy Service handle
   */
  public void setProxyService(ProxyService px) {
    this.proxyService = px;
  }

  private void insert2(Tenant t, String id, Handler<ExtendedAsyncResult<String>> fut) {
    tenants.add(id, t, ares -> {
      if (ares.failed()) {
        fut.handle(new Failure<>(ares.getType(), ares.cause()));
      } else {
        fut.handle(new Success<>(id));
      }
    });
  }

  /**
   * Insert a tenant.
   *
   * @param t tenant
   * @param fut future
   */
  public void insert(Tenant t, Handler<ExtendedAsyncResult<String>> fut) {
    String id = t.getId();
    tenants.get(id, gres -> {
      if (gres.succeeded()) {
        fut.handle(new Failure<>(ErrorType.USER, messages.getMessage("10400", id)));
      } else if (gres.getType() == ErrorType.NOT_FOUND) {
        tenantStore.insert(t, res -> {
          if (res.failed()) {
            logger.warn("TenantManager: Adding {} failed: {}", id, res);
            fut.handle(new Failure<>(res.getType(), res.cause()));
          } else {
            insert2(t, id, fut);
          }
        });
      } else {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      }
    });
  }

  void updateDescriptor(TenantDescriptor td,
                        Handler<ExtendedAsyncResult<Void>> fut) {
    final String id = td.getId();
    tenants.get(id, gres -> {
      if (gres.failed() && gres.getType() != ErrorType.NOT_FOUND) {
        logger.warn("TenantManager: updateDescriptor: getting {} failed: {}", id, gres);
        fut.handle(new Failure<>(ErrorType.INTERNAL, gres.cause()));
        return;
      }
      Tenant t;
      if (gres.succeeded()) {
        t = new Tenant(td, gres.result().getEnabled());
      } else {
        t = new Tenant(td);
      }
      tenantStore.updateDescriptor(td, upres -> {
        if (upres.failed()) {
          logger.warn("TenantManager: Updating database for {} failed: {}", id, upres);
          fut.handle(new Failure<>(ErrorType.INTERNAL, upres.cause()));
        } else {
          tenants.add(id, t, fut); // handles success
        }
      });
    });
  }

  void list(Handler<ExtendedAsyncResult<List<TenantDescriptor>>> fut) {
    tenants.getKeys(lres -> {
      if (lres.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, lres.cause()));
      } else {
        CompList<List<TenantDescriptor>> futures = new CompList<>(ErrorType.INTERNAL);
        List<TenantDescriptor> tdl = new LinkedList<>();
        for (String s : lres.result()) {
          Promise<Tenant> promise = Promise.promise();
          tenants.get(s, res -> {
            if (res.succeeded()) {
              tdl.add(res.result().getDescriptor());
            }
            promise.handle(res);
          });
          futures.add(promise);
        }
        futures.all(tdl, fut);
      }
    });
  }

  /**
   * Get a tenant.
   *
   * @param id tenant ID
   * @param fut future
   */
  public void get(String id, Handler<ExtendedAsyncResult<Tenant>> fut) {
    tenants.get(id, fut);
  }

  /**
   * Delete a tenant.
   *
   * @param id tenant ID
   * @param fut future with a boolean, true if actually deleted, false if not there.
   */
  public void delete(String id, Handler<ExtendedAsyncResult<Boolean>> fut) {
    tenantStore.delete(id, dres -> {
      if (dres.failed() && dres.getType() != ErrorType.NOT_FOUND) {
        logger.warn("TenantManager: Deleting {} failed: {}", id, dres);
        fut.handle(new Failure<>(ErrorType.INTERNAL, dres.cause()));
      } else {
        tenants.remove(id, fut);
      }
    });
  }

  /**
   * Actually update the enabled modules. Assumes dependencies etc have been
   * checked.
   *
   * @param id - tenant to update for
   * @param moduleFrom - module to be disabled, may be null if none
   * @param moduleTo - module to be enabled, may be null if none
   * @param fut callback for errors.
   */
  public void updateModuleCommit(String id,
                                 String moduleFrom, String moduleTo,
                                 Handler<ExtendedAsyncResult<Void>> fut) {
    tenants.get(id, gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      }
      updateModuleCommit(gres.result(), moduleFrom, moduleTo, fut);
    });
  }

  /**
   * Update module for tenant and commit to storage.
   * @param t tenant
   * @param moduleFrom null if no original module
   * @param moduleTo null if removing a module for tenant
   * @param fut async result
   */
  public void updateModuleCommit(Tenant t,
                                 String moduleFrom, String moduleTo,
                                 Handler<ExtendedAsyncResult<Void>> fut) {
    String id = t.getId();
    if (moduleFrom != null) {
      t.disableModule(moduleFrom);
    }
    if (moduleTo != null) {
      t.enableModule(moduleTo);
    }
    tenantStore.updateModules(id, t.getEnabled(), ures -> {
      if (ures.failed()) {
        fut.handle(new Failure<>(ures.getType(), ures.cause()));
      } else {
        tenants.put(id, t, fut);
      }
    });
  }

  void enableAndDisableModule(String tenantId, TenantInstallOptions options,
                              String moduleFrom, TenantModuleDescriptor td, ProxyContext pc,
                              Handler<ExtendedAsyncResult<String>> fut) {
    tenants.get(tenantId, tres -> {
      if (tres.failed()) {
        fut.handle(new Failure<>(tres.getType(), tres.cause()));
        return;
      }
      Tenant tenant = tres.result();
      enableAndDisableModule(tenant, options, moduleFrom, td, pc, fut);
    });
  }

  private void enableAndDisableModule(Tenant tenant, TenantInstallOptions options,
                                      String moduleFrom, TenantModuleDescriptor td, ProxyContext pc,
                                      Handler<ExtendedAsyncResult<String>> fut) {

    if (td == null) {
      enableAndDisableModule2(tenant, options, moduleFrom, null, pc, fut);
    } else {
      moduleManager.getLatest(td.getId(), resTo -> {
        if (resTo.failed()) {
          fut.handle(new Failure<>(resTo.getType(), resTo.cause()));
          return;
        }
        ModuleDescriptor mdTo = resTo.result();
        enableAndDisableModule2(tenant, options, moduleFrom, mdTo, pc, fut);
      });
    }
  }

  private Future<Void> enableAndDisableModuleFut(String tenantId, TenantInstallOptions options,
                                                 String moduleFrom, TenantModuleDescriptor td,
                                                 ProxyContext pc) {
    Promise<Void> promise = Promise.promise();
    enableAndDisableModule(tenantId, options, moduleFrom, td, pc, res -> {
      if (res.failed()) {
        promise.fail(res.cause());
      } else {
        promise.complete();
      }
    });
    return promise.future();
  }

  Future<Void> disableModules(String tenantId, TenantInstallOptions options, ProxyContext pc) {
    Promise<Void> promise = Promise.promise();
    options.setDepCheck(false);
    listModules(tenantId, res -> {
      if (res.failed()) {
        promise.fail(res.cause());
        return;
      }
      Future<Void> future = Future.succeededFuture();
      for (ModuleDescriptor md : res.result()) {
        future = future.compose(x -> enableAndDisableModuleFut(tenantId, options,
          md.getId(), null, pc));
      }
      future.onComplete(promise::handle);
    });
    return promise.future();
  }

  private void enableAndDisableModule2(Tenant tenant, TenantInstallOptions options,
                                      String moduleFrom, ModuleDescriptor mdTo, ProxyContext pc,
                                      Handler<ExtendedAsyncResult<String>> fut) {

    moduleManager.get(moduleFrom, resFrom -> {
      if (resFrom.failed()) {
        fut.handle(new Failure<>(resFrom.getType(), resFrom.cause()));
        return;
      }
      ModuleDescriptor mdFrom = resFrom.result();
      if (options.getDepCheck()) {
        moduleManager.enableAndDisableCheck(tenant, mdFrom, mdTo, cres -> {
          if (cres.failed()) {
            pc.debug("enableAndDisableModule: depcheck fail: " + cres.cause().getMessage());
            fut.handle(new Failure<>(cres.getType(), cres.cause()));
            return;
          }
          enableAndDisableModule3(tenant, options, mdFrom, mdTo, pc, fut);
        });
      } else {
        enableAndDisableModule3(tenant, options, mdFrom, mdTo, pc, fut);
      }
    });
  }

  private void enableAndDisableModule3(Tenant tenant, TenantInstallOptions options,
                                       ModuleDescriptor mdFrom, ModuleDescriptor mdTo,
                                       ProxyContext pc, Handler<ExtendedAsyncResult<String>> fut) {
    if (mdFrom == null && mdTo == null) {
      fut.handle(new Success<>(""));
      return;
    }
    invokePermissions(tenant, options, mdTo, pc, res1 -> {
      if (res1.failed()) {
        fut.handle(new Failure<>(res1.getType(), res1.cause()));
        return;
      }
      invokeTenantInterface(tenant, options, mdFrom, mdTo, pc, res2 -> {
        if (res2.failed()) {
          fut.handle(new Failure<>(res2.getType(), res2.cause()));
          return;
        }
        invokePermissionsPermMod(tenant, options, mdFrom, mdTo, pc, res3 -> {
          if (res3.failed()) {
            fut.handle(new Failure<>(res3.getType(), res3.cause()));
            return;
          }
          ead5commit(tenant, mdFrom, mdTo, pc, res4 -> {
            if (res4.failed()) {
              fut.handle(new Failure<>(ErrorType.USER, res4.cause()));
              return;
            }
            fut.handle(new Success<>(mdTo != null ? mdTo.getId() : ""));
          });
        });
      });
    });
  }

  /**
   * invoke the tenant interface for a module.
   *
   * @param tenant tenant
   * @param mdFrom module from
   * @param mdTo module to
   * @param fut future
   */
  private void invokeTenantInterface(Tenant tenant, TenantInstallOptions options,
                                     ModuleDescriptor mdFrom, ModuleDescriptor mdTo,
                                     ProxyContext pc, Handler<ExtendedAsyncResult<Void>> fut) {

    if (!options.getInvoke()) {
      fut.handle(new Success<>());
      return;
    }
    JsonObject jo = new JsonObject();
    if (mdTo != null) {
      jo.put("module_to", mdTo.getId());
    }
    if (mdFrom != null) {
      jo.put("module_from", mdFrom.getId());
    }
    String tenantParameters = options.getTenantParameters();
    boolean purge = mdTo == null && options.getPurge();
    getTenantInterface(mdFrom, mdTo, jo, tenantParameters, purge, ires -> {
      if (ires.failed()) {
        if (ires.getType() == ErrorType.NOT_FOUND) {
          logger.debug("eadTenantInterface: {} has no support for tenant init",
              (mdTo != null ? mdTo.getId() : mdFrom.getId()));
          fut.handle(new Success<>());
        } else {
          fut.handle(new Failure<>(ires.getType(), ires.cause()));
        }
      } else {
        ModuleInstance tenInst = ires.result();
        final String req = purge ? "" : jo.encodePrettily();
        proxyService.callSystemInterface(tenant, tenInst, req, pc, cres -> {
          if (cres.failed()) {
            fut.handle(new Failure<>(ErrorType.USER, cres.cause()));
          } else {
            pc.passOkapiTraceHeaders(cres.result());
            // We can ignore the result, the call went well.
            fut.handle(new Success<>());
          }
        });
      }
    });
  }

  /**
   * If enabling non-permissions module, announce permissions to permissions module if enabled.
   *
   * @param tenant tenant
   * @param options install options
   * @param mdTo module to
   * @param pc proxy context
   * @param fut response
   */
  private void invokePermissions(Tenant tenant, TenantInstallOptions options,
                                 ModuleDescriptor mdTo,
                                 ProxyContext pc, Handler<ExtendedAsyncResult<Void>> fut) {
    if (mdTo == null || !options.getInvoke()
        || mdTo.getSystemInterface("_tenantPermissions") != null) {
      fut.handle(new Success<>());
      return;
    }
    findSystemInterface(tenant, res -> {
      if (res.failed()) {
        if (res.getType() != ErrorType.NOT_FOUND) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
          return;
        }
        // no perms module for now
        fut.handle(new Success<>());
      } else {
        invokePermissionsForModule(tenant, mdTo, res.result(), pc, fut);
      }
    });
  }

  /**
   * If enabling permissions module it, announce permissions to it.
   *
   * @param tenant tenant
   * @param options install options
   * @param mdFrom module from
   * @param mdTo module to
   * @param pc proxy context
   * @param fut response
   */
  private void invokePermissionsPermMod(Tenant tenant, TenantInstallOptions options,
                                        ModuleDescriptor mdFrom, ModuleDescriptor mdTo,
                                        ProxyContext pc, Handler<ExtendedAsyncResult<Void>> fut) {
    if (mdTo == null || !options.getInvoke()
        || mdTo.getSystemInterface("_tenantPermissions") == null) {
      fut.handle(new Success<>());
      return;
    }
    // enabling permissions module.
    String moduleFrom = mdFrom != null ? mdFrom.getId() : null;
    findSystemInterface(tenant, res -> {
      if (res.failed()) {
        if (res.getType() != ErrorType.NOT_FOUND) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
          return;
        }
        Set<String> listModules = tenant.listModules();
        Iterator<String> modit = listModules.iterator();
        loadPermissionsForEnabled(tenant, modit, moduleFrom, mdTo, mdTo, pc, fut);
      } else {
        invokePermissionsForModule(tenant, mdTo, mdTo, pc, fut);
      }
    });
  }

  /**
   * Reload permissions. When we enable a module that
   * provides the tenantPermissions interface, we may have other modules already
   * enabled, who have not got their permissions pushed. Now that we have a
   * place to push those permissions to, we do it recursively for all enabled
   * modules.
   *
   * @param tenant tenant
   * @param moduleFrom module from
   * @param mdTo module to
   * @param permsModule permissions module
   * @param pc ProxyContext
   * @param fut future
   */
  private void loadPermissionsForEnabled(
      Tenant tenant, Iterator<String> modit,
      String moduleFrom, ModuleDescriptor mdTo, ModuleDescriptor permsModule,
      ProxyContext pc, Handler<ExtendedAsyncResult<Void>> fut) {
    if (!modit.hasNext()) {
      pc.debug("ead3RealoadPerms: No more modules to reload");
      invokePermissionsForModule(tenant, mdTo, permsModule, pc, fut);
      return;
    }
    String mdid = modit.next();
    moduleManager.get(mdid, res -> {
      if (res.failed()) { // not likely to happen
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      ModuleDescriptor md = res.result();
      pc.debug("ead3RealoadPerms: Should reload perms for " + md.getName());
      invokePermissionsForModule(tenant, md, permsModule, pc, pres -> {
        if (pres.failed()) { // not likely to happen
          fut.handle(pres);
          return;
        }
        loadPermissionsForEnabled(tenant, modit, moduleFrom, mdTo, permsModule, pc, fut);
      });
    });
  }

  /**
   * enableAndDisable helper 5: Commit the change in modules.
   *
   * @param tenant tenant
   * @param mdFrom module from
   * @param mdTo module to
   * @param pc ProxyContext
   * @param fut future
   */
  private void ead5commit(Tenant tenant,
                          ModuleDescriptor mdFrom, ModuleDescriptor mdTo, ProxyContext pc,
                          Handler<ExtendedAsyncResult<Void>> fut) {

    String moduleFrom = mdFrom != null ? mdFrom.getId() : null;
    String moduleTo = mdTo != null ? mdTo.getId() : null;

    pc.debug("ead5commit: " + moduleFrom + " " + moduleTo);
    updateModuleCommit(tenant, moduleFrom, moduleTo, ures -> {
      if (ures.failed()) {
        fut.handle(new Failure<>(ures.getType(), ures.cause()));
        return;
      }
      if (moduleTo != null) {
        EventBus eb = vertx.eventBus();
        eb.publish(EVENT_NAME, tenant.getId());
      }
      pc.debug("ead5commit done");
      fut.handle(new Success<>());
    });
  }

  /**
   * start timers for all tenants.
   * @param promise async result
   */
  public void startTimers(Promise<Void> promise, DiscoveryManager discoveryManager) {
    this.discoveryManager = discoveryManager;
    tenants.getKeys(res -> {
      if (res.succeeded()) {
        for (String tenantId : res.result()) {
          logger.info("starting {}", tenantId);
          handleTimer(tenantId);
        }
        consumeTimers();
        promise.complete();
      } else {
        promise.fail(res.cause());
      }
    });
  }

  /**
   * For unit testing.
   */
  Set<String> getTimers() {
    return timers;
  }

  private void consumeTimers() {
    EventBus eb = vertx.eventBus();
    eb.consumer(EVENT_NAME, res -> {
      String tenantId = (String) res.body();
      handleTimer(tenantId);
    });
  }

  private void stopTimer(String tenantId, String moduleId, int seq) {
    logger.info("remove timer for module {} for tenant {}", moduleId, tenantId);
    final String key = tenantId + "_" + moduleId + "_" + seq;
    timers.remove(key);
  }

  private void handleTimer(String tenantId) {
    handleTimer(tenantId, null, 0);
  }

  void handleTimer(String tenantId, String moduleId, int seq1) {
    logger.info("handleTimer tenant {} module {} seq1 {}", tenantId, moduleId, seq1);
    tenants.get(tenantId, tres -> {
      if (tres.failed()) {
        // tenant no longer exist
        stopTimer(tenantId, moduleId, seq1);
        return;
      }
      Tenant tenant = tres.result();
      moduleManager.getEnabledModules(tenant, mres -> {
        if (mres.failed()) {
          stopTimer(tenantId, moduleId, seq1);
          return;
        }
        List<ModuleDescriptor> mdList = mres.result();
        try {
          handleTimer(tenant, mdList, moduleId, seq1);
        } catch (Exception ex) {
          logger.warn("handleTimer exception {}", ex.getMessage(), ex);
        }
      });
    });
  }

  private void handleTimer(Tenant tenant, List<ModuleDescriptor> mdList,
                           String moduleId, int seq1) {
    int noTimers = 0;
    final String tenantId = tenant.getId();
    for (ModuleDescriptor md : mdList) {
      if (moduleId == null || moduleId.equals(md.getId())) {
        InterfaceDescriptor timerInt = md.getSystemInterface("_timer");
        if (timerInt != null) {
          List<RoutingEntry> routingEntries = timerInt.getAllRoutingEntries();
          noTimers += handleTimer(tenant, md, routingEntries, seq1);
        }
      }
    }
    if (noTimers == 0) {
      // module no longer enabled for tenant
      stopTimer(tenantId, moduleId, seq1);
    }
    logger.info("handleTimer done no {}", noTimers);
  }

  private int handleTimer(Tenant tenant, ModuleDescriptor md,
                          List<RoutingEntry> routingEntries, int seq1) {
    int i = 0;
    final String tenantId = tenant.getId();
    for (RoutingEntry re : routingEntries) {
      final int seq = ++i;
      final String key = tenantId + "_" + md.getId() + "_" + seq;
      final long delay = re.getDelayMilliSeconds();
      final String path = re.getStaticPath();
      if (delay > 0 && path != null) {
        if (seq1 == 0) {
          if (!timers.contains(key)) {
            timers.add(key);
            waitTimer(tenantId, md, delay, seq);
          }
        } else if (seq == seq1) {
          if (discoveryManager.isLeader()) {
            fireTimer(tenant, md, re, path);
          }
          waitTimer(tenantId, md, delay, seq);
          return 1;
        }
      }
    }
    return 0;
  }

  private void waitTimer(String tenantId, ModuleDescriptor md, long delay, int seq) {
    vertx.setTimer(delay, res
        -> handleTimer(tenantId, md.getId(), seq));
  }

  private void fireTimer(Tenant tenant, ModuleDescriptor md, RoutingEntry re, String path) {
    String tenantId = tenant.getId();
    HttpMethod httpMethod = HttpMethod.POST;
    String[] methods = re.getMethods();
    if (methods != null && re.getMethods().length >= 1) {
      httpMethod = HttpMethod.valueOf(methods[0]);
    }
    ModuleInstance inst = new ModuleInstance(md, re, path, httpMethod, true);
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    logger.info("timer call start module {} for tenant {}", md.getId(), tenantId);
    proxyService.callSystemInterface(headers, tenant, inst, "", cres -> {
      if (cres.succeeded()) {
        logger.info("timer call succeeded to module {} for tenant {}",
            md.getId(), tenantId);
      } else {
        logger.info("timer call failed to module {} for tenant {} : {}",
            md.getId(), tenantId, cres.cause().getMessage());
      }
    });
  }

  private void invokePermissionsForModule(Tenant tenant, ModuleDescriptor mdTo,
                                          ModuleDescriptor permsModule, ProxyContext pc,
                                          Handler<ExtendedAsyncResult<Void>> fut) {

    pc.debug("Loading permissions for " + mdTo.getName()
        + " (using " + permsModule.getName() + ")");
    String moduleTo = mdTo.getId();
    PermissionList pl = null;
    InterfaceDescriptor permInt = permsModule.getSystemInterface("_tenantPermissions");
    if (permInt.getVersion().equals("1.0")) {
      pl = new PermissionList(moduleTo, mdTo.getPermissionSets());
    } else {
      pl = new PermissionList(moduleTo, mdTo.getExpandedPermissionSets());
    }
    String pljson = Json.encodePrettily(pl);
    pc.debug("tenantPerms Req: " + pljson);
    String permPath = "";
    List<RoutingEntry> routingEntries = permInt.getAllRoutingEntries();
    ModuleInstance permInst = null;
    if (!routingEntries.isEmpty()) {
      for (RoutingEntry re : routingEntries) {
        if (re.match(null, "POST")) {
          permPath = re.getStaticPath();
          permInst = new ModuleInstance(permsModule, re, permPath, HttpMethod.POST, true);
        }
      }
    }
    if (permInst == null) {
      fut.handle(new Failure<>(ErrorType.USER,
          "Bad _tenantPermissions interface in module " + permsModule.getId()
              + ". No path to POST to"));
      return;
    }
    pc.debug("tenantPerms: " + permsModule.getId() + " and " + permPath);
    proxyService.callSystemInterface(tenant, permInst,
        pljson, pc, cres -> {
          if (cres.failed()) {
            fut.handle(new Failure<>(ErrorType.USER, cres.cause()));
          } else {
            pc.passOkapiTraceHeaders(cres.result());
            pc.debug("tenantPerms request to " + permsModule.getName()
                + " succeeded for module " + moduleTo + " and tenant " + tenant.getId());
            fut.handle(new Success<>());
          }
        });
  }

  /**
   * Find the tenant API interface. Supports several deprecated versions of the
   * tenant interface: the 'tenantInterface' field in MD; if the module provides
   * a '_tenant' interface without RoutingEntries, and finally the proper way,
   * if the module provides a '_tenant' interface that is marked as a system
   * interface, and has a RoutingEntry that supports POST.
   *
   * @param mdFrom module from
   * @param mdTo module to
   * @param jo Json Object to be POSTed
   * @param tenantParameters tenant parameters (eg sample data)
   * @param purge true if purging (DELETE)
   * @param fut future
   */
  private void getTenantInterface(
      ModuleDescriptor mdFrom,
      ModuleDescriptor mdTo, JsonObject jo, String tenantParameters, boolean purge,
      Handler<ExtendedAsyncResult<ModuleInstance>> fut) {

    ModuleDescriptor md = mdTo != null ? mdTo : mdFrom;
    InterfaceDescriptor[] prov = md.getProvidesList();
    for (InterfaceDescriptor pi : prov) {
      logger.debug("findTenantInterface: Looking at {}", pi.getId());
      if ("_tenant".equals(pi.getId())) {
        final String v = pi.getVersion();
        final String method = purge ? "DELETE" : "POST";
        switch (v) {
          case "1.0":
            if (mdTo == null && !purge) {
              break;
            } else if (getTenantInterface1(pi, mdFrom, mdTo, method, fut)) {
              return;
            } else if (!purge) {
              logger.warn("Module '{}' uses old-fashioned tenant "
                  + "interface. Define InterfaceType=system, and add a RoutingEntry."
                  + " Falling back to calling /_/tenant.", md.getId());
              fut.handle(new Success<>(new ModuleInstance(md, null,
                  "/_/tenant", HttpMethod.POST, true).withRetry()));
              return;
            }
            break;
          case "1.1":
            if (getTenantInterface1(pi, mdFrom, mdTo, method, fut)) {
              return;
            }
            break;
          case "1.2":
            putTenantParameters(jo, tenantParameters);
            if (getTenantInterface1(pi, mdFrom, mdTo, method, fut)) {
              return;
            }
            break;
          default:
            fut.handle(new Failure<>(ErrorType.USER, messages.getMessage("10401", v)));
            return;
        }
      }
    }
    fut.handle(new Failure<>(ErrorType.NOT_FOUND, messages.getMessage("10402", md.getId())));
  }

  private void putTenantParameters(JsonObject jo, String tenantParameters) {
    if (tenantParameters != null) {
      JsonArray ja = new JsonArray();
      for (String p : tenantParameters.split(",")) {
        String[] kv = p.split("=");
        if (kv.length > 0) {
          JsonObject jsonKv = new JsonObject();
          jsonKv.put("key", kv[0]);
          if (kv.length > 1) {
            jsonKv.put("value", kv[1]);
          }
          ja.add(jsonKv);
        }
      }
      jo.put("parameters", ja);
    }
  }

  private boolean getTenantInterface1(InterfaceDescriptor pi,
                                      ModuleDescriptor mdFrom, ModuleDescriptor mdTo, String method,
                                      Handler<ExtendedAsyncResult<ModuleInstance>> fut) {

    ModuleDescriptor md = mdTo != null ? mdTo : mdFrom;
    if ("system".equals(pi.getInterfaceType())) {
      List<RoutingEntry> res = pi.getAllRoutingEntries();
      for (RoutingEntry re : res) {
        if (re.match(null, method)) {
          String pattern = re.getStaticPath();
          if (method.equals("DELETE")) {
            fut.handle(new Success<>(new ModuleInstance(md, re, pattern, HttpMethod.DELETE, true)));
            return true;
          } else if ("/_/tenant/disable".equals(pattern)) {
            if (mdTo == null) {
              fut.handle(new Success<>(new ModuleInstance(md, re, pattern, HttpMethod.POST, true)));
              return true;
            }
          } else if (mdTo != null) {
            fut.handle(new Success<>(new ModuleInstance(md, re, pattern,
                HttpMethod.POST, true).withRetry()));
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Find (the first) module that provides a given system interface. Module must
   * be enabled for the tenant.
   *
   * @param tenant tenant to check for
   * @param fut callback with a @return ModuleDescriptor for the module
   *
   */
  private void findSystemInterface(Tenant tenant,
                                   Handler<ExtendedAsyncResult<ModuleDescriptor>> fut) {

    Iterator<String> it = tenant.getEnabled().keySet().iterator();
    findSystemInterfaceR(tenant, "_tenantPermissions", it, fut);
  }

  private void findSystemInterfaceR(Tenant tenant, String interfaceName,
                                    Iterator<String> it,
                                    Handler<ExtendedAsyncResult<ModuleDescriptor>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Failure<>(ErrorType.NOT_FOUND, messages.getMessage("10403", interfaceName)));
      return;
    }
    String mid = it.next();
    moduleManager.get(mid, gres -> {
      if (gres.failed()) { // should not happen
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      }
      ModuleDescriptor md = gres.result();
      logger.debug("findSystemInterface: looking at {} system interface {}",
          mid, md.getSystemInterface(interfaceName));
      if (md.getSystemInterface(interfaceName) != null) {
        logger.debug("findSystemInterface: found {}", mid);
        fut.handle(new Success<>(md));
        return;
      }
      findSystemInterfaceR(tenant, interfaceName, it, fut);
    });
  }

  void listInterfaces(String tenantId, boolean full, String interfaceType,
                      Handler<ExtendedAsyncResult<List<InterfaceDescriptor>>> fut) {

    tenants.get(tenantId, tres -> {
      if (tres.failed()) {
        fut.handle(new Failure<>(tres.getType(), tres.cause()));
      } else {
        listInterfaces(tres.result(), full, interfaceType, fut);
      }
    });
  }

  private void listInterfaces(Tenant tenant, boolean full, String interfaceType,
                              Handler<ExtendedAsyncResult<List<InterfaceDescriptor>>> fut) {

    List<InterfaceDescriptor> intList = new LinkedList<>();
    moduleManager.getEnabledModules(tenant, mres -> {
      if (mres.failed()) {
        fut.handle(new Failure<>(mres.getType(), mres.cause()));
        return;
      }
      List<ModuleDescriptor> modlist = mres.result();
      Set<String> ids = new HashSet<>();
      for (ModuleDescriptor md : modlist) {
        for (InterfaceDescriptor provide : md.getProvidesList()) {
          if (interfaceType == null || provide.isType(interfaceType)) {
            if (full) {
              intList.add(provide);
            } else {
              if (ids.add(provide.getId())) {
                InterfaceDescriptor tmp = new InterfaceDescriptor();
                tmp.setId(provide.getId());
                tmp.setVersion(provide.getVersion());
                intList.add(tmp);
              }
            }
          }
        }
      }
      fut.handle(new Success<>(intList));
    });
  }

  void listModulesFromInterface(String tenantId,
                                String interfaceName, String interfaceType,
                                Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {

    tenants.get(tenantId, tres -> {
      if (tres.failed()) {
        fut.handle(new Failure<>(tres.getType(), tres.cause()));
        return;
      }
      Tenant tenant = tres.result();
      List<ModuleDescriptor> mdList = new LinkedList<>();
      moduleManager.getEnabledModules(tenant, mres -> {
        if (mres.failed()) {
          fut.handle(new Failure<>(mres.getType(), mres.cause()));
          return;
        }
        List<ModuleDescriptor> modlist = mres.result();
        for (ModuleDescriptor md : modlist) {
          for (InterfaceDescriptor provide : md.getProvidesList()) {
            if (interfaceName.equals(provide.getId())
                && (interfaceType == null || provide.isType(interfaceType))) {
              mdList.add(md);
              break;
            }
          }
        }
        fut.handle(new Success<>(mdList));
      }); // modlist
    }); // tenant
  }

  void installUpgradeModules(String tenantId, ProxyContext pc,
                             TenantInstallOptions options, List<TenantModuleDescriptor> tml,
                             Handler<ExtendedAsyncResult<List<TenantModuleDescriptor>>> fut) {

    if (tml != null) {
      for (TenantModuleDescriptor tm : tml) {
        if (tm.getAction() == null) {
          fut.handle(new Failure<>(ErrorType.USER, messages.getMessage("10405", tm.getId())));
          return;
        }
      }
    }
    tenants.get(tenantId, gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      }
      Tenant t = gres.result();
      moduleManager.getModulesWithFilter(options.getPreRelease(),
          options.getNpmSnapshot(), null, mres -> {
            if (mres.failed()) {
              fut.handle(new Failure<>(mres.getType(), mres.cause()));
              return;
            }
            List<ModuleDescriptor> modResult = mres.result();
            HashMap<String, ModuleDescriptor> modsAvailable = new HashMap<>(modResult.size());
            HashMap<String, ModuleDescriptor> modsEnabled = new HashMap<>();
            for (ModuleDescriptor md : modResult) {
              modsAvailable.put(md.getId(), md);
              logger.info("mod available: {}", md.getId());
              if (t.isEnabled(md.getId())) {
                logger.info("mod enabled: {}", md.getId());
                modsEnabled.put(md.getId(), md);
              }
            }
            List<TenantModuleDescriptor> tml2
                = prepareTenantModuleList(modsAvailable, modsEnabled, tml);
            installUpgradeModules2(t, pc, options, modsAvailable, modsEnabled, tml2, fut);
          });
    });
  }

  private List<TenantModuleDescriptor> prepareTenantModuleList(
      Map<String, ModuleDescriptor> modsAvailable,
      Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml) {

    if (tml == null) { // upgrade case . Mark all newer modules for install
      List<TenantModuleDescriptor> tml2 = new LinkedList<>();
      for (String id : modsEnabled.keySet()) {
        ModuleId moduleId = new ModuleId(id);
        String latestId = moduleId.getLatest(modsAvailable.keySet());
        if (!latestId.equals(id)) {
          TenantModuleDescriptor tmd = new TenantModuleDescriptor();
          tmd.setAction(Action.enable);
          tmd.setId(latestId);
          logger.info("upgrade.. enable {}", latestId);
          tmd.setFrom(id);
          tml2.add(tmd);
        }
      }
      return tml2;
    } else {
      return tml;
    }
  }

  private void installUpgradeModules2(
      Tenant t, ProxyContext pc,
      TenantInstallOptions options,
      Map<String, ModuleDescriptor> modsAvailable,
      Map<String, ModuleDescriptor> modsEnabled, List<TenantModuleDescriptor> tml,
      Handler<ExtendedAsyncResult<List<TenantModuleDescriptor>>> fut) {

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      if (options.getSimulate()) {
        fut.handle(new Success<>(tml));
      } else {
        installAutodeploy(t, pc, options, modsAvailable, tml, tml.iterator(),
            res1 -> {
              if (res1.failed()) {
                fut.handle(new Failure<>(res1.getType(), res1.cause()));
              } else {
                fut.handle(new Success<>(tml));
              }
            });
      }
    });
  }

  /* phase 1 deploy modules if necessary */
  private void installAutodeploy(Tenant t, ProxyContext pc,
                                 TenantInstallOptions options,
                                 Map<String, ModuleDescriptor> modsAvailable,
                                 List<TenantModuleDescriptor> tml,
                                 Iterator<TenantModuleDescriptor> it,
                                 Handler<ExtendedAsyncResult<Void>> fut) {

    if (!it.hasNext() || !options.getDeploy()) {
      installTenantPrepare(t, pc, options, modsAvailable, tml, tml.iterator(), fut);
      return;
    }
    TenantModuleDescriptor tm = it.next();
    if (tm.getAction() == Action.enable || tm.getAction() == Action.uptodate) {
      ModuleDescriptor md = modsAvailable.get(tm.getId());
      proxyService.autoDeploy(md, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          installAutodeploy(t, pc, options, modsAvailable, tml, it, fut);
        }
      });
    } else {
      installAutodeploy(t, pc, options, modsAvailable, tml, it, fut);
    }
  }

  /* phase 2 enable modules for tenant */
  private void installTenantPrepare(Tenant tenant, ProxyContext pc,
                                    TenantInstallOptions options,
                                    Map<String, ModuleDescriptor> modsAvailable,
                                    List<TenantModuleDescriptor> tml,
                                    Iterator<TenantModuleDescriptor> it,
                                    Handler<ExtendedAsyncResult<Void>> fut) {

    if (!it.hasNext()) {
      installUndeploy(tenant, options, modsAvailable, tml, tml.iterator(), fut);
      return;
    }
    TenantModuleDescriptor tm = it.next();
    ModuleDescriptor mdFrom = null;
    ModuleDescriptor mdTo = null;
    if (tm.getAction() == Action.enable) {
      if (tm.getFrom() != null) {
        mdFrom = modsAvailable.get(tm.getFrom());
      }
      mdTo = modsAvailable.get(tm.getId());
    } else if (tm.getAction() == Action.disable) {
      mdFrom = modsAvailable.get(tm.getId());
    }
    enableAndDisableModule3(tenant, options, mdFrom, mdTo, pc, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      installTenantPrepare(tenant, pc, options, modsAvailable, tml, it, fut);
    });
  }

  /* phase 4 undeploy if no longer needed */
  private void installUndeploy(Tenant tenant,
                               TenantInstallOptions options,
                               Map<String, ModuleDescriptor> modsAvailable,
                               List<TenantModuleDescriptor> tml,
                               Iterator<TenantModuleDescriptor> it,
                               Handler<ExtendedAsyncResult<Void>> fut) {

    if (!it.hasNext() || !options.getDeploy()) {
      fut.handle(new Success<>());
      return;
    }
    TenantModuleDescriptor tm = it.next();
    ModuleDescriptor md = null;
    if (tm.getAction() == Action.enable) {
      md = modsAvailable.get(tm.getFrom());
    }
    if (tm.getAction() == Action.disable) {
      md = modsAvailable.get(tm.getId());
    }
    if (md == null) {
      installUndeploy(tenant, options, modsAvailable, tml, it, fut);
      return;
    }
    final ModuleDescriptor mdF = md;
    getModuleUser(md.getId(), ures -> {
      if (ures.failed()) {
        // in use or other error, so skip
        installUndeploy(tenant, options, modsAvailable, tml, it, fut);
        return;
      }
      // success means : not in use, so we can undeploy it
      logger.info("autoUndeploy mdF {}", mdF.getId());
      proxyService.autoUndeploy(mdF, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          installUndeploy(tenant, options, modsAvailable, tml, it, fut);
        }
      });
    });
  }

  void listModules(String id,
                   Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {

    tenants.get(id, gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        Tenant t = gres.result();
        List<ModuleDescriptor> tl = new LinkedList<>();
        CompList<List<ModuleDescriptor>> futures = new CompList<>(ErrorType.INTERNAL);
        for (String moduleId : t.listModules()) {
          Promise<ModuleDescriptor> promise = Promise.promise();
          moduleManager.get(moduleId, res -> {
            if (res.succeeded()) {
              tl.add(res.result());
            }
            promise.handle(res);
          });
          futures.add(promise);
        }
        futures.all(tl, fut);
      }
    });
  }

  /**
   * Get the (first) tenant that uses the given module. Used to check if a
   * module may be deleted.
   *
   * @param mod id of the module in question.
   * @param fut - Succeeds if not in use. Fails with ANY and the module name
   */
  public void getModuleUser(String mod, Handler<ExtendedAsyncResult<Void>> fut) {
    tenants.getKeys(kres -> {
      if (kres.failed()) {
        fut.handle(new Failure<>(kres.getType(), kres.cause()));
      } else {
        Collection<String> tkeys = kres.result();
        Iterator<String> it = tkeys.iterator();
        getModuleUserR(mod, it, fut);
      }
    });
  }

  private void getModuleUserR(String mod, Iterator<String> it,
                              Handler<ExtendedAsyncResult<Void>> fut) {
    if (!it.hasNext()) { // no problems found
      fut.handle(new Success<>());
    } else {
      String tid = it.next();
      tenants.get(tid, gres -> {
        if (gres.failed()) {
          fut.handle(new Failure<>(gres.getType(), gres.cause()));
        } else {
          Tenant t = gres.result();
          if (t.isEnabled(mod)) {
            fut.handle(new Failure<>(ErrorType.ANY, tid));
          } else {
            getModuleUserR(mod, it, fut);
          }
        }
      });
    }
  }

  /**
   * Load tenants from the store into the shared memory map.
   *
   * @param fut future
   */
  private void loadTenants(Handler<ExtendedAsyncResult<Void>> fut) {
    tenants.getKeys(gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
        return;
      }
      Collection<String> keys = gres.result();
      if (!keys.isEmpty()) {
        fut.handle(new Success<>());
        return;
      }
      tenantStore.listTenants(lres -> {
        if (lres.failed()) {
          fut.handle(new Failure<>(lres.getType(), lres.cause()));
          return;
        }
        CompList<List<Void>> futures = new CompList<>(ErrorType.INTERNAL);
        for (Tenant t : lres.result()) {
          Promise<Void> f = Promise.promise();
          tenants.add(t.getId(), t, f::handle);
          futures.add(f);
        }
        futures.all(fut);
      });
    });
  }

} // class
