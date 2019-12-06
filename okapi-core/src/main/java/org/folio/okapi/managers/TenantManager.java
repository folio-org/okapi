package org.folio.okapi.managers;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.Lock;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.bean.PermissionList;
import org.folio.okapi.bean.RoutingEntry;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.util.TenantInstallOptions;
import org.folio.okapi.bean.TenantModuleDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor.Action;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;
import org.folio.okapi.util.CompList;
import org.folio.okapi.util.LockedTypedMap1;
import org.folio.okapi.common.ModuleId;
import org.folio.okapi.service.TenantStore;
import org.folio.okapi.util.AsyncLock;
import org.folio.okapi.util.DepResolution;
import org.folio.okapi.util.ProxyContext;

/**
 * Manages the tenants in the shared map, and passes updates to the database.
 */
@java.lang.SuppressWarnings({"squid:S1192"})
public class TenantManager {

  private final Logger logger = OkapiLogger.get();
  private ModuleManager moduleManager = null;
  private ProxyService proxyService = null;
  private TenantStore tenantStore = null;
  private LockedTypedMap1<Tenant> tenants = new LockedTypedMap1<>(Tenant.class);
  private String mapName = "tenants";
  private String eventName = "timer";
  private Set<String> timers = new HashSet<>();
  private Messages messages = Messages.getInstance();
  private AsyncLock asyncLock;
  private Vertx vertx;

