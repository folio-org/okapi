package org.folio.okapi.web;

import io.vertx.core.Handler;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.service.TenantManager;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.OkapiClient;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.discovery.DiscoveryManager;
import org.folio.okapi.util.ProxyContext;

public class TenantWebService {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  final private Vertx vertx;
  TenantManager tenants;
  private final DiscoveryManager discoveryManager;


  public TenantWebService(Vertx vertx, TenantManager tenantManager,
                          DiscoveryManager discoveryManager ) {
    this.vertx = vertx;
    this.tenants = tenantManager;
    this.discoveryManager = discoveryManager;
  }

   public void init(Vertx vertx, Handler<ExtendedAsyncResult<Void>> fut) {
     tenants.init(vertx, fut);
  }

  public void create(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.create");
    try {
      final TenantDescriptor td = Json.decodeValue(ctx.getBodyAsString(),
              TenantDescriptor.class);
      if (td.getId() == null || td.getId().isEmpty()) {
        td.setId(UUID.randomUUID().toString());
      }
      final String id = td.getId();
      if (!id.matches("^[a-z0-9._-]+$")) {
        pc.responseError(400, "Invalid id");
      } else {
        Tenant t = new Tenant(td);
        tenants.insert(t, res -> {
          if (res.failed()) {
            pc.responseError(res.getType(), res.cause());
            return;
          }
          final String uri = ctx.request().uri() + "/" + id;
          final String s = Json.encodePrettily(t.getDescriptor());
          pc.responseJson(201, s, uri);
        });
      }
    } catch (DecodeException ex) {
      pc.responseError(400, ex);
    }
  }

  public void update(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.update");
    try {
      final TenantDescriptor td = Json.decodeValue(ctx.getBodyAsString(),
              TenantDescriptor.class);
      final String id = ctx.request().getParam("id");
      if (!id.equals(td.getId())) {
        pc.responseError(400, "Tenant.id=" + td.getId() + " id=" + id);
        return;
      }
      Tenant t = new Tenant(td);
      tenants.updateDescriptor(td, res -> {
        if (res.succeeded()) {
          final String s = Json.encodePrettily(t.getDescriptor());
          pc.responseJson(200, s);
        } else {
          pc.responseError(404, res.cause());
        }
      });
    } catch (DecodeException ex) {
      pc.responseError(400, ex);
    }
  }

  public void list(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.list");
    tenants.list(res -> {
      if (res.succeeded()) {
        List<TenantDescriptor> tdl = res.result();
        String s = Json.encodePrettily(tdl);
        pc.responseJson(200, s);
      } else {
        pc.responseError(400, res.cause());
      }
    });
  }

  public void get(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.get");
    final String id = ctx.request().getParam("id");
    tenants.get(id, res -> {
      if (res.succeeded()) {
        Tenant t = res.result();
        String s = Json.encodePrettily(t.getDescriptor());
        pc.responseJson(200, s);
      } else {
        pc.responseError(res.getType(), res.cause());
      }
    });
  }

  public void delete(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.delete");
    final String id = ctx.request().getParam("id");
    tenants.delete(id, res -> {
      if (res.succeeded()) {
        pc.responseText(204, "");
      } else {
        pc.responseError(res.getType(), res.cause());
      }
    });
  }

  public void disableModule(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.disable");
    try {
      final String id = ctx.request().getParam("id");
      final String module = ctx.request().getParam("mod");
      pc.debug("disablemodule t=" + id + " m=" + module);
      tenants.enableAndDisableModule(id, module, null, pc, res -> {
      if (res.failed()) {
        pc.responseError(res.getType(), res.cause());
      } else {
        pc.responseText(204, "");
      }
    });
    } catch (DecodeException ex) {
      pc.responseError(400, ex);
    }
  }

  public void enableModule(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.enablemodule");
    try {
      final String id = ctx.request().getParam("id");
      final TenantModuleDescriptor td = Json.decodeValue(ctx.getBodyAsString(),
        TenantModuleDescriptor.class);
      final String module_to = td.getId();
      tenants.enableAndDisableModule(id, null, module_to, pc, eres -> {
        if (eres.failed()) {
          pc.responseError(eres.getType(), eres.cause());
          return;
        }
        final String uri = ctx.request().uri() + "/" + module_to;
        pc.responseJson(201, Json.encodePrettily(td), uri);
      });

    } catch (DecodeException ex) {
      pc.responseError(400, ex);
    }
  }

  public void updateModule(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.updatemodule");
    //enableTenantInt(pc, module_from);
    try {
      final String id = ctx.request().getParam("id");
      final String module_from = ctx.request().getParam("mod");
      final TenantModuleDescriptor td = Json.decodeValue(ctx.getBodyAsString(),
        TenantModuleDescriptor.class);
      final String module_to = td.getId();
      tenants.enableAndDisableModule(id, module_from, module_to, pc, eres -> {
        if (eres.failed()) {
          pc.responseError(eres.getType(), eres.cause());
          return;
        }
        final String uri = ctx.request().uri() + "/" + module_to + "/XXXX";
        pc.responseJson(201, Json.encodePrettily(td), uri);
      });

    } catch (DecodeException ex) {
      pc.responseError(400, ex);
    }
  }

