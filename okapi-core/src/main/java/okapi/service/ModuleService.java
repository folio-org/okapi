/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.service;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import okapi.bean.ModuleDescriptor;
import okapi.bean.ModuleInstance;
import okapi.bean.Modules;
import okapi.bean.Ports;
import okapi.bean.ProcessModuleHandle;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import java.util.Set;
import okapi.util.Failure;
import okapi.util.Success;

public class ModuleService {
  private Modules modules;
  private Ports ports;

  final private Vertx vertx;

  public ModuleService(Vertx vertx, Modules modules, int port_start, int port_end) {
    this.vertx = vertx;
    this.ports = new Ports(port_start, port_end);
    this.modules = modules;
  }

  public void create(RoutingContext ctx) {
    try {
      final ModuleDescriptor md = Json.decodeValue(ctx.getBodyAsString(),
              ModuleDescriptor.class);

      final String id = md.getId();
      String url;
      final int use_port = ports.get();
      int spawn_port = -1;
      ModuleInstance m = modules.get(id);
      if (m != null) {
        ctx.response().setStatusCode(400).end("module " + id
                + " already deployed");
        return;
      }
      if (md.getUrl() == null) {
        if (use_port == -1) {
          ctx.response().setStatusCode(400).end("module " + id
                  + " can not be deployed: all ports in use");
        }
        spawn_port = use_port;
        url = "http://localhost:" + use_port;
      } else {
        ports.free(use_port);
        url = md.getUrl();
      }
      if (md.getDescriptor() != null) {
        // enable it now so that activation for 2nd one will fail
        ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, md.getDescriptor(),
                spawn_port);
        modules.put(id, new ModuleInstance(md, pmh, url));

        pmh.start(future -> {
          if (future.succeeded()) {
            ctx.response().setStatusCode(201)
                    .putHeader("Location", ctx.request().uri() + "/" + id)
                    .end();
          } else {
            modules.remove(md.getId());
            ports.free(use_port);
            ctx.response().setStatusCode(500).end(future.cause().getMessage());
          }
        });
      } else {
        modules.put(id, new ModuleInstance(md, null, url));
            ctx.response().setStatusCode(201)
                    .putHeader("Location", ctx.request().uri() + "/" + id)
                    .end();
      }
    } catch (DecodeException ex) {
      ctx.response().setStatusCode(400).end(ex.getMessage());
    }
  }

  public void get(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");

    ModuleInstance m = modules.get(id);
    if (m == null) {
      ctx.response().setStatusCode(404).end();
      return;
    }
    String s = Json.encodePrettily(modules.get(id).getModuleDescriptor());
    ctx.response().end(s);
  }
  public void list(RoutingContext ctx) {
    String s = Json.encodePrettily(modules.list());
    ctx.response().end(s);
  }

  public void delete(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");

    ModuleInstance m = modules.get(id);
    if (m == null) {
      ctx.response().setStatusCode(404).end();
      return;
    }

    ProcessModuleHandle pmh = m.getProcessModuleHandle();
    if (pmh == null) {
      ctx.response().setStatusCode(204).end();
    } else {
      pmh.stop(future -> {
        if (future.succeeded()) {
          modules.remove(id);
          ports.free(pmh.getPort());
          ctx.response().setStatusCode(204).end();
        } else {
          ctx.response().setStatusCode(500).end(future.cause().getMessage());
        }
      });
    }
  }

  public void deleteAll(Handler<AsyncResult<Void>> fut) {
    Set<String> list = modules.list();
    if ( list.isEmpty())
      fut.handle(new Success<Void>());
    else {
      String id = list.iterator().next();
      ModuleInstance mi = modules.get(id);
      ProcessModuleHandle pmh = mi.getProcessModuleHandle();
      if ( pmh == null ) {
        modules.remove(id);
        System.out.println("Deleted module " + id);
        deleteAll(fut);
      } else {
        pmh.stop(res -> {
          if (res.succeeded()) {
            System.out.println("Stopped module " + id);
            ports.free(pmh.getPort());
          } else {
            System.out.println("Failed to stop module " + id + ":" + res.cause().getMessage());
            fut.handle(new Failure<Void>("Failed to stop module " + id + ":" + res.cause().getMessage()));
            // TODO - What to in this case? Declare the whole node dead?
          }
          modules.remove(id); // remove in any case
          System.out.println("Deleted module " + id);
          deleteAll(fut);
        });
      }
    }
  }
   
} // class
