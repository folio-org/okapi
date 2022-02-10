package org.folio.okapi.managers;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.InstallJob;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.bean.Permission;
import org.folio.okapi.bean.PermissionList;
import org.folio.okapi.bean.RoutingEntry;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor.Action;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.ModuleId;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.service.Liveness;
import org.folio.okapi.service.TenantStore;
import org.folio.okapi.util.DepResolution;
import org.folio.okapi.util.LockedTypedMap1;
import org.folio.okapi.util.LockedTypedMap2;
import org.folio.okapi.util.ModuleCache;
import org.folio.okapi.util.OkapiError;
import org.folio.okapi.util.ProxyContext;
import org.folio.okapi.util.TenantInstallOptions;

/**
 * Manages the tenants in the shared map, and passes updates to the database.
 */
@java.lang.SuppressWarnings({"squid:S1192"}) // String literals should not be duplicated
public class TenantManager implements Liveness {

  private static final Logger logger = OkapiLogger.get();
  private final ModuleManager moduleManager;
  private ProxyService proxyService = null;
  private final TenantStore tenantStore;
  private LockedTypedMap1<Tenant> tenants = new LockedTypedMap1<>(Tenant.class);
  private static final String MAP_NAME = "tenants";
  private final LockedTypedMap2<InstallJob> jobs = new LockedTypedMap2<>(InstallJob.class);
  private static final String EVENT_NAME = "timer";
  private static final Messages messages = Messages.getInstance();
  private Vertx vertx;
  private final Map<String, ModuleCache> enabledModulesCache = new HashMap<>();
  // tenants with new permission module (_tenantPermissions version 1.1 or later)
  private final Map<String, Boolean> expandedModulesCache = new HashMap<>();
  private final boolean local;
  private static final int TENANT_INIT_DELAY = 300; // initial wait in ms
  private static final int TENANT_INIT_INCREASE = 1250;  // increase factor (/ 1000)
  private Consumer<String> tenantChangeConsumer;

  /**
   * Construct Tenant Manager.
   *
   * @param moduleManager module manager
   * @param tenantStore tenant storage
   * @param local if true, use local, in-process maps, only
   */
  public TenantManager(ModuleManager moduleManager, TenantStore tenantStore, boolean local) {
    this.moduleManager = moduleManager;
    this.tenantStore = tenantStore;
    this.local = local;
  }

  void setTenantsMap(LockedTypedMap1<Tenant> tenants) {
    this.tenants = tenants;
  }