  public TenantManager(ModuleManager moduleManager, TenantStore tenantStore) {
    this.moduleManager = moduleManager;
    this.tenantStore = tenantStore;
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
   * @param vertx
   * @param fut
   */
  public void init(Vertx vertx, Handler<ExtendedAsyncResult<Void>> fut) {
    this.vertx = vertx;
    asyncLock = new AsyncLock(vertx);

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
   * @param px
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
   * @param t
   * @param fut
   */
  public void insert(Tenant t, Handler<ExtendedAsyncResult<String>> fut) {
    String id = t.getId();
    tenants.get(id, gres -> {
      if (gres.succeeded()) {
        fut.handle(new Failure<>(ErrorType.USER, messages.getMessage("10400", id)));
      } else if (gres.getType() == ErrorType.NOT_FOUND) {
        if (tenantStore == null) { // no db, just add it to shared mem
          insert2(t, id, fut);
        } else { // insert into db first
          tenantStore.insert(t, res -> {
            if (res.failed()) {
              logger.warn("TenantManager: Adding {} failed: {}", id, res);
              fut.handle(new Failure<>(res.getType(), res.cause()));
            } else {
              insert2(t, id, fut);
            }
          });
        }
      } else {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      }
    });
  }

  public void updateDescriptor(TenantDescriptor td,
    Handler<ExtendedAsyncResult<Void>> fut) {
    final String id = td.getId();
    tenants.get(id, gres -> {
      if (gres.failed() && gres.getType() != ErrorType.NOT_FOUND) {
        logger.warn("TenantManager: updateDescriptor: getting {} failed: {}", id, gres);
        fut.handle(new Failure<>(ErrorType.INTERNAL, ""));
      }
      Tenant t;
      if (gres.succeeded()) {
        t = new Tenant(td, gres.result().getEnabled());
      } else {
        t = new Tenant(td);
      }
      if (tenantStore == null) {
        tenants.add(id, t, fut); // no database. handles success directly
      } else {
        tenantStore.updateDescriptor(td, upres -> {
          if (upres.failed()) {
            logger.warn("TenantManager: Updating database for {} failed: {}", id, upres);
            fut.handle(new Failure<>(ErrorType.INTERNAL, ""));
          } else {
            tenants.add(id, t, fut); // handles success
          }
        });
      }
    });
  }

  public void list(Handler<ExtendedAsyncResult<List<TenantDescriptor>>> fut) {
    tenants.getKeys(lres -> {
      if (lres.failed()) {
        logger.warn("TenantManager list: Getting keys failed: {}", lres);
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
   * @param id
   * @param fut
   */
  public void get(String id, Handler<ExtendedAsyncResult<Tenant>> fut) {
    tenants.get(id, fut);
  }

  /**
   * Delete a tenant.
   *
   * @param id
   * @param fut callback with a boolean, true if actually deleted, false if not
   * there.
   */
  public void delete(String id, Handler<ExtendedAsyncResult<Boolean>> fut) {
    if (tenantStore == null) { // no db, just do it
      tenants.remove(id, fut);
    } else {
      tenantStore.delete(id, dres -> {
        if (dres.failed() && dres.getType() != ErrorType.NOT_FOUND) {
          logger.warn("TenantManager: Deleting {} failed: {}", id, dres);
          fut.handle(new Failure<>(ErrorType.INTERNAL, dres.cause()));
        } else {
          tenants.remove(id, fut);
        }
      });
    }
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

  private void updateModuleCommit(Tenant t,
    String moduleFrom, String moduleTo,
    Handler<ExtendedAsyncResult<Void>> fut) {
    String id = t.getId();
    if (moduleFrom != null) {
      t.disableModule(moduleFrom);
    }
    if (moduleTo != null) {
      t.enableModule(moduleTo);
    }
    if (tenantStore == null) {
      updateModuleCommit2(id, t, fut);
    } else {
      tenantStore.updateModules(id, t.getEnabled(), ures -> {
        if (ures.failed()) {
          fut.handle(new Failure<>(ures.getType(), ures.cause()));
        } else {
          updateModuleCommit2(id, t, fut);
        }
      });
    }
  }

  private void updateModuleCommit2(String id, Tenant t, Handler<ExtendedAsyncResult<Void>> fut) {
    tenants.put(id, t, pres -> {
      if (pres.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, pres.cause()));
      } else {
        fut.handle(new Success<>());
      }
    });
  }

  /**
   * Enable a module for a tenant and disable another. Checks dependencies,
   * invokes the tenant interface, and the tenantPermissions interface, and
   * finally marks the modules as enabled and disabled.
   *
   * @param tenantId - id of the the tenant in question
   * @param moduleFrom id of the module to be disabled, or null
   * @param moduleTo id of the module to be enabled, or null
   * @param pc proxyContext for proper logging, etc
   * @param fut callback with success, or various errors
   *
   * To avoid too much callback hell, this has been split into several helpers.
   */
  public void enableAndDisableModule(String tenantId,
    String moduleFrom, TenantModuleDescriptor td, ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {

    tenants.get(tenantId, tres -> {
      if (tres.failed()) {
        fut.handle(new Failure<>(tres.getType(), tres.cause()));
      } else {
        Tenant tenant = tres.result();
        enableAndDisableModule(tenant, moduleFrom, td, pc, fut);
      }
    });
  }

  private void enableAndDisableModule(Tenant tenant,
    String moduleFrom, TenantModuleDescriptor td, ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {

    if (td == null) {
      moduleManager.get(moduleFrom, resFrom -> {
        if (resFrom.failed()) {
          fut.handle(new Failure<>(resFrom.getType(), resFrom.cause()));
        } else {
          ModuleDescriptor mdFrom = resFrom.result();
          enableAndDisableModule2(tenant, null, mdFrom, null, pc, fut);
        }
      });
    } else {
      moduleManager.getLatest(td.getId(), resTo -> {
        if (resTo.failed()) {
          fut.handle(new Failure<>(resTo.getType(), resTo.cause()));
        } else {
          ModuleDescriptor mdTo = resTo.result();
          moduleManager.get(moduleFrom, resFrom -> {
            if (resFrom.failed()) {
              fut.handle(new Failure<>(resFrom.getType(), resFrom.cause()));
            } else {
              ModuleDescriptor mdFrom = resFrom.result();
              enableAndDisableModule2(tenant, null, mdFrom, mdTo, pc, fut);
            }
          });
        }
      });
    }
  }

  private void enableAndDisableModule2(Tenant tenant, String tenantParameters,
    ModuleDescriptor mdFrom, ModuleDescriptor mdTo, ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {

    moduleManager.enableAndDisableCheck(tenant, mdFrom, mdTo, cres -> {
      if (cres.failed()) {
        pc.debug("enableAndDisableModule: depcheck fail: " + cres.cause().getMessage());
        fut.handle(new Failure<>(cres.getType(), cres.cause()));
      } else {
        pc.debug("enableAndDisableModule: depcheck ok");
        ead1TenantInterface(tenant, tenantParameters, mdFrom, mdTo, false, pc, res -> {
          if (res.failed()) {
            fut.handle(new Failure<>(res.getType(), res.cause()));
          } else {
            fut.handle(new Success<>(mdTo != null ? mdTo.getId() : ""));
          }
        });
      }
    });
  }

  /**
   * enableAndDisable helper 1: call the tenant interface.
   *
   * @param tenant
   * @param mdFrom
   * @param mdTo
   * @param fut
   */
  private void ead1TenantInterface(Tenant tenant, String tenantParameters,
    ModuleDescriptor mdFrom, ModuleDescriptor mdTo, boolean purge,
    ProxyContext pc, Handler<ExtendedAsyncResult<Void>> fut) {

    JsonObject jo = new JsonObject();
    if (mdTo != null) {
      jo.put("module_to", mdTo.getId());
    }
    if (mdFrom != null) {
      jo.put("module_from", mdFrom.getId());
    }
    getTenantInterface(mdFrom, mdTo, jo, tenantParameters, purge, ires -> {
      if (ires.failed()) {
        if (ires.getType() == ErrorType.NOT_FOUND) {
          logger.debug("eadTenantInterface: "
            + (mdTo != null ? mdTo.getId() : mdFrom.getId())
            + " has no support for tenant init");
          ead2TenantInterface(tenant, mdFrom, mdTo, pc, fut);
        } else {
          fut.handle(new Failure<>(ires.getType(), ires.cause()));
        }
      } else {
        ModuleInstance tenInst = ires.result();
        final String req = purge ? "" : jo.encodePrettily();
        proxyService.callSystemInterface(tenant, tenInst, req, pc, cres -> {
          if (cres.failed()) {
            fut.handle(new Failure<>(cres.getType(), cres.cause()));
          } else {
            pc.passOkapiTraceHeaders(cres.result());
            // We can ignore the result, the call went well.
            ead2TenantInterface(tenant, mdFrom, mdTo, pc, fut);
          }
        });
      }
    });
  }

  private void ead2TenantInterface(Tenant tenant,
    ModuleDescriptor mdFrom, ModuleDescriptor mdTo, ProxyContext pc,
    Handler<ExtendedAsyncResult<Void>> fut) {
    if (mdTo == null) {
      ead5commit(tenant, mdFrom.getId(), null, pc, fut);
    } else {
      ead2PermMod(tenant, mdFrom, mdTo, pc, fut);
    }
  }

  /**
   * enableAndDisable helper 2: Choose which permission module to invoke.
   *
   * @param tenant
   * @param mdFrom
   * @param mdTo
   * @param pc
   * @param fut
   */
  private void ead2PermMod(Tenant tenant,
    ModuleDescriptor mdFrom, ModuleDescriptor mdTo, ProxyContext pc,
    Handler<ExtendedAsyncResult<Void>> fut) {
    String moduleFrom = mdFrom != null ? mdFrom.getId() : null;
    String moduleTo = mdTo.getId();
    findSystemInterface(tenant, res -> {
      if (res.failed()) {
        if (res.getType() == ErrorType.NOT_FOUND) { // no perms interface.
          if (mdTo.getSystemInterface("_tenantPermissions") != null) {
            pc.debug("ead2PermMod: Here we reload perms of all enabled modules");
            Set<String> listModules = tenant.listModules();
            pc.debug("Got a list of already-enabled moduled: " + Json.encode(listModules));
            Iterator<String> modit = listModules.iterator();
            ead3RealoadPerms(tenant, modit, moduleFrom, mdTo, mdTo, pc, fut);
            return;
          }
          pc.debug("enablePermissions: No tenantPermissions interface found. "
            + "Carrying on without it.");
          ead5commit(tenant, moduleFrom, moduleTo, pc, fut);
        } else {
          pc.responseError(res.getType(), res.cause());
        }
      } else {
        ModuleDescriptor permsMod = res.result();
        if (mdTo.getSystemInterface("_tenantPermissions") != null) {
          pc.debug("Using the tenantPermissions of this module itself");
          permsMod = mdTo;
        }
        ead4Permissions(tenant, moduleFrom, mdTo, permsMod, pc, fut);
      }
    });
  }

  /**
   * enableAndDisable helper 3: Reload permissions. When we enable a module that
   * provides the tenantPermissions interface, we may have other modules already
   * enabled, who have not got their permissions pushed. Now that we have a
   * place to push those permissions to, we do it recursively for all enabled
   * modules.
   *
   * @param tenant
   * @param moduleFrom
   * @param mdTo
   * @param permsModule
   * @param pc
   * @param fut
   */
  private void ead3RealoadPerms(Tenant tenant, Iterator<String> modit,
    String moduleFrom, ModuleDescriptor mdTo, ModuleDescriptor permsModule,
    ProxyContext pc, Handler<ExtendedAsyncResult<Void>> fut) {
    if (!modit.hasNext()) {
      pc.debug("ead3RealoadPerms: No more modules to reload");
      ead4Permissions(tenant, moduleFrom, mdTo, permsModule, pc, fut);
      return;
    }
    String mdid = modit.next();
    moduleManager.get(mdid, res -> {
      if (res.failed()) { // not likely to happen
        pc.responseError(res.getType(), res.cause());
        return;
      }
      ModuleDescriptor md = res.result();
      pc.debug("ead3RealoadPerms: Should reload perms for " + md.getName());
      tenantPerms(tenant, md, permsModule, pc, pres -> {
        if (pres.failed()) { // not likely to happen
          pc.responseError(res.getType(), res.cause());
          return;
        }
        ead3RealoadPerms(tenant, modit, moduleFrom, mdTo, permsModule, pc, fut);
      });
    });
  }

  /**
   * enableAndDisable helper 4: Make the tenantPermissions call. For the module
   * itself.
   *
   * @param tenant
   * @param moduleFrom
   * @param module_to
   * @param mdTo
   * @param permsModule
   * @param pc
   * @param fut
   */
  private void ead4Permissions(Tenant tenant, String moduleFrom,
    ModuleDescriptor mdTo, ModuleDescriptor permsModule,
    ProxyContext pc, Handler<ExtendedAsyncResult<Void>> fut) {

    pc.debug("ead4Permissions: Perms interface found in "
      + permsModule.getName());

    tenantPerms(tenant, mdTo, permsModule, pc, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      String moduleTo = mdTo.getId();
      ead5commit(tenant, moduleFrom, moduleTo, pc, fut);
    });
  }

  /**
   * enableAndDisable helper 5: Commit the change in modules.
   *
   * @param tenant
   * @param moduleFrom
   * @param moduleTo
   * @param pc
   * @param fut
   */
  private void ead5commit(Tenant tenant,
    String moduleFrom, String moduleTo, ProxyContext pc,
    Handler<ExtendedAsyncResult<Void>> fut) {

    pc.debug("ead5commit: " + moduleFrom + " " + moduleTo);
    updateModuleCommit(tenant, moduleFrom, moduleTo, ures -> {
      if (ures.failed()) {
        pc.responseError(ures.getType(), ures.cause());
      } else {
        if (moduleTo != null) {
          EventBus eb = vertx.eventBus();
          eb.publish(eventName, tenant.getId());
        }
        pc.debug("ead5commit done");
        fut.handle(new Success<>());
      }
    });
  }

  public void startTimers(Promise<Void> promise) {
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

  private void consumeTimers() {
    EventBus eb = vertx.eventBus();
    eb.consumer(eventName, res -> {
      String tenantId = (String) res.body();
      handleTimer(tenantId);
    });
  }

  private void handleTimer(String tenantId) {
    handleTimer(tenantId, null, 0, null);
  }

  private void stopTimer(String tenantId, String moduleId, int seq ,Lock lockP) {
    if (lockP != null) {
      logger.info("remove timer for module {} for tenant {}", moduleId, tenantId);
      final String key = tenantId + "_" + moduleId + "_" + seq;
      timers.remove(key);
      lockP.release();
    }
  }

  private void handleTimer(String tenantId, String moduleId, int seq1, Lock lockP) {
    logger.info("handleTimer tenant {} module {} seq1 {}", tenantId, moduleId, seq1);
    tenants.get(tenantId, tRes -> {
      if (tRes.failed()) {
        // tenant no longer exist
        stopTimer(tenantId, moduleId, seq1, lockP);
        return;
      }
      Tenant tenant = tRes.result();
      moduleManager.getEnabledModules(tenant, mRes -> {
        if (mRes.failed()) {
          logger.warn("handleTimer getEnabledModules failed: {}", mRes.cause());
          stopTimer(tenantId, moduleId, seq1, lockP);
          return;
        }
        List<ModuleDescriptor> mdList = mRes.result();
        try {
          handleTimer(tenant, mdList, moduleId, seq1, lockP);
        } catch (Exception ex) {
          logger.warn("handleTimer execption {}", ex.getMessage());
        }
      });
    });
  }

  private void handleTimer(Tenant tenant, List<ModuleDescriptor> mdList, String moduleId, int seq1, Lock lockP) {
    int noTimers = 0;
    final String tenantId = tenant.getId();
    for (ModuleDescriptor md : mdList) {
      if (moduleId == null || moduleId.equals(md.getId())) {
        InterfaceDescriptor timerInt = md.getSystemInterface("_timer");
        if (timerInt != null) {
          List<RoutingEntry> routingEntries = timerInt.getAllRoutingEntries();
          noTimers += handleTimer(tenant, md, routingEntries, seq1, lockP);
        }
      }
    }
    if (noTimers == 0) {
      // module no longer enabled for tenant
      stopTimer(tenantId, moduleId, seq1, lockP);
    }
    logger.info("handleTimer done no {}", noTimers);
  }

  private int handleTimer(Tenant tenant, ModuleDescriptor md, List<RoutingEntry> routingEntries, int seq1, Lock lockP) {
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
            lockTimer(tenantId, md, key, delay, seq);
          }
        } else if (seq == seq1) {
          fireTimer(tenant, md, re, path, lockP);
          lockTimer(tenantId, md, key, delay, seq);
          return 1;
        }
      }
    }
    return 0;
  }

  private void lockTimer(String tenantId, ModuleDescriptor md, String key, long delay, int seq) {
    logger.info("wait for lock {}", key);
    asyncLock.getLock(key, lockRes -> {
      if (lockRes.succeeded()) {
        logger.info("wait for lock {} returned", key);
        Lock lock = lockRes.result();
        logger.info("setTimer delay {}", delay);
        vertx.setTimer(delay, res4
          -> vertx.runOnContext(res5
            -> handleTimer(tenantId, md.getId(), seq, lock)));
      } else {
        logger.info("wait for lock {} returned failure ", key, lockRes.cause());
      }
    });
  }

  private void fireTimer(Tenant tenant, ModuleDescriptor md, RoutingEntry re, String path, Lock lock) {
    String tenantId = tenant.getId();
    HttpMethod httpMethod = HttpMethod.POST;
    String[] methods = re.getMethods();
    if (methods != null && re.getMethods().length >= 1) {
      httpMethod = HttpMethod.valueOf(methods[0]);
    }
    ModuleInstance inst = new ModuleInstance(md, re, path, httpMethod, true);
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    logger.info("timer call start module {} for tenant {}", md.getId(), tenantId);
    proxyService.callSystemInterface("supertenant", headers, tenant, inst, "", cRes -> {
      lock.release();
      if (cRes.succeeded()) {
        logger.info("timer call succeeded to module {} for tenant {}",
          md.getId(), tenantId);
      } else {
        logger.info("timer call failed to module {} for tenant {} : {}",
          md.getId(), tenantId, cRes.cause().getMessage());
      }
    });
  }

  /**
   * Helper to make the tenantPermissions call for one module. Used from
   * ead3RealoadPerms and ead4Permissions.
   */
  private void tenantPerms(Tenant tenant, ModuleDescriptor mdTo,
    ModuleDescriptor permsModule, ProxyContext pc,
    Handler<ExtendedAsyncResult<Void>> fut) {

    pc.debug("Loading permissions for " + mdTo.getName()
      + " (using " + permsModule.getName() + ")");
    String moduleTo = mdTo.getId();
    PermissionList pl = new PermissionList(moduleTo, mdTo.getPermissionSets());
    String pljson = Json.encodePrettily(pl);
    pc.debug("tenantPerms Req: " + pljson);
    InterfaceDescriptor permInt = permsModule.getSystemInterface("_tenantPermissions");
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
          fut.handle(new Failure<>(cres.getType(), cres.cause()));
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
   * @param module
   * @param fut callback with the getPath to the interface, "" if no interface,
   * or a failure
   *
   */
  private void getTenantInterface(ModuleDescriptor mdFrom,
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
              fut.handle(new Success<>(new ModuleInstance(md, null, "/_/tenant", HttpMethod.POST, true).withRetry()));
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
            fut.handle(new Success<>(new ModuleInstance(md, re, pattern, HttpMethod.POST, true).withRetry()));
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

  public void listInterfaces(String tenantId, boolean full, String interfaceType,
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

  public void listModulesFromInterface(String tenantId,
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

  public void installUpgradeModules(String tenantId, ProxyContext pc,
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
      for (String fId : modsEnabled.keySet()) {
        ModuleId moduleId = new ModuleId(fId);
        String uId = moduleId.getLatest(modsAvailable.keySet());
        if (!uId.equals(fId)) {
          TenantModuleDescriptor tmd = new TenantModuleDescriptor();
          tmd.setAction(Action.enable);
          tmd.setId(uId);
          logger.info("upgrade.. enable {}", uId);
          tmd.setFrom(fId);
          tml2.add(tmd);
        }
      }
      return tml2;
    } else {
      return tml;
    }
  }

  private void installUpgradeModules2(Tenant t, ProxyContext pc,
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
        installCommit1(t, pc, options, modsAvailable, tml, tml.iterator(),
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
  private void installCommit1(Tenant t, ProxyContext pc,
    TenantInstallOptions options,
    Map<String, ModuleDescriptor> modsAvailable,
    List<TenantModuleDescriptor> tml,
    Iterator<TenantModuleDescriptor> it,
    Handler<ExtendedAsyncResult<Void>> fut) {

    if (it.hasNext() && options.getDeploy()) {
      TenantModuleDescriptor tm = it.next();
      if (tm.getAction() == Action.enable || tm.getAction() == Action.uptodate) {
        ModuleDescriptor md = modsAvailable.get(tm.getId());
        proxyService.autoDeploy(md, res -> {
          if (res.failed()) {
            fut.handle(new Failure<>(res.getType(), res.cause()));
          } else {
            installCommit1(t, pc, options, modsAvailable, tml, it, fut);
          }
        });
      } else {
        installCommit1(t, pc, options, modsAvailable, tml, it, fut);
      }
    } else {
      installCommit2(t, pc, options, modsAvailable, tml, tml.iterator(), fut);
    }
  }

  /* phase 2 enable modules for tenant */
  private void installCommit2(Tenant tenant, ProxyContext pc,
    TenantInstallOptions options,
    Map<String, ModuleDescriptor> modsAvailable,
    List<TenantModuleDescriptor> tml,
    Iterator<TenantModuleDescriptor> it,
    Handler<ExtendedAsyncResult<Void>> fut) {
    if (it.hasNext()) {
      TenantModuleDescriptor tm = it.next();
      ModuleDescriptor mdFrom = null;
      ModuleDescriptor mdTo = null;
      boolean purge = false;
      if (tm.getAction() == Action.enable) {
        if (tm.getFrom() != null) {
          mdFrom = modsAvailable.get(tm.getFrom());
        }
        mdTo = modsAvailable.get(tm.getId());
      } else if (tm.getAction() == Action.disable) {
        mdFrom = modsAvailable.get(tm.getId());
        if (options.getPurge()) {
          purge = true;
        }
      }
      if (mdFrom == null && mdTo == null) {
        installCommit2(tenant, pc, options, modsAvailable, tml, it, fut);
      } else {
        ead1TenantInterface(tenant, options.getTenantParameters(), mdFrom, mdTo, purge, pc, res -> {
          if (res.failed()) {
            fut.handle(new Failure<>(res.getType(), res.cause()));
          } else {
            installCommit2(tenant, pc, options, modsAvailable, tml, it, fut);
          }
        });
      }
    } else {
      installCommit3(tenant, options, modsAvailable, tml, tml.iterator(), fut);
    }
  }

  /* phase 3 undeploy if no longer needed */
  private void installCommit3(Tenant tenant,
    TenantInstallOptions options,
    Map<String, ModuleDescriptor> modsAvailable,
    List<TenantModuleDescriptor> tml,
    Iterator<TenantModuleDescriptor> it,
    Handler<ExtendedAsyncResult<Void>> fut) {

    if (it.hasNext() && options.getDeploy()) {
      TenantModuleDescriptor tm = it.next();
      ModuleDescriptor md = null;
      if (tm.getAction() == Action.enable) {
        md = modsAvailable.get(tm.getFrom());
      }
      if (tm.getAction() == Action.disable) {
        md = modsAvailable.get(tm.getId());
      }
      if (md != null) {
        final ModuleDescriptor mdF = md;
        getModuleUser(md.getId(), ures -> {
          if (ures.failed()) {
            // in use or other error, so skip
            installCommit3(tenant, options, modsAvailable, tml, it, fut);
          } else {
            // success means : not in use, so we can undeploy it
            logger.info("autoUndeploy mdF {}", mdF.getId());
            proxyService.autoUndeploy(mdF, res -> {
              if (res.failed()) {
                fut.handle(new Failure<>(res.getType(), res.cause()));
              } else {
                installCommit3(tenant, options, modsAvailable, tml, it, fut);
              }
            });
          }
        });
      } else {
        installCommit3(tenant, options, modsAvailable, tml, it, fut);
      }
    } else {
      fut.handle(new Success<>());
    }
  }

  public void listModules(String id,
    Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {

    tenants.get(id, gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        Tenant t = gres.result();
        List<ModuleDescriptor> tl = new LinkedList<>();
        CompList<List<ModuleDescriptor>> futures = new CompList<>(ErrorType.INTERNAL);
        for (String mId : t.listModules()) {
          Promise<ModuleDescriptor> promise = Promise.promise();
          moduleManager.get(mId, res -> {
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

  private void getModuleUserR(String mod, Iterator<String> it, Handler<ExtendedAsyncResult<Void>> fut) {
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
   * Load tenants from the store into the shared memory map
   *
   * @param fut
   */
  private void loadTenants(Handler<ExtendedAsyncResult<Void>> fut) {
    tenants.getKeys(gres -> {
      if (gres.failed()) {
        fut.handle(new Failure<>(gres.getType(), gres.cause()));
      } else {
        Collection<String> keys = gres.result();
        if (!keys.isEmpty()) {
          logger.info("Not loading tenants, looks like someone already did");
          fut.handle(new Success<>());
        } else if (tenantStore == null) {
          logger.info("No storage to load tenants from, so starting with empty");
          fut.handle(new Success<>());
        } else {
          loadTenants2(fut);
        }
      }
    });
  }

  private void loadTenants2(Handler<ExtendedAsyncResult<Void>> fut) {
    tenantStore.listTenants(lres -> {
      if (lres.failed()) {
        fut.handle(new Failure<>(lres.getType(), lres.cause()));
      } else {
        CompList<List<Void>> futures = new CompList<>(ErrorType.INTERNAL);
        for (Tenant t : lres.result()) {
          Promise<Void> f = Promise.promise();
          tenants.add(t.getId(), t, f::handle);
          futures.add(f);
        }
        futures.all(fut);
      }
    });
  }

} // class