  /**
   * Helper to make a DELETE request to the module's tenant interface.
   * Sets up
   * the response in ctx. NOTE - This is not used at the moment. It used to be
   * called from disableModule, but that was too drastic. We will need a way to
   * invoke this, in some future version.
   *
   * @param ctx
   * @param module
   */
  private void destroyTenant(RoutingContext ctx, String module, String id) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.destroy");
    tenants.getTenantInterface(module, ires -> {
      if (ires.failed()) {
        if (ires.getType() == NOT_FOUND) {
          pc.debug("enableModule: " + module + " has no support for tenant init");
          pc.responseText(204, "");
          return;
        } else {
          pc.responseError(ires.getType(), ires.cause());
          return;
        }
      }
      // We have a tenant interface, invoke DELETE on it
      String tenInt = ires.result();
      discoveryManager.get(module, gres -> {
        if (gres.failed()) {
          pc.responseError(gres.getType(), gres.cause());
        } else {
          List<DeploymentDescriptor> instances = gres.result();
          if (instances.isEmpty()) {
            pc.responseError(400, "No running instances for module " + module
              + ". Can not invoke tenant destroy");
          } else { // TODO - Don't just take the first. Pick one by random.
            String baseurl = instances.get(0).getUrl();
            pc.debug("disableModule Url: " + baseurl + " and " + tenInt);
            Map<String, String> headers = new HashMap<>();
            for (String hdr : ctx.request().headers().names()) {
              if (hdr.matches("^X-.*$")) {
                headers.put(hdr, ctx.request().headers().get(hdr));
              }
            }
            if (!headers.containsKey(XOkapiHeaders.TENANT)) {
              headers.put(XOkapiHeaders.TENANT, id);
              pc.debug("Added " + XOkapiHeaders.TENANT + " : " + id);
            }
            headers.put("Accept", "*/*");
            //headers.put("Content-Type", "application/json; charset=UTF-8");
            String body = ""; // dummy
            OkapiClient cli = new OkapiClient(baseurl, vertx, headers);
            cli.request(HttpMethod.DELETE, tenInt, body, cres -> {
              if (cres.failed()) {
                pc.warn("Tenant destroy request for " + module
                  + " failed with " + cres.cause().getMessage());
                pc.responseError(500, "DELETE to " + tenInt
                  + " on " + module + " failed with "
                  + cres.cause().getMessage());
              } else { // All well, we can finally enable it
                pc.debug("disableModule: destroy request to " + module + " succeeded");
                pc.responseText(204, "");  // finally we are done
              }
            }); // cli.request
          }
        } // got module
      });
    }); // tenantInterface
  } //destroyTenant


  public void listModules(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.listmodules");
    final String id = ctx.request().getParam("id");
    tenants.listModules(id, res -> {
      if (res.failed()) {
        pc.responseError(res.getType(), res.cause());
      } else {
        List<String> ml = res.result();
        Iterator<String> mli = ml.iterator();  // into a list of objects
        ArrayList<TenantModuleDescriptor> ta = new ArrayList<>();
        while (mli.hasNext()) {
          TenantModuleDescriptor tmd = new TenantModuleDescriptor();
          tmd.setId(mli.next());
          ta.add(tmd);
        }
        String s = Json.encodePrettily(ta);
        pc.responseJson(200, s);
      }
    });
  }

  public void getModule(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.getmodule");
    final String id = ctx.request().getParam("id");
    final String mod = ctx.request().getParam("mod");
    tenants.get(id, res -> {
      if (res.succeeded()) {
        Tenant t = res.result();
        Set<String> ml = t.listModules();  // Convert the list of module names
        if (ml.contains(mod)) {
          TenantModuleDescriptor tmd = new TenantModuleDescriptor();
          tmd.setId(mod);
          String s = Json.encodePrettily(tmd);
          pc.responseJson(200, s);
        } else {
          pc.responseError(404, mod);
        }
      } else {
        pc.responseError(res.getType(), res.cause());
      }
    });
  }

  public void listModulesFromInterface(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.tenants.listmodulesfrominterface");
    final String intId = ctx.request().getParam("int");
    final String id = ctx.request().getParam("id");
    tenants.listModulesFromInterface(id, intId, lres -> {
      if (lres.failed()) {
        pc.responseError(lres.getType(), lres.cause());
        return;
      }
      List<ModuleDescriptor> mdL = lres.result();
      ArrayList<TenantModuleDescriptor> ta = new ArrayList<>();
      for (ModuleDescriptor md : mdL) {
        TenantModuleDescriptor tmd = new TenantModuleDescriptor();
        tmd.setId(md.getId());
        ta.add(tmd);
      }
      String s = Json.encodePrettily(ta);
      pc.responseJson(200, s);
    });
  }

} // class