  /**
   * Initialize the TenantManager.
   *
   * @param vertx Vert.x handle
   * @return fut future
   */
  public Future<Void> init(Vertx vertx) {
    this.vertx = vertx;

    return tenants.init(vertx, MAP_NAME, local)
        .compose(x -> jobs.init(vertx, "installJobs", local))
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

  Future<Collection<String>> allTenants() {
    return tenants.getKeys();
  }

  Future<List<TenantDescriptor>> list() {
    return tenants.getKeys().compose(lres -> {
      List<Future<Void>> futures = new LinkedList<>();
      List<TenantDescriptor> tdl = new LinkedList<>();
      for (String s : lres) {
        futures.add(tenants.getNotFound(s).compose(res -> {
          tdl.add(res.getDescriptor());
          return Future.succeededFuture();
        }));
      }
      return GenericCompositeFuture.all(futures).map(tdl);
    });
  }

  /**
   * Get a tenant.
   *
   * @param tenantId tenant ID
   * @return fut future
   */
  public Future<Tenant> get(String tenantId) {
    return tenants.getNotFound(tenantId);
  }

  /**
   * Delete a tenant.
   *
   * @param tenantId tenant ID
   * @return future .. OkapiError if tenantId not found
   */
  public Future<Void> delete(String tenantId) {
    return tenantStore.delete(tenantId).compose(x -> {
      if (Boolean.FALSE.equals(x)) {
        return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND, tenantId));
      }
      return tenants.removeNotFound(tenantId).mapEmpty();
    }).compose(x -> reloadEnabledModules(tenantId));
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
    }).compose(x -> reloadEnabledModules(t));
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

  Future<Void> enableAndDisableCheck(Tenant tenant, ModuleDescriptor modFrom,
                                     ModuleDescriptor modTo) {

    List<ModuleDescriptor> modlist = getEnabledModules(tenant);
    HashMap<String, ModuleDescriptor> mods = new HashMap<>(modlist.size());
    for (ModuleDescriptor md : modlist) {
      mods.put(md.getId(), md);
    }
    if (modTo == null) {
      List<String> errors = DepResolution.checkEnabled(mods);
      if (!errors.isEmpty()) {
        logger.warn("Skip check when disabling {} as dependencies are inconsistent already",
            modFrom.getId());
        return Future.succeededFuture(); // failures even before we remove a module
      }
    }
    if (modFrom != null) {
      mods.remove(modFrom.getId());
    }
    if (modTo != null) {
      ModuleDescriptor already = mods.get(modTo.getId());
      if (already != null) {
        return Future.failedFuture(new OkapiError(ErrorType.USER,
            "Module " + modTo.getId() + " already provided"));
      }
      mods.put(modTo.getId(), modTo);
    }

    List<String> errors = DepResolution.checkEnabled(mods);
    if (!errors.isEmpty()) {
      return Future.failedFuture(new OkapiError(ErrorType.USER, String.join(". ", errors)));
    }
    return Future.succeededFuture();
  }

  Future<String> enableAndDisableModule(
      String tenantId, TenantInstallOptions options, String moduleFrom,
      TenantModuleDescriptor td, ProxyContext pc) {

    return tenants.getNotFound(tenantId).compose(tenant ->
        enableAndDisableModule(tenant, options, moduleFrom, td != null ? td.getId() : null, pc));
  }

  private Future<String> enableAndDisableModule(
      Tenant tenant, TenantInstallOptions options, String moduleFrom,
      String moduleTo, ProxyContext pc) {

    Future<ModuleDescriptor> mdFrom = moduleFrom != null
        ? moduleManager.get(moduleFrom) : Future.succeededFuture(null);
    Future<ModuleDescriptor> mdTo = moduleTo != null
        ? moduleManager.getLatest(moduleTo) : Future.succeededFuture(null);
    return mdFrom
        .compose(x -> mdTo)
        .compose(x -> options.getDepCheck()
              ? enableAndDisableCheck(tenant, mdFrom.result(), mdTo.result())
              : Future.succeededFuture())
        .compose(x -> enableAndDisableModule(tenant, options, mdFrom.result(),
            mdTo.result(), pc));
  }

  private Future<String> enableAndDisableModule(Tenant tenant, TenantInstallOptions options,
                                                ModuleDescriptor mdFrom, ModuleDescriptor mdTo,
                                                ProxyContext pc) {
    if (mdFrom == null && mdTo == null) {
      return Future.succeededFuture("");
    }
    return invokePermissions(tenant, options, mdFrom, mdTo, pc)
        .compose(x -> invokeTenantInterface(tenant, options, mdFrom, mdTo, pc))
        .compose(x -> invokePermissionsPermMod(tenant, options, mdTo, pc))
        .compose(x -> reloadPermissions(tenant, options, mdFrom, mdTo, pc))
        .compose(x -> commitModuleChange(tenant, mdFrom, mdTo))
        .compose(x -> Future.succeededFuture((mdTo != null ? mdTo.getId() : ""))
    );
  }

  private void waitTenantInit(Tenant tenant, ModuleInstance getInstance,
                              ModuleInstance deleteInstance, ProxyContext pc,
                              Promise<Void> promise, long waitMs) {
    proxyService.callSystemInterface(tenant.getId(), getInstance, "", pc)
        .onFailure(promise::fail)
        .onSuccess(cli -> {
          JsonObject obj = new JsonObject(cli.getResponsebody());
          Boolean complete = obj.getBoolean("complete");
          if (Boolean.TRUE.equals(complete)) {
            proxyService.callSystemInterface(tenant.getId(), deleteInstance, "", pc)
                .onFailure(promise::fail)
                .onSuccess(x -> {
                  String error = obj.getString("error");
                  if (error == null) {
                    promise.complete();
                    return;
                  }
                  // a shame that we must make a structured JSON response into a text response.
                  // We have to stuff it into one element: TenantModuleDescriptor.message
                  StringBuilder message = new StringBuilder(error);
                  JsonArray ar = obj.getJsonArray("messages");
                  if (ar != null) {
                    for (int i = 0; i < ar.size(); i++) {
                      message.append("\n");
                      message.append(ar.getString(i));
                    }
                  }
                  promise.fail(messages.getMessage("10410",
                      getInstance.getModuleDescriptor().getId(), message.toString()));
                });
            return;
          }
          vertx.setTimer(waitMs, x ->
              waitTenantInit(tenant, getInstance, deleteInstance, pc, promise,
                  (waitMs * TENANT_INIT_INCREASE) / 1000));
        });
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

    Future<ModuleDescriptor> future;
    if (mdTo != null && options.getPurge()) {
      // enable with purge is turned into purge on its own, followed by regular enable
      future = invokeTenantInterface1(tenant, options, mdTo, null, pc)
          .otherwise(res -> {
            logger.info("Tenant purge error for module {} ignored: {}",
                mdTo.getId(), res.getMessage());
            return null;
          })
          .map(x -> {
            // from this point, enable (no upgrade) and no purge
            options.setPurge(false);
            return null;
          });
    } else {
      future = Future.succeededFuture(mdFrom);
    }
    return future.compose(from -> invokeTenantInterface1(tenant, options, from, mdTo, pc));
  }

  private Future<Void> invokeTenantInterface1(Tenant tenant, TenantInstallOptions options,
      ModuleDescriptor mdFrom, ModuleDescriptor mdTo,
      ProxyContext pc) {
    JsonObject jo = new JsonObject();
    if (mdTo != null) {
      jo.put("module_to", mdTo.getId());
    }
    if (mdFrom != null) {
      jo.put("module_from", mdFrom.getId());
    }
    String tenantParameters = options.getTenantParameters();
    boolean purge = mdTo == null && options.getPurge();
    ModuleDescriptor md = mdTo != null ? mdTo : mdFrom;
    if (!options.checkInvoke(md.getId())) {
      return Future.succeededFuture();
    }
    long startTime = System.nanoTime();
    logger.info("Starting activation of module '{}' for tenant '{}'", md.getId(), tenant.getId());
    return getTenantInstanceForModule(md, mdFrom, mdTo, jo, tenantParameters, purge)
        .compose(instances -> {
          if (instances.isEmpty()) {
            logger.info("{}: has no support for tenant init", md.getId());
            return Future.succeededFuture();
          }
          ModuleInstance postInstance = instances.get(0);
          String req = HttpMethod.DELETE.equals(postInstance.getMethod())
              ? "" : jo.encodePrettily();
          return proxyService.callSystemInterface(tenant.getId(), postInstance, req, pc)
              .compose(cres -> {
                pc.passOkapiTraceHeaders(cres);
                String location = cres.getRespHeaders().get("Location");
                if (location == null) {
                  return Future.succeededFuture(); // sync v1 / v2
                }
                if (instances.size() != 3) {
                  return Future.failedFuture(messages.getMessage(
                      "10409", md.getId(), postInstance.getMethod(), postInstance.getPath()));
                }
                JsonObject obj = new JsonObject(cres.getResponsebody());
                String id = obj.getString("id");
                if (id == null) {
                  return Future.failedFuture(messages.getMessage("10408",
                      md.getId(), postInstance.getMethod().name(), postInstance.getPath()));
                }
                ModuleInstance getInstance = instances.get(1);
                getInstance.setUrl(postInstance.getUrl()); // same URL for POST & GET
                getInstance.substPathId(id);
                ModuleInstance deleteInstance = instances.get(2);
                deleteInstance.setUrl(postInstance.getUrl()); // same URL for POST & DELETE
                deleteInstance.substPathId(id);
                Promise<Void> promise = Promise.promise();
                waitTenantInit(tenant, getInstance, deleteInstance, pc, promise, TENANT_INIT_DELAY);
                return promise.future();
              });
        })
        .onSuccess(res -> logger.info(
            "Activation of module '{}' for tenant '{}' completed successfully in {} seconds",
            md.getId(), tenant.getId(), (System.nanoTime() - startTime) / 1000000000L))
        .onFailure(e -> logger.warn(
            "Activation of module '{}' for tenant '{}' failed: {}", md.getId(), tenant.getId(),
            e.getMessage()));
  }

  /**
   * If enabling non-permissions module, announce permissions to permissions module if enabled.
   *
   * @param tenant tenant
   * @param options install options
   * @param mdFrom module from - is null if enabling for the first time
   * @param mdTo module to - is null if disabling
   * @param pc proxy content
   * @return Future
   */
  private Future<Void> invokePermissions(Tenant tenant, TenantInstallOptions options,
      ModuleDescriptor mdFrom, ModuleDescriptor mdTo, ProxyContext pc) {
    if (mdTo != null && mdTo.getSystemInterface("_tenantPermissions") != null) {
      return Future.succeededFuture();
    }
    ModuleDescriptor md = findSystemInterface(tenant, "_tenantPermissions");
    if (md == null || !options.checkInvoke(md.getId())) {
      return Future.succeededFuture();
    }
    return invokePermissionsForModule(tenant, mdFrom, mdTo, md, pc);
  }

  /**
   * If enabling permissions module it, announce permissions to it.
   *
   * @param tenant tenant
   * @param options install options
   * @param mdTo module to
   * @param pc proxy context
   * @return fut response
   */
  private Future<Void> invokePermissionsPermMod(Tenant tenant, TenantInstallOptions options,
                                                ModuleDescriptor mdTo, ProxyContext pc) {
    if (mdTo == null || !options.checkInvoke(mdTo.getId())
        || mdTo.getSystemInterface("_tenantPermissions") == null) {
      return Future.succeededFuture();
    }
    // enabling permissions module.
    return invokePermissionsForModule(tenant,null, mdTo, mdTo, pc);
  }

  /**
   * Conditionally announce permissions for a set of modules to a permissions module.
   *
   * <p>This will only happen if the permissions module is being enabled for the first time,
   * not when it's upgraded.
   *
   * @param tenant tenant
   * @param mdFrom permissions module from
   * @param mdTo permissions module to
   * @param pc ProxyContext
   * @return future
   */
  private Future<Void> reloadPermissions(Tenant tenant, TenantInstallOptions options,
                                         ModuleDescriptor mdFrom, ModuleDescriptor mdTo,
                                         ProxyContext pc) {

    Future<Void> future = Future.succeededFuture();
    // only reload if mod-permissions is being enabled (not when upgraded)
    if (mdFrom == null && mdTo != null && options.checkInvoke(mdTo.getId())
        && mdTo.getSystemInterface("_tenantPermissions") != null) {
      for (String mdid : tenant.listModules()) {
        future = future.compose(x -> moduleManager.get(mdid)
            .compose(md -> invokePermissionsForModule(tenant, null, md, mdTo, pc)));
      }
    }
    return future;
  }

  /**
   * Commit change of module for tenant and publish on event bus about it.
   *
   * @param tenant tenant
   * @param mdFrom module from (null if new module)
   * @param mdTo module to (null if module is removed)
   * @return future
   */
  private Future<Void> commitModuleChange(Tenant tenant, ModuleDescriptor mdFrom,
                                          ModuleDescriptor mdTo) {

    String moduleFrom = mdFrom != null ? mdFrom.getId() : null;
    String moduleTo = mdTo != null ? mdTo.getId() : null;

    return updateModuleCommit(tenant, moduleFrom, moduleTo)
        .compose(x -> {
          EventBus eb = vertx.eventBus();
          eb.publish(EVENT_NAME, tenant.getId());
          return Future.succeededFuture();
        });
  }

  /**
   * prepare module cache and upgrade Okapi.
   * @return async result
   */
  public Future<Void> prepareModules(String okapiVersion) {
    final ModuleDescriptor md = InternalModule.moduleDescriptor(okapiVersion);
    return tenants.getKeys().compose(res -> {
      Future<Void> future = Future.succeededFuture();
      for (String tenantId : res) {
        future = future.compose(x -> reloadEnabledModules(tenantId));
      }
      for (String tenantId : res) {
        future = future.compose(x -> upgradeOkapiModule(tenantId, md));
      }
      consumeTenantChange();
      return future;
    });
  }

  private Future<Void> upgradeOkapiModule(String tenantId, ModuleDescriptor md) {
    return get(tenantId).compose(tenant -> {
      String moduleTo = md.getId();
      Set<String> enabledMods = tenant.getEnabled().keySet();
      String enver = null;
      for (String emod : enabledMods) {
        if (emod.startsWith("okapi-")) {
          enver = emod;
        }
      }
      logger.info("Checking okapi module for tenant {} md={}", tenantId, moduleTo);
      String moduleFrom = enver;
      if (moduleFrom == null) {
        logger.info("Tenant {} does not have okapi module enabled already", tenantId);
        return Future.succeededFuture();
      }
      if (moduleFrom.equals(moduleTo)) {
        logger.info("Tenant {} has module {} enabled already", tenantId, moduleTo);
        return Future.succeededFuture();
      }
      if (ModuleId.compare(moduleFrom, moduleTo) >= 4) {
        logger.warn("Will not downgrade tenant {} from {} to {}", tenantId, moduleTo, moduleFrom);
        return Future.succeededFuture();
      }
      logger.info("Tenant {} moving from {} to {}", tenantId, moduleFrom, moduleTo);
      TenantInstallOptions options = new TenantInstallOptions();
      return invokePermissions(tenant, options, null, md, null).compose(x ->
          updateModuleCommit(tenant, moduleFrom, moduleTo));
    });
  }

  private void consumeTenantChange() {
    EventBus eb = vertx.eventBus();
    eb.consumer(EVENT_NAME, res -> {
      String tenantId = (String) res.body();
      reloadEnabledModules(tenantId).onSuccess(x -> {
        if (tenantChangeConsumer != null) {
          tenantChangeConsumer.accept(tenantId);
        }
      });
    });
  }

  void setTenantChange(Consumer<String> consumer) {
    tenantChangeConsumer = consumer;
  }

  private Permission[] stripPermissionReplaces(Permission[] perms) {
    if (perms != null) {
      for (Permission perm : perms) {
        if (perm.getReplaces() != null) {
          perm.setReplaces(null);
        }
      }
    }
    return perms;
  }

  private Future<Void> invokePermissionsForModule(Tenant tenant,
                                                  ModuleDescriptor mdFrom, ModuleDescriptor mdTo,
                                                  ModuleDescriptor permsModule, ProxyContext pc) {

    PermissionList pl;
    InterfaceDescriptor permInt = permsModule.getSystemInterface("_tenantPermissions");
    String permIntVer = permInt.getVersion();
    switch (permIntVer) {
      case "1.0":
        if (mdTo == null) {
          return Future.succeededFuture();
        }
        pl = new PermissionList(mdTo.getId(), stripPermissionReplaces(mdTo.getPermissionSets()));
        break;
      case "1.1":
        if (mdTo == null) {
          return Future.succeededFuture();
        }
        pl = new PermissionList(mdTo.getId(), stripPermissionReplaces(
            mdTo.getExpandedPermissionSets()));
        break;
      case "2.0":
        if (mdTo != null) {
          pl = new PermissionList(mdTo.getId(), mdTo.getExpandedPermissionSets());
        } else  {
          // attempt an empty list for the module being disabled
          pl = new PermissionList(mdFrom.getId(), new Permission[0]);
        }
        break;
      default:
        return Future.failedFuture(new OkapiError(ErrorType.USER,
            "Unknown version of _tenantPermissions interface in use " + permIntVer + "."));
    }
    String pljson = Json.encodePrettily(pl);
    logger.info("tenantPerms Req: {}", pljson);
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
    logger.debug("tenantPerms: {} and {}", permsModule.getId(), permPath);
    if (pc == null) {
      MultiMap headersIn = MultiMap.caseInsensitiveMultiMap();
      return proxyService.doCallSystemInterface(headersIn, tenant.getId(), null,
          permInst, null, pljson).mapEmpty();
    }
    return proxyService.callSystemInterface(tenant.getId(), permInst, pljson, pc).compose(cres -> {
      pc.passOkapiTraceHeaders(cres);
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
   * @param md module ("to" if available otherwise "from")
   * @param mdFrom module from
   * @param mdTo module to
   * @param jo Json Object to be POSTed
   * @param tenantParameters tenant parameters (eg sample data)
   * @param purge true if purging (DELETE)
   * @return future empty ModuleInstance list if no tenant interface
   */
  static Future<List<ModuleInstance>> getTenantInstanceForModule(
      ModuleDescriptor md, ModuleDescriptor mdFrom,
      ModuleDescriptor mdTo, JsonObject jo, String tenantParameters, boolean purge) {

    InterfaceDescriptor[] prov = md.getProvidesList();
    List<ModuleInstance> instances = new LinkedList<>();
    for (InterfaceDescriptor pi : prov) {
      logger.debug("findTenantInterface: Looking at {}", pi.getId());
      if ("_tenant".equals(pi.getId())) {
        final String v = pi.getVersion();
        final String method = purge ? "DELETE" : "POST";
        ModuleInstance instance = null;
        switch (v) {
          case "1.0":
            if (mdTo != null || purge) {
              instance = getTenantInstanceForInterface(pi, mdFrom, mdTo, method);
              if (instance == null && !purge) {
                logger.warn("Module '{}' uses old-fashioned tenant "
                    + "interface. Define InterfaceType=system, and add a RoutingEntry."
                    + " Falling back to calling /_/tenant.", md.getId());
                return Future.succeededFuture(Collections.singletonList(new ModuleInstance(md, null,
                    "/_/tenant", HttpMethod.POST, true).withRetry()));
              }
            }
            break;
          case "1.1":
            instance = getTenantInstanceForInterface(pi, mdFrom, mdTo, method);
            break;
          case "1.2":
            putTenantParameters(jo, tenantParameters);
            instance = getTenantInstanceForInterface(pi, mdFrom, mdTo, method);
            break;
          case "2.0":
            jo.put("purge", purge);
            putTenantParameters(jo, tenantParameters);
            instance = getTenantInstanceForInterfacev2(pi, md, "POST", "/");
            if (instance == null) {
              return Future.succeededFuture(instances);
            }
            instances.add(instance);
            instance = getTenantInstanceForInterfacev2(pi, md, "GET", "{id}");
            if (instance == null) {
              return Future.succeededFuture(instances); // OK only POST method for v2
            }
            instances.add(instance);
            // both DELETE and GET must be present
            instance = getTenantInstanceForInterfacev2(pi, md, "DELETE", "{id}");
            if (instance == null) {
              return Future.failedFuture(messages.getMessage("10407", md.getId()));
            }
            // 0: POST, 1: GET, 2:DELETE
            break;
          default:
            return Future.failedFuture(new OkapiError(ErrorType.USER,
                messages.getMessage("10401", v)));
        }
        if (instance != null) {
          instances.add(instance);
        }
      }
    }
    return Future.succeededFuture(instances);
  }

  private static void putTenantParameters(JsonObject jo, String tenantParameters) {
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

  private static ModuleInstance getTenantInstanceForInterface(
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

  private static ModuleInstance getTenantInstanceForInterfacev2(
      InterfaceDescriptor pi, ModuleDescriptor md, String method, String mustContain) {

    if ("system".equals(pi.getInterfaceType())) {
      List<RoutingEntry> res = pi.getAllRoutingEntries();
      for (RoutingEntry re : res) {
        if (re.match(null, method) && re.getStaticPath().contains(mustContain)) {
          return new ModuleInstance(md, re, re.getStaticPath(), HttpMethod.valueOf(method), true)
              .withRetry();
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
   * @return found ModuleDescriptor result or null for not found
   */

  private ModuleDescriptor findSystemInterface(Tenant tenant, String interfaceName) {
    for (ModuleDescriptor md : getEnabledModules(tenant)) {
      if (md.getSystemInterface(interfaceName) != null) {
        return md;
      }
    }
    return null;
  }

  Future<List<InterfaceDescriptor>> listInterfaces(String tenantId, boolean full,
                                                   String interfaceType) {
    return tenants.getNotFound(tenantId)
        .map(tres -> listInterfaces(tres, full, interfaceType));
  }

  private List<InterfaceDescriptor> listInterfaces(Tenant tenant, boolean full,
                                                           String interfaceType) {
    List<InterfaceDescriptor> intList = new LinkedList<>();
    Set<String> ids = new HashSet<>();
    for (ModuleDescriptor md : getEnabledModules(tenant)) {
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
    return intList;
  }

  Future<List<ModuleDescriptor>> listModulesFromInterface(
      String tenantId, String interfaceName, String interfaceType) {

    List<ModuleDescriptor> mdList = new LinkedList<>();
    return getEnabledModules(tenantId).compose(modlist -> {
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
  }

  Future<InstallJob> installUpgradeGet(String tenantId, String installId) {
    return tenants.getNotFound(tenantId).compose(x -> jobs.getNotFound(tenantId, installId));
  }

  Future<Void> installUpgradeDelete(String tenantId, String installId) {
    return installUpgradeGet(tenantId, installId).compose(job -> {
      if (!Boolean.TRUE.equals(job.getComplete())) {
        return Future.failedFuture(new OkapiError(ErrorType.USER,
            messages.getMessage("10406", installId)));
      }
      return jobs.removeNotFound(tenantId, installId);
    });
  }

  Future<List<InstallJob>> installUpgradeGetList(String tenantId) {
    return tenants.getNotFound(tenantId)
        .compose(x -> jobs.get(tenantId)
        .map(list -> Objects.requireNonNullElseGet(list, LinkedList::new)));
  }

  Future<Void> installUpgradeDeleteList(String tenantId) {
    return installUpgradeGetList(tenantId).compose(res -> {
      Future<Void> future = Future.succeededFuture();
      for (InstallJob job : res) {
        if (Boolean.TRUE.equals(job.getComplete())) {
          future = future.compose(x -> jobs.removeNotFound(tenantId, job.getId()));
        }
      }
      return future;
    });
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
        moduleManager.getModulesWithFilter(options.getModuleVersionFilter(), null)
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
              job.setStartDate(Instant.now().toString());
              job.setModules(Objects.requireNonNullElseGet(tml,
                  () -> upgrades(modsAvailable, modsEnabled)));
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
    DepResolution.install(modsAvailable, modsEnabled, tml, options.getReinstall());
    if (options.getSimulate()) {
      return Future.succeededFuture(tml);
    }
    return jobs.add(t.getId(), job.getId(), job)
        .compose(res2 -> runJob(t, pc, options, tml, modsAvailable, job));
  }

  private Future<List<TenantModuleDescriptor>> runJob(
      Tenant t, ProxyContext pc, TenantInstallOptions options,
      List<TenantModuleDescriptor> tml,
      Map<String, ModuleDescriptor> modsAvailable, InstallJob job) {

    Promise<List<TenantModuleDescriptor>> promise = Promise.promise();
    Future<Void> future = Future.succeededFuture();
    if (options.getAsync()) {
      List<TenantModuleDescriptor> tml2 = new LinkedList<>();
      for (TenantModuleDescriptor tm : tml) {
        tml2.add(tm.cloneWithoutStage());
      }
      promise.complete(tml2);
    }
    future = future.compose(x -> {
      for (TenantModuleDescriptor tm : tml) {
        tm.setStage(TenantModuleDescriptor.Stage.pending);
      }
      return jobs.put(t.getId(), job.getId(), job);
    });
    if (options.getDeploy()) {
      future = future.compose(x -> autoDeploy(t, job, modsAvailable, tml));
    }
    for (TenantModuleDescriptor tm : tml) {
      future = future.compose(x -> {
        tm.setStage(TenantModuleDescriptor.Stage.invoke);
        return jobs.put(t.getId(), job.getId(), job);
      });
      future = future.compose(x -> installTenantModule(t, pc, options, modsAvailable, tm));
      if (options.getIgnoreErrors()) {
        future = future.otherwise(e -> {
          logger.warn("Ignoring error for tenant {} module {}", t.getId(), tm.getId(), e);
          return null;
        });
      }
      future = future.compose(x -> {
        if (tm.getMessage() != null) {
          return Future.succeededFuture();
        }
        tm.setStage(TenantModuleDescriptor.Stage.done);
        return jobs.put(t.getId(), job.getId(), job);
      });
    }
    // if we are really upgrading permissions do a refresh last
    for (TenantModuleDescriptor tm : tml) {
      if (tm.getAction() == Action.enable && tm.getFrom() != null) {
        ModuleDescriptor mdTo = modsAvailable.get(tm.getId());
        // mdFrom is null so reloadPermissions is triggered!!!!
        future = future.compose(x -> reloadPermissions(t, options, null, mdTo, pc));
      }
    }
    if (options.getDeploy()) {
      future.compose(x -> autoUndeploy(t, job, modsAvailable, tml));
    }
    for (TenantModuleDescriptor tm : tml) {
      future = future.compose(x -> {
        if (tm.getMessage() != null) {
          return Future.succeededFuture();
        }
        tm.setStage(TenantModuleDescriptor.Stage.done);
        return jobs.put(t.getId(), job.getId(), job);
      });
    }
    future.onComplete(x -> {
      job.setEndDate(Instant.now().toString());
      job.setComplete(true);
      jobs.put(t.getId(), job.getId(), job).onComplete(y -> logger.info("job complete"));
      if (options.getAsync()) {
        return;
      }
      if (x.failed()) {
        logger.warn("job failed", x.cause());
        promise.fail(x.cause());
        return;
      }
      List<TenantModuleDescriptor> tml2 = new LinkedList<>();
      for (TenantModuleDescriptor tm : tml) {
        tml2.add(tm.cloneWithoutStage());
      }
      promise.complete(tml2);
    });
    return promise.future();
  }

  private Future<Void> autoDeploy(Tenant tenant, InstallJob job, Map<String,
      ModuleDescriptor> modsAvailable, List<TenantModuleDescriptor> tml) {

    List<Future<Void>> futures = new LinkedList<>();
    for (TenantModuleDescriptor tm : tml) {
      if (tm.getAction() == Action.enable || tm.getAction() == Action.uptodate) {
        ModuleDescriptor md = modsAvailable.get(tm.getId());
        tm.setStage(TenantModuleDescriptor.Stage.deploy);
        futures.add(jobs.put(tenant.getId(), job.getId(), job).compose(res ->
            proxyService.autoDeploy(md)
                .onFailure(x -> tm.setMessage(x.getMessage()))));
      }
    }
    return GenericCompositeFuture.all(futures).mapEmpty();
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

  private Future<Void> autoUndeploy(Tenant tenant, InstallJob job,
                                    Map<String, ModuleDescriptor> modsAvailable,
                                    List<TenantModuleDescriptor> tml) {

    List<Future<Void>> futures = new LinkedList<>();
    for (TenantModuleDescriptor tm : tml) {
      futures.add(autoUndeploy(tenant, job, modsAvailable, tm));
    }
    return GenericCompositeFuture.all(futures).mapEmpty();
  }

  private Future<Void> autoUndeploy(Tenant tenant, InstallJob job,
                                    Map<String, ModuleDescriptor> modsAvailable,
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
      tm.setStage(TenantModuleDescriptor.Stage.undeploy);
      return jobs.put(tenant.getId(), job.getId(), job).compose(x ->
          proxyService.autoUndeploy(mdF));
    });
  }

  Future<List<ModuleDescriptor>> listModules(String id) {
    return tenants.getNotFound(id).compose(t -> {
      List<ModuleDescriptor> tl = new LinkedList<>();
      List<Future<Void>> futures = new LinkedList<>();
      for (String moduleId : t.listModules()) {
        futures.add(moduleManager.get(moduleId).compose(x -> {
          tl.add(x);
          return Future.succeededFuture();
        }));
      }
      return GenericCompositeFuture.all(futures).map(tl);
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
      List<Future<Void>> futures = new LinkedList<>();
      for (String tid : kres) {
        futures.add(tenants.get(tid).compose(t -> {
          if (t.isEnabled(mod)) {
            users.add(tid);
          }
          return Future.succeededFuture();
        }));
      }
      return GenericCompositeFuture.all(futures).map(users);
    });
  }

  /**
   * Return list of modules by all tenants.
   * @return map with key of module ID, and value of one tenant using it (there could be others).
   */
  public Future<Map<String, String>> getEnabledModulesAllTenants() {
    return tenants.getKeys().compose(kres -> {
      Map<String,String> enabled = new HashMap<>();
      Future<Void> future = Future.succeededFuture();
      for (String tid : kres) {
        future = future.compose(x -> tenants.get(tid)
            .compose(t -> {
              for (String m : t.getEnabled().keySet()) {
                enabled.put(m, t.getId());
              }
              return Future.succeededFuture();
            })
        );
      }
      return future.map(x -> enabled);
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
        List<Future<Void>> futures = new LinkedList<>();
        for (Tenant t : res) {
          futures.add(tenants.add(t.getId(), t));
        }
        return GenericCompositeFuture.all(futures).mapEmpty();
      });
    });
  }

  /**
   * Get module cache for tenant.
   */
  public ModuleCache getModuleCache(String tenantId) {
    if (!enabledModulesCache.containsKey(tenantId)) {
      return new ModuleCache(new LinkedList<>());
    }
    return enabledModulesCache.get(tenantId);
  }

  /**
   * Return modules enabled for tenant.
   *
   * @param tenantId tenant identifier.
   * @return list of modules
   */
  public Future<List<ModuleDescriptor>> getEnabledModules(String tenantId) {
    return tenants.getNotFound(tenantId)
        .map(this::getEnabledModules);
  }

  /**
   * Return modules enabled for tenant.
   *
   * @param tenant Tenant
   * @return list of modules
   */
  public List<ModuleDescriptor> getEnabledModules(Tenant tenant) {
    return getModuleCache(tenant.getId()).getModules();
  }

  private Future<Void> reloadEnabledModules(String tenantId) {
    return tenants.get(tenantId).compose(tenant -> {
      if (tenant == null) {
        enabledModulesCache.remove(tenantId);
        return Future.succeededFuture();
      }
      return reloadEnabledModules(tenant);
    });
  }

  private Future<Void> reloadEnabledModules(Tenant tenant) {
    if (moduleManager == null) {
      return Future.succeededFuture(); // only happens in tests really
    }
    List<ModuleDescriptor> mdl = new LinkedList<>();
    List<Future<Void>> futures = new LinkedList<>();
    for (String tenantId : tenant.getEnabled().keySet()) {
      futures.add(moduleManager.get(tenantId).compose(md -> {
        InterfaceDescriptor id = md.getSystemInterface("_tenantPermissions");
        if (id != null) {
          expandedModulesCache.put(tenant.getId(), !id.getVersion().equals("1.0"));
        }
        mdl.add(md);
        return Future.succeededFuture();
      }));
    }
    return GenericCompositeFuture.all(futures).compose(res -> {
      enabledModulesCache.put(tenant.getId(), new ModuleCache(mdl));
      return Future.succeededFuture();
    });
  }

  /**
   * Return if permissions should be expanded.
   * @param tenantId Tenant ID
   * @return TRUE if expansion; FALSE if no expansion; null if not known
   */
  Boolean getExpandModulePermissions(String tenantId) {
    return expandedModulesCache.get(tenantId);
  }

  @Override
  public Future<Void> isAlive() {
    return tenantStore.listTenants().mapEmpty();
  }
} // class
