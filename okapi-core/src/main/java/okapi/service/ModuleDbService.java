/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.service;

import io.vertx.core.Handler;
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
import static okapi.util.ErrorType.*;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import okapi.util.Success;

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
  final String timestampCollection = "okapi.timestamps";
  final String timestampId = "modules";
  private Long timestamp = new Long(-1);

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
      Long receivedStamp = (Long)(message.body());
      if ( this.timestamp < receivedStamp ) {
        System.out.println("Received message is newer tnan my config, reloading");
        // TODO - Actual reload - need to refactor that not to use ctx first
      } else {
        System.out.println("Received stamp is not newer, not reloading");
      }
    });

  }

  // Time stamp processing
  // TODO - Move to its own helper class

  private void updateTimeStamp(Handler<ExtendedAsyncResult<Long>> fut) {
    // TODO - Get the current timestamp, check if in the future
    // If so, just increment it by 1 ms, and hope we will catch up in time
    // This may work with daylight saving changes, but is not a generic solution
    long ts = System.currentTimeMillis();
    this.timestamp = ts;
    final String q = "{ \"_id\": \"" + timestampId + "\", "
                 + "\"timestamp\": \" " + Long.toString(ts)+ "\" }";
    JsonObject doc = new JsonObject(q);
    cli.save(timestampCollection, doc, res-> {
      if ( res.succeeded() ) {
          System.out.println("updated modules timestamp to " + ts);
          fut.handle(new Success<>(new Long(ts)));
      } else {
        fut.handle(new Failure<>(INTERNAL, "Updating module timestamp failed: "
                 + res.cause().getMessage() ));
      }
    });
  }

  private void getTimeStamp(Handler<ExtendedAsyncResult<Long>> fut) {
    final String q = "{ \"_id\": \"" + timestampId + "\"}";
    JsonObject jq = new JsonObject(q);
    cli.find(timestampCollection, jq, res -> {
      if (res.succeeded()) {
        List<JsonObject> l = res.result();
        if (l.size() > 0) {
          JsonObject d = l.get(0);
          d.remove("_id");
          System.out.println("Got time stamp " + d.encode());
          fut.handle(new Success<>(null));
        } else {
          fut.handle(new Failure<>(INTERNAL,"Corrupt database - no timestamp for modules" ));
        }
      } else {
        fut.handle(new Failure<>(INTERNAL, "Reading module timestamp failed "
                 + res.cause().getMessage() ));
      }
    });
  }

  private void sendReloadSignal(Handler<ExtendedAsyncResult<Long>> fut) {
    updateTimeStamp(res->{
      if ( res.failed() )
        fut.handle(res);
      else {
        eb.publish(eventBusName, res.result() );
        fut.handle(new Success<>(null));
      }
    });
  }

  public void init(RoutingContext ctx) {
    cli.dropCollection(collection, res -> {
      if (res.succeeded()) {
        this.sendReloadSignal(res2->{
          if ( res.succeeded())
            ctx.response().setStatusCode(204).end();
          else
            ctx.response().setStatusCode(500).end(res2.cause().getMessage());
        });
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
          //sendReloadSignal();
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
              //sendReloadSignal();
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
    reloadModules( res-> {
      if ( res.succeeded() ) {
        ctx.response().setStatusCode(204).end();
      } else {
        ctx.response().setStatusCode(500).end(res.cause().getMessage());
      }
    });

  }

  public void reloadModules(Handler<ExtendedAsyncResult<Void>> fut) {
    System.out.println("Starting to reload modules");
    moduleService.deleteAll(res->{
      if ( res.failed()) {
        System.out.println("Reload: Failed to delete all");
        fut.handle(res);
      } else {
        System.out.println("Reload: Should restart all modules");
        vertx.setTimer(1000, t -> {
          loadModules(fut);
        });
      }
    });

  }


  private void loadModules(Handler<ExtendedAsyncResult<Void>> fut) {
    String q = "{}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, res -> {
      if (res.failed()) {
        fut.handle( new Failure<>(INTERNAL,res.cause()));
      } else {
        System.out.println("Got " + res.result().size() + " modules to deploy");
        Iterator<JsonObject> it = res.result().iterator();
        loadR(it,fut);
      }
    });
  }

  private void loadR(Iterator<JsonObject> it, Handler<ExtendedAsyncResult<Void>> fut) {
    if ( !it.hasNext() ) {
      System.out.println("All modules deployed. Sleeping a second");
      vertx.setTimer(1000, t -> {
        // TODO - This is not right. But the tests occasionally fail after reload
        // Some kind of race condition
        System.out.println("Slept a second, loadR is done");
        fut.handle(new Success<>());
      });
    } else {
      JsonObject jo = it.next();
      jo.remove("_id");
      System.out.println("Starting " + jo);
      final ModuleDescriptor md = Json.decodeValue(jo.encode(),
              ModuleDescriptor.class);
      moduleService.create(md, res-> {
        if ( res.failed()) {
          fut.handle(new Failure<>(res.getType(),res.cause() ));
        } else {
          loadR(it, fut);
        }
      });
    }

  }
} // class
