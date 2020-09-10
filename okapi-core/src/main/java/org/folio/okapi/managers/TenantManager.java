package org.folio.okapi.managers;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.InstallJob;
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
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.ModuleId;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.service.TenantStore;
import org.folio.okapi.util.DepResolution;
import org.folio.okapi.util.LockedTypedMap1;
import org.folio.okapi.util.LockedTypedMap2;
import org.folio.okapi.util.OkapiError;
import org.folio.okapi.util.ProxyContext;
import org.folio.okapi.util.TenantInstallOptions;

/**
 * Manages the tenants in the shared map, and passes updates to the database.
 */
@java.lang.SuppressWarnings({"squid:S1192"}) // String literals should not be duplicated
public class TenantManager {

  private final Logger logger = OkapiLogger.get();
  private ModuleManager moduleManager;
  private ProxyService proxyService = null;
  private DiscoveryManager discoveryManager;
  private final TenantStore tenantStore;
  private LockedTypedMap1<Tenant> tenants = new LockedTypedMap1<>(Tenant.class);
  private String mapName = "tenants";
  private LockedTypedMap2<InstallJob> jobs = new LockedTypedMap2<>(InstallJob.class);
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
   * @return fut future
   */
  public Future<Void> init(Vertx vertx) {
    this.vertx = vertx;

    return tenants.init(vertx, mapName)
        .compose(x -> jobs.init(vertx, "installJobs"))
        .compose(x -> loadTenants());
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

  /**
   * Insert a tenant.
   *
   * @param t tenant
   * @return future
   */
  public Future<String> insert(Tenant t) {
    String id = t.getId();
    return tenants.get(id)
        .compose(gres -> {
          if (gres != null) { // already exists
            return Future.failedFuture(new OkapiError(ErrorType.USER,
                messages.getMessage("10400", id)));
          }
          return Future.succeededFuture();
        })
        .compose(res1 -> tenantStore.insert(t))
        .compose(res2 -> tenants.add(id, t))
        .compose(x -> Future.succeededFuture(id));
  }

  Future<Void> updateDescriptor(TenantDescriptor td) {
    final String id = td.getId();
    return tenants.get(id).compose(gres -> {
      Tenant t;
      if (gres != null) {
        t = new Tenant(td, gres.getEnabled());
      } else {
        t = new Tenant(td);
      }
      return tenantStore.updateDescriptor(td).compose(res -> tenants.add(id, t));
    });
  }

  Future<List<TenantDescriptor>> list() {
    return tenants.getKeys().compose(lres -> {
      List<Future> futures = new LinkedList<>();
      List<TenantDescriptor> tdl = new LinkedList<>();
      for (String s : lres) {
        futures.add(tenants.getNotFound(s).compose(res -> {
          tdl.add(res.getDescriptor());
          return Future.succeededFuture();
        }));
      }
      return CompositeFuture.all(futures).compose(x -> Future.succeededFuture(tdl));
    });
  }

  /**
   * Get a tenant.
   *
   * @param id tenant ID
   * @return fut future
   */
  public Future<Tenant> get(String id) {
    return tenants.getNotFound(id);
  }

  /**
   * Delete a tenant.
   *
   * @param id tenant ID
   * @return future .. OkapiError if id not found
   */
  public Future<Void> delete(String id) {
    return tenantStore.delete(id).compose(x -> {
      if (Boolean.FALSE.equals(x)) {
        return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND, id));
      }
      return tenants.removeNotFound(id).mapEmpty();
    });
  }

  /**
   * Actually update the enabled modules. Assumes dependencies etc have been
   * checked.
   *
   * @param id - tenant to update for
   * @param moduleFrom - module to be disabled, may be null if none
   * @param moduleTo - module to be enabled, may be null if none
   * @return fut callback for errors.
   */
  public Future<Void> updateModuleCommit(String id, String moduleFrom, String moduleTo) {
    return tenants.getNotFound(id).compose(t -> updateModuleCommit(t, moduleFrom, moduleTo));
  }

  /**
   * Update module for tenant and commit to storage.
   * @param t tenant
   * @param moduleFrom null if no original module
   * @param moduleTo null if removing a module for tenant
   * @return fut async result
   */
  public Future<Void> updateModuleCommit(Tenant t, String moduleFrom, String moduleTo) {
    String id = t.getId();
    if (moduleFrom != null) {
      t.disableModule(moduleFrom);
    }
    if (moduleTo != null) {
      t.enableModule(moduleTo);
    }
    return tenantStore.updateModules(id, t.getEnabled()).compose(ures -> {
      if (Boolean.FALSE.equals(ures)) {
        return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND, id));
      }
      return tenants.put(id, t);
    });
  }

  Future<Void> disableModules(String tenantId, TenantInstallOptions options, ProxyContext pc) {
    options.setDepCheck(false);
    return listModules(tenantId).compose(res -> {
      Future<Void> future = Future.succeededFuture();
      for (ModuleDescriptor md : res) {
        future = future.compose(x -> enableAndDisableModule(tenantId, options,
            md.getId(), null, pc).mapEmpty());
      }
      return future;
    });
  }

  Future<String> enableAndDisableModule(
      String tenantId, TenantInstallOptions options, String moduleFrom,
      TenantModuleDescriptor td, ProxyContext pc) {

    return tenants.getNotFound(tenantId)
        .compose(tenant -> Future.succeededFuture()
            .compose(res -> {
              if (td == null) {
                return Future.succeededFuture(null);
              }
              return moduleManager.getLatest(td.getId());
            }).compose(mdTo ->
                moduleManager.get(moduleFrom).compose(mdFrom -> {
                  Future<Void> future = Future.succeededFuture();
                  if (options.getDepCheck()) {
                    future = future
                        .compose(x -> moduleManager.enableAndDisableCheck(tenant, mdFrom, mdTo));
                  }
                  return future
                      .compose(x -> enableAndDisableModule(tenant, options, mdFrom, mdTo, pc));
                })
            )
        );
  }

  private Future<String> enableAndDisableModule(Tenant tenant, TenantInstallOptions options,
                                                ModuleDescriptor mdFrom, ModuleDescriptor mdTo,
                                                ProxyContext pc) {
    if (mdFrom == null && mdTo == null) {
      return Future.succeededFuture("");
    }
    return invokePermissions(tenant, options, mdTo, pc)
        .compose(x -> invokeTenantInterface(tenant, options, mdFrom, mdTo, pc))
        .compose(x -> invokePermissionsPermMod(tenant, options, mdFrom, mdTo, pc))
        .compose(x -> commitModuleChange(tenant, mdFrom, mdTo, pc))
        .compose(x -> Future.succeededFuture((mdTo != null ? mdTo.getId() : ""))
    );
  }

  /**
   * invoke the tenant interface for a module.
   *
   * @param tenant tenant
   * @param mdFrom module from
   * @param mdTo module to
   * @return fut future
   */
  private Future<Void> invokeTenantInterface(Tenant tenant, TenantInstallOptions options,
                                             ModuleDescriptor mdFrom, ModuleDescriptor mdTo,
                                             ProxyContext pc) {

    if (!options.getInvoke()) {
      return Future.succeededFuture();
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
    return getTenantInstanceForModule(mdFrom, mdTo, jo, tenantParameters, purge)
        .compose(instance -> {
          if (instance == null) {
            logger.debug("{}: has no support for tenant init",
                (mdTo != null ? mdTo.getId() : mdFrom.getId()));
            return Future.succeededFuture();
          }
          final String req = purge ? "" : jo.encodePrettily();
          return proxyService.callSystemInterface(tenant, instance, req, pc).compose(cres -> {
            pc.passOkapiTraceHeaders(cres);
            // We can ignore the result, the call went well.
            return Future.succeededFuture();
          });
        });
  }

  /**
   * If enabling non-permissions module, announce permissions to permissions module if enabled.
   *
   * @param tenant tenant
   * @param options install options
   * @param mdTo module to
   * @param pc proxy content
   * @return Future
   */
  private Future<Void> invokePermissions(Tenant tenant, TenantInstallOptions options,
                                         ModuleDescriptor mdTo,
                                         ProxyContext pc) {
    if (mdTo == null || !options.getInvoke()
        || mdTo.getSystemInterface("_tenantPermissions") != null) {
      return Future.succeededFuture();
    }
    return findSystemInterface(tenant, "_tenantPermissions").compose(md -> {
      if (md == null) {
        return Future.succeededFuture();
      }
      return invokePermissionsForModule(tenant, mdTo, md, pc);
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
   * @return fut response
   */
  private Future<Void> invokePermissionsPermMod(Tenant tenant, TenantInstallOptions options,
                                                ModuleDescriptor mdFrom, ModuleDescriptor mdTo,
                                                ProxyContext pc) {
    if (mdTo == null || !options.getInvoke()
        || mdTo.getSystemInterface("_tenantPermissions") == null) {
      return Future.succeededFuture();
    }
    // enabling permissions module.
    String moduleFrom = mdFrom != null ? mdFrom.getId() : null;
    return findSystemInterface(tenant, "_tenantPermissions")
        .compose(res -> {
          if (res == null) { // == null : no permissions module already enabled
            return loadPermissionsForEnabled(tenant, mdTo, pc);
          } else {
            return Future.succeededFuture();
          }
        })
        .compose(res -> invokePermissionsForModule(tenant, mdTo, mdTo, pc));
  }

  /**
   * Announce permissions for a set of modules to a permissions module.
   *
   * @param tenant tenant
   * @param permsModule permissions module
   * @param pc ProxyContext
   * @return future
   */
  private Future<Void> loadPermissionsForEnabled(
      Tenant tenant, ModuleDescriptor permsModule, ProxyContext pc) {

    Future<Void> future = Future.succeededFuture();
    for (String mdid : tenant.listModules()) {
      future = future.compose(x -> moduleManager.get(mdid)
          .compose(md -> invokePermissionsForModule(tenant, md, permsModule, pc)));
    }
    return future;
  }

  /**
   * Commit change of module for tenant and publish on event bus about it.
   *
   * @param tenant tenant
   * @param mdFrom module from (null if new module)
   * @param mdTo module to (null if module is removed)
   * @param pc ProxyContext
   * @return future
   */
  private Future<Void> commitModuleChange(Tenant tenant, ModuleDescriptor mdFrom,
                                          ModuleDescriptor mdTo, ProxyContext pc) {

    String moduleFrom = mdFrom != null ? mdFrom.getId() : null;
    String moduleTo = mdTo != null ? mdTo.getId() : null;

    Promise<Void> promise = Promise.promise();
    return updateModuleCommit(tenant, moduleFrom, moduleTo).compose(ures -> {
      if (moduleTo != null) {
        EventBus eb = vertx.eventBus();
        eb.publish(EVENT_NAME, tenant.getId());
      }
      return Future.succeededFuture();
    });
  }

  /**
   * start timers for all tenants.
   * @param discoveryManager discovery manager
   * @return async result
   */
  public Future<Void> startTimers(DiscoveryManager discoveryManager) {
    this.discoveryManager = discoveryManager;
    return tenants.getKeys().compose(res -> {
      for (String tenantId : res) {
        logger.info("starting {}", tenantId);
        handleTimer(tenantId);
      }
      consumeTimers();
      return Future.succeededFuture();
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
    tenants.getNotFound(tenantId).onComplete(tres -> {
      if (tres.failed()) {
        // tenant no longer exist
        stopTimer(tenantId, moduleId, seq1);
        return;
      }
      Tenant tenant = tres.result();
      moduleManager.getEnabledModules(tenant).onComplete(mres -> {
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
    HttpMethod httpMethod = re.getDefaultMethod(HttpMethod.POST);
    ModuleInstance inst = new ModuleInstance(md, re, path, httpMethod, true);
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    logger.info("timer call start module {} for tenant {}", md.getId(), tenantId);
    proxyService.callSystemInterface(headers, tenant, inst, "").onComplete(cres -> {
      if (cres.succeeded()) {
        logger.info("timer call succeeded to module {} for tenant {}",
            md.getId(), tenantId);
      } else {
        logger.info("timer call failed to module {} for tenant {} : {}",
            md.getId(), tenantId, cres.cause().getMessage());
      }
    });
  }

  private Future<Void> invokePermissionsForModule(Tenant tenant, ModuleDescriptor mdTo,
                                                  ModuleDescriptor permsModule, ProxyContext pc) {

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
      return Future.failedFuture(new OkapiError(ErrorType.USER,
          "Bad _tenantPermissions interface in module " + permsModule.getId()
              + ". No path to POST to"));
    }
    pc.debug("tenantPerms: " + permsModule.getId() + " and " + permPath);
    return proxyService.callSystemInterface(tenant, permInst, pljson, pc).compose(cres -> {
      pc.passOkapiTraceHeaders(cres);
      pc.debug("tenantPerms request to " + permsModule.getName()
          + " succeeded for module " + moduleTo + " and tenant " + tenant.getId());
      return Future.succeededFuture();
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
   * @return future (result==null if no tenant interface!)
   */
  private Future<ModuleInstance> getTenantInstanceForModule(
      ModuleDescriptor mdFrom,
      ModuleDescriptor mdTo, JsonObject jo, String tenantParameters, boolean purge) {

    ModuleDescriptor md = mdTo != null ? mdTo : mdFrom;
    InterfaceDescriptor[] prov = md.getProvidesList();
    for (InterfaceDescriptor pi : prov) {
      logger.debug("findTenantInterface: Looking at {}", pi.getId());
      if ("_tenant".equals(pi.getId())) {
        final String v = pi.getVersion();
        final String method = purge ? "DELETE" : "POST";
        ModuleInstance instance;
        switch (v) {
          case "1.0":
            if (mdTo != null || purge) {
              instance = getTenantInstanceForInterface(pi, mdFrom, mdTo, method);
              if (instance != null) {
                return Future.succeededFuture(instance);
              } else if (!purge) {
                logger.warn("Module '{}' uses old-fashioned tenant "
                    + "interface. Define InterfaceType=system, and add a RoutingEntry."
                    + " Falling back to calling /_/tenant.", md.getId());
                return Future.succeededFuture(new ModuleInstance(md, null,
                    "/_/tenant", HttpMethod.POST, true).withRetry());
              }
            }
            break;
          case "1.1":
            instance = getTenantInstanceForInterface(pi, mdFrom, mdTo, method);
            if (instance != null) {
              return Future.succeededFuture(instance);
            }
            break;
          case "1.2":
            putTenantParameters(jo, tenantParameters);
            instance = getTenantInstanceForInterface(pi, mdFrom, mdTo, method);
            if (instance != null) {
              return Future.succeededFuture(instance);
            }
            break;
          default:
            return Future.failedFuture(new OkapiError(ErrorType.USER,
                messages.getMessage("10401", v)));
        }
      }
    }
    return Future.succeededFuture(null);
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

  private ModuleInstance getTenantInstanceForInterface(
      InterfaceDescriptor pi, ModuleDescriptor mdFrom,
      ModuleDescriptor mdTo, String method) {

    ModuleDescriptor md = mdTo != null ? mdTo : mdFrom;
    if ("system".equals(pi.getInterfaceType())) {
      List<RoutingEntry> res = pi.getAllRoutingEntries();
      for (RoutingEntry re : res) {
        if (re.match(null, method)) {
          String pattern = re.getStaticPath();
          if (method.equals("DELETE")) {
            return new ModuleInstance(md, re, pattern, HttpMethod.DELETE, true);
          } else if ("/_/tenant/disable".equals(pattern)) {
            if (mdTo == null) {
              return new ModuleInstance(md, re, pattern, HttpMethod.POST, true);
            }
          } else if (mdTo != null) {
            return new ModuleInstance(md, re, pattern, HttpMethod.POST, true).withRetry();
          }
        }
      }
    }
    return null;
  }

  /**
   * Find (the first) module that provides a given system interface. Module must
   * be enabled for the tenant.
   *
   * @param tenant tenant to check for
   * @param interfaceName system interface to search for
   * @return future with ModuleDescriptor result (== null for not found)
   */

  private Future<ModuleDescriptor> findSystemInterface(Tenant tenant, String interfaceName) {
    return moduleManager.getEnabledModules(tenant).compose(res -> {
      for (ModuleDescriptor md : res) {
        if (md.getSystemInterface(interfaceName) != null) {
          return Future.succeededFuture(md);
        }
      }
      return Future.succeededFuture(null);
    });
  }

  Future<List<InterfaceDescriptor>> listInterfaces(String tenantId, boolean full,
                                                   String interfaceType) {
    return tenants.getNotFound(tenantId)
        .compose(tres -> listInterfaces(tres, full, interfaceType));
  }

  private Future<List<InterfaceDescriptor>> listInterfaces(Tenant tenant, boolean full,
                                                           String interfaceType) {
    return moduleManager.getEnabledModules(tenant).compose(modlist -> {
      List<InterfaceDescriptor> intList = new LinkedList<>();
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
      return Future.succeededFuture(intList);
    });
  }

  Future<List<ModuleDescriptor>> listModulesFromInterface(
      String tenantId, String interfaceName, String interfaceType) {

    return tenants.getNotFound(tenantId).compose(tenant -> {
      List<ModuleDescriptor> mdList = new LinkedList<>();
      return moduleManager.getEnabledModules(tenant).compose(modlist -> {
        for (ModuleDescriptor md : modlist) {
          for (InterfaceDescriptor provide : md.getProvidesList()) {
            if (interfaceName.equals(provide.getId())
                && (interfaceType == null || provide.isType(interfaceType))) {
              mdList.add(md);
              break;
            }
          }
        }
        return Future.succeededFuture(mdList);
      });
    });
  }

  Future<InstallJob> installUpgradeGet(String tenantId, String installId) {
    return tenants.getNotFound(tenantId).compose(x -> jobs.getNotFound(tenantId, installId));
  }

  Future<List<InstallJob>> installUpgradeGetList(String tenantId) {
    return tenants.getNotFound(tenantId).compose(x -> jobs.get(tenantId).compose(list -> {
      if (list == null) {
        return Future.succeededFuture(new LinkedList<>());
      }
      return Future.succeededFuture(list);
    }));
  }

  Future<List<TenantModuleDescriptor>> installUpgradeCreate(
      String tenantId, String installId, ProxyContext pc,
      TenantInstallOptions options, List<TenantModuleDescriptor> tml) {

    logger.info("installUpgradeCreate InstallId={}", installId);
    if (tml != null) {
      for (TenantModuleDescriptor tm : tml) {
        if (tm.getAction() == null) {
          return Future.failedFuture(new OkapiError(ErrorType.USER,
              messages.getMessage("10405", tm.getId())));
        }
      }
    }
    return tenants.getNotFound(tenantId).compose(tenant ->
        moduleManager.getModulesWithFilter(options.getPreRelease(),
            options.getNpmSnapshot(), null)
            .compose(modules -> {
              HashMap<String, ModuleDescriptor> modsAvailable = new HashMap<>(modules.size());
              HashMap<String, ModuleDescriptor> modsEnabled = new HashMap<>();
              for (ModuleDescriptor md : modules) {
                modsAvailable.put(md.getId(), md);
                logger.info("mod available: {}", md.getId());
                if (tenant.isEnabled(md.getId())) {
                  logger.info("mod enabled: {}", md.getId());
                  modsEnabled.put(md.getId(), md);
                }
              }
              InstallJob job = new InstallJob();
              job.setId(installId);
              if (tml == null) {
                job.setModules(upgrades(modsAvailable, modsEnabled));
              } else {
                job.setModules(tml);
              }
              job.setComplete(false);
              return runJob(tenant, pc, options, modsAvailable, modsEnabled, job);
            }));
  }

  private List<TenantModuleDescriptor> upgrades(
      Map<String, ModuleDescriptor> modsAvailable, Map<String, ModuleDescriptor> modsEnabled) {

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    for (String id : modsEnabled.keySet()) {
      ModuleId moduleId = new ModuleId(id);
      String latestId = moduleId.getLatest(modsAvailable.keySet());
      if (!latestId.equals(id)) {
        TenantModuleDescriptor tmd = new TenantModuleDescriptor();
        tmd.setAction(Action.enable);
        tmd.setId(latestId);
        logger.info("upgrade.. enable {}", latestId);
        tmd.setFrom(id);
        tml.add(tmd);
      }
    }
    return tml;
  }

  private Future<List<TenantModuleDescriptor>> runJob(
      Tenant t, ProxyContext pc, TenantInstallOptions options,
      Map<String, ModuleDescriptor> modsAvailable,
      Map<String, ModuleDescriptor> modsEnabled, InstallJob job) {

    List<TenantModuleDescriptor> tml = job.getModules();
    return DepResolution.installSimulate(modsAvailable, modsEnabled, tml).compose(res -> {
      if (options.getSimulate()) {
        return Future.succeededFuture(tml);
      }
      return jobs.add(t.getId(), job.getId(), job).compose(res2 -> {
        Promise<List<TenantModuleDescriptor>> promise = Promise.promise();
        Future<Void> future = Future.succeededFuture();
        if (options.getAsync()) {
          List<TenantModuleDescriptor> tml2 = new LinkedList<>();
          for (TenantModuleDescriptor tm : tml) {
            tml2.add(tm.cloneWithoutStatus());
          }
          promise.complete(tml2);
          future = future.compose(x -> {
            for (TenantModuleDescriptor tm : tml) {
              tm.setStatus(TenantModuleDescriptor.Status.idle);
            }
            return jobs.put(t.getId(), job.getId(), job);
          });
        }
        if (options.getDeploy()) {
          if (options.getAsync()) {
            future = future.compose(x -> {
              for (TenantModuleDescriptor tm : tml) {
                tm.setStatus(TenantModuleDescriptor.Status.deploy);
              }
              return jobs.put(t.getId(), job.getId(), job);
            });
          }
          future = future.compose(x -> autoDeploy(t, modsAvailable, tml));
        }
        for (TenantModuleDescriptor tm : tml) {
          if (options.getAsync()) {
            future = future.compose(x -> {
              tm.setStatus(TenantModuleDescriptor.Status.call);
              return jobs.put(t.getId(), job.getId(), job);
            });
          }
          if (options.getIgnoreErrors()) {
            Promise<Void> promise1 = Promise.promise();
            installTenantModule(t, pc, options, modsAvailable, tm).onComplete(x -> {
              if (x.failed()) {
                logger.warn("Ignoring error for tenant {} module {}",
                    t.getId(), tm.getId(), x.cause());
              }
              promise1.complete();
            });
            future = future.compose(x -> promise1.future());
          } else {
            future = future.compose(x -> installTenantModule(t, pc, options, modsAvailable, tm));
          }
          if (options.getAsync()) {
            future = future.compose(x -> {
              if (tm.getMessage() == null) {
                tm.setStatus(TenantModuleDescriptor.Status.done);
              }
              return jobs.put(t.getId(), job.getId(), job);
            });
          }
        }
        if (options.getDeploy()) {
          future.compose(x -> autoUndeploy(t, modsAvailable, tml));
        }
        future.onComplete(x -> {
          job.setComplete(true);
          jobs.put(t.getId(), job.getId(), job).onComplete(y -> logger.info("job complete"));
          if (options.getAsync()) {
            return;
          }
          if (x.failed()) {
            promise.fail(x.cause());
            return;
          }
          promise.complete(tml);
        });
        return promise.future();
      });
    });
  }

  private Future<Void> autoDeploy(Tenant t, Map<String, ModuleDescriptor> modsAvailable,
                                  List<TenantModuleDescriptor> tml) {
    List<Future> futures = new LinkedList<>();
    for (TenantModuleDescriptor tm : tml) {
      if (tm.getAction() == Action.enable || tm.getAction() == Action.uptodate) {
        ModuleDescriptor md = modsAvailable.get(tm.getId());
        futures.add(proxyService.autoDeploy(md)
            .onFailure(x -> tm.setMessage(x.getMessage())));
      }
    }
    return CompositeFuture.all(futures).mapEmpty();
  }

  private Future<Void> installTenantModule(Tenant tenant, ProxyContext pc,
                                           TenantInstallOptions options,
                                           Map<String, ModuleDescriptor> modsAvailable,
                                           TenantModuleDescriptor tm) {
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
    return enableAndDisableModule(tenant, options, mdFrom, mdTo, pc)
        .onFailure(x -> tm.setMessage(x.getMessage()))
        .mapEmpty();
  }

  private Future<Void> autoUndeploy(Tenant t, Map<String, ModuleDescriptor> modsAvailable,
                                  List<TenantModuleDescriptor> tml) {
    List<Future> futures = new LinkedList<>();
    for (TenantModuleDescriptor tm : tml) {
      futures.add(autoUndeploy(t, modsAvailable, tm));
    }
    return CompositeFuture.all(futures).mapEmpty();
  }

  private Future<Void> autoUndeploy(Tenant tenant, Map<String, ModuleDescriptor> modsAvailable,
                                    TenantModuleDescriptor tm) {
    ModuleDescriptor md = null;
    if (tm.getAction() == Action.enable) {
      md = modsAvailable.get(tm.getFrom());
    }
    if (tm.getAction() == Action.disable) {
      md = modsAvailable.get(tm.getId());
    }
    if (md == null) {
      return Future.succeededFuture();
    }
    final ModuleDescriptor mdF = md;
    return getModuleUser(md.getId()).compose(res -> {
      if (!res.isEmpty()) { // tenants using module, skip undeploy
        return Future.succeededFuture();
      }
      return proxyService.autoUndeploy(mdF);
    });
  }

  Future<List<ModuleDescriptor>> listModules(String id) {
    return tenants.getNotFound(id).compose(t -> {
      List<ModuleDescriptor> tl = new LinkedList<>();
      List<Future> futures = new LinkedList<>();
      for (String moduleId : t.listModules()) {
        futures.add(moduleManager.get(moduleId).compose(x -> {
          tl.add(x);
          return Future.succeededFuture();
        }));
      }
      return CompositeFuture.all(futures).compose(x -> Future.succeededFuture(tl));
    });
  }

  /**
   * Return tenants using module.
   * @param mod module Id
   * @return future with tenants that have this module enabled
   */
  public Future<List<String>> getModuleUser(String mod) {
    return tenants.getKeys().compose(kres -> {
      List<String> users = new LinkedList<>();
      List<Future> futures = new LinkedList<>();
      for (String tid : kres) {
        futures.add(tenants.get(tid).compose(t -> {
          if (t.isEnabled(mod)) {
            users.add(tid);
          }
          return Future.succeededFuture();
        }));
      }
      return CompositeFuture.all(futures).map(users);
    });
  }

  /**
   * Load tenants from the store into the shared memory map.
   *
   * @return fut future
   */
  private Future<Void> loadTenants() {
    return tenants.getKeys().compose(keys -> {
      if (!keys.isEmpty()) {
        return Future.succeededFuture();
      }
      return tenantStore.listTenants().compose(res -> {
        List<Future> futures = new LinkedList<>();
        for (Tenant t : res) {
          futures.add(tenants.add(t.getId(), t));
        }
        return CompositeFuture.all(futures).mapEmpty();
      });
    });
  }

} // class
