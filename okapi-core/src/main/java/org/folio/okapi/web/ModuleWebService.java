package org.folio.okapi.web;

import org.folio.okapi.bean.ModuleDescriptor;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.folio.okapi.bean.ModuleDescriptorBrief;
import org.folio.okapi.service.ModuleManager;
import org.folio.okapi.util.ProxyContext;

/**
 * Services related to adding and deleting modules. All operations try to do the
 * thing on the locally running system first. If that succeeds, they update the
 * database, and tell other instances to reload the configuration.
 */
public class ModuleWebService {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  ModuleManager moduleManager;

  public ModuleWebService(Vertx vertx, ModuleManager moduleService) {
    this.moduleManager = moduleService;
  }

  public void create(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.modules.create");
    try {
      final ModuleDescriptor md = Json.decodeValue(ctx.getBodyAsString(),
              ModuleDescriptor.class);
      if (md.getId() == null || md.getId().isEmpty()) {
        md.setId(UUID.randomUUID().toString());
      }
      String validerr = md.validate(pc);
      if (!validerr.isEmpty()) {
        pc.responseError(400, validerr);
      } else {
        moduleManager.create(md, cres -> {
          if (cres.failed()) {
            pc.responseError(cres.getType(), cres.cause());
            return;
          }
          final String s = Json.encodePrettily(md);
          final String uri = ctx.request().uri() + "/" + md.getId();
          pc.responseJson(201, s, uri);
        });
      }
    } catch (DecodeException ex) {
      pc.debug("Failed to decode md: " + ctx.getBodyAsString());
      pc.responseError(400, ex);
    }
  }

  public void update(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.modules.update");
    try {
      final ModuleDescriptor md = Json.decodeValue(ctx.getBodyAsString(),
              ModuleDescriptor.class);
      final String id = ctx.request().getParam("id");
      if (!id.equals(md.getId())) {
        pc.responseError(400, "Module.id=" + md.getId() + " id=" + id);
        return;
      }
      String validerr = md.validate(pc);
      if (!validerr.isEmpty()) {
        pc.responseError(400, validerr);
      } else {
        moduleManager.update(md, cres -> {
          if (cres.failed()) {
            pc.responseError(cres.getType(), cres.cause());
            return;
          }
          final String s = Json.encodePrettily(md);
          pc.responseJson(200, s);
        });
      }
    } catch (DecodeException ex) {
      pc.responseError(400, ex);
    }
  }

  public void get(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.modules.get");
    final String id = ctx.request().getParam("id");
    final String q = "{ \"id\": \"" + id + "\"}";
    JsonObject jq = new JsonObject(q);
    //cli.find(collection, jq, res -> {
    moduleManager.get(id, res -> {
      if (res.succeeded()) {
        pc.responseJson(200, Json.encodePrettily(res.result()));
      } else {
        pc.responseError(res.getType(), res.cause());
      }
    });
  }

  public void list(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.modules.list");
    moduleManager.getAllModules(res -> {
      if (res.succeeded()) {
        List<ModuleDescriptorBrief> ml = new ArrayList<>(res.result().size());
        for (ModuleDescriptor md : res.result()) {
          ml.add(new ModuleDescriptorBrief(md));
        }
        pc.responseJson(200, Json.encodePrettily(ml));
      } else {
        pc.responseError(res.getType(), res.cause());
      }
    });
  }

  /**
   * Delete a module.
   *
   * @param ctx
   */
  public void delete(RoutingContext ctx) {
    ProxyContext pc = new ProxyContext(ctx, "okapi.modules.delete");
    final String id = ctx.request().getParam("id");
    moduleManager.delete(id, sres -> {
      if (sres.failed()) {
        pc.error("delete (runtime) failed: " + sres.getType()
          + ":" + sres.cause().getMessage());
        pc.responseError(sres.getType(), sres.cause());
        return;
      }
      pc.responseText(204, "");
    });
  }

} // class
