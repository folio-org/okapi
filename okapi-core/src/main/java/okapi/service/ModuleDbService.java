/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.service;

import okapi.bean.ModuleDescriptor;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import io.vertx.core.eventbus.EventBus;

/* TODO
  - Message bus to force a reload after any op: broadcast, receive, reload
  - Factor the Mongo stuff away, make a memory-only alternative
  - Remove http stuff from moduleservice
  - Rename moduleService to moduleManager

*/


public class ModuleDbService {
  ModuleService moduleService;
  MongoClient cli;
  EventBus eb;
  private final String eventBusName = "okapi.conf.modules";

  final private Vertx vertx;
  final String collection = "okapi.modules";

  public ModuleDbService(Vertx vertx, ModuleService moduleService) {
    this.vertx = vertx;
    this.moduleService = moduleService;

    JsonObject opt = new JsonObject().
            put("host", "127.0.0.1").
            put("port", 27017);
    this.cli = MongoClient.createShared(vertx, opt);

    
    this.eb = vertx.eventBus();
    eb.consumer(eventBusName, message -> {
      System.out.println("I have received a message: " + message.body());
    });

  }

  private void sendReloadSignal() {
    eb.publish(eventBusName, "Someone did something with the config!");
  }

  public void init(RoutingContext ctx) {
    cli.dropCollection(collection, res -> {
      if (res.succeeded()) {
        this.sendReloadSignal();
        ctx.response().setStatusCode(204).end();
      } else {
        ctx.response().setStatusCode(500).end(res.cause().getMessage());
      }
    });
  }
  
  public void create(RoutingContext ctx) {
    try {
      final ModuleDescriptor md = Json.decodeValue(ctx.getBodyAsString(),
              ModuleDescriptor.class);
      String s = Json.encodePrettily(md);
      JsonObject document = new JsonObject(s);
      document.put("_id", document.getString("id"));
      cli.insert(collection, document, res -> {
        if (res.succeeded()) {
          moduleService.create(ctx);  // TODO - try this first, with the md
          sendReloadSignal();
        } else {
          System.out.println("create failred " + res.cause().getLocalizedMessage());
          ctx.response().setStatusCode(500).end(res.cause().getMessage());
        }
      });
    } catch (DecodeException ex) {
      ctx.response().setStatusCode(400).end(ex.getMessage());
    }
  }

  public void get(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");
    final String q = "{ \"id\": \"" + id + "\"}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, res -> {
      if (res.succeeded()) {
        List<JsonObject> l = res.result();
        if (l.size() > 0) {
          JsonObject d = l.get(0);
          d.remove("_id");
          ctx.response().setStatusCode(200).end(Json.encodePrettily(d));
        } else {
          ctx.response().setStatusCode(404).end();
        }
      } else {
        ctx.response().setStatusCode(500).end(res.cause().getMessage());
      }
    });
  }

  public void list(RoutingContext ctx) {
    String q = "{}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, res -> {
      List<String> ids = new ArrayList<>(res.result().size());
      if (res.succeeded()) {
        for (JsonObject jo : res.result()) {
          ids.add(jo.getString("id"));
        }
        ctx.response().setStatusCode(200).end(Json.encodePrettily(ids));
      } else {
        ctx.response().setStatusCode(500).end(res.cause().getMessage());
      }
    });
    // moduleService.list(ctx);
  }

  public void delete(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");
    String q = "{ \"id\": \"" + id + "\"}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, res -> {
      if (res.succeeded()) {
        List<JsonObject> l = res.result();
        if (l.size() > 0) {
          cli.remove(collection, jq, res2 -> {
            if (res2.succeeded()) {
              moduleService.delete(ctx);
              // ctx.response().setStatusCode(204).end();
              sendReloadSignal();
            } else {
              ctx.response().setStatusCode(500).end(res2.cause().getMessage());
            }
          });
        } else {
          ctx.response().setStatusCode(404).end();
        }
      } else {
        ctx.response().setStatusCode(500).end(res.cause().getMessage());
      }
    });
  }

  public void reloadModules(RoutingContext ctx) {
    System.out.println("Starting to reload modules");
    moduleService.deleteAll(res->{
      if ( res.failed()) {
        System.out.println("Reload: Failed to delete all");
        ctx.response().setStatusCode(500).end(res.cause().getMessage());
      } else {
        System.out.println("Reload: Should restart all modules");
        loadModules(ctx);
      }
    });    
  }

  private void loadModules(RoutingContext ctx) {
    String q = "{}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, res -> {
      if (res.failed()) {
        ctx.response().setStatusCode(500).end(res.cause().getMessage());
      } else {
        System.out.println("Got " + res.result().size() + " modules to deploy");
        Iterator<JsonObject> it = res.result().iterator();
        loadR(it,ctx);
      }
    });
  }

  private void loadR(Iterator<JsonObject> it, RoutingContext ctx) {
    if ( !it.hasNext() ) {
      System.out.println("All modules deployed");
      ctx.response().setStatusCode(204).end();
    } else {
      JsonObject jo = it.next();
      jo.remove("_id");
      System.out.println("Starting " + jo);
      final ModuleDescriptor md = Json.decodeValue(jo.encode(),
              ModuleDescriptor.class);
      moduleService.create(md, res-> {
        if ( res.failed()) {
          ctx.response().setStatusCode(500).end( res.cause().getMessage());
        } else {
          loadR(it, ctx);
        }
      });
    }

  }
} // class
