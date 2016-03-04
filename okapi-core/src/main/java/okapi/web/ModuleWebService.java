/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.web;

import io.vertx.core.Handler;
import okapi.bean.ModuleDescriptor;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.Iterator;
import io.vertx.core.eventbus.EventBus;
import java.util.ArrayList;
import java.util.List;
import okapi.bean.ModuleDescriptorBrief;
import okapi.service.ModuleManager;
import okapi.service.ModuleStore;
import okapi.service.TimeStampStore;
import static okapi.util.ErrorType.*;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import okapi.util.Success;


/**
 * Services related to adding and deleting modules.
 * All operations try to do the thing on the locally running system first.
 * If that succeeds, they update the database, and tell other instances to
 * reload the configuration.
 */
public class ModuleWebService {
  ModuleManager moduleManager;
  ModuleStore moduleStore; 
  EventBus eb;
  private final String eventBusName = "okapi.conf.modules";
  final private Vertx vertx;
  final private TimeStampStore timeStampStore;
  final String timestampId = "modules";
  private Long timestamp = (long) -1;

  public ModuleWebService(Vertx vertx,
            ModuleManager moduleService, ModuleStore moduleStore,
            TimeStampStore timeStampStore) {
    this.vertx = vertx;
    this.moduleManager = moduleService;
    this.moduleStore = moduleStore;
    this.timeStampStore = timeStampStore;

    this.eb = vertx.eventBus();
    eb.consumer(eventBusName, message -> {
      Long receivedStamp = (Long)(message.body());
      if ( this.timestamp < receivedStamp ) {
        reloadModules( rres-> {
          if ( rres.succeeded())
            System.out.println("Reload of modules succeeded");
          else {
            System.out.println("Reload modules FAILED - No idea what to do about that!");
            // TODO - What can we do if reload fails here ?
            // We have nowehere to report failures. Declare the whole node dead?
          }
        });
      } else {
        //System.out.println("Received stamp is not newer, not reloading modules");
      }
    });

  }

  public void updateTimeStamp(Handler<ExtendedAsyncResult<Long>> fut) {
    timeStampStore.updateTimeStamp(timestampId, this.timestamp, res->{
      if ( res.succeeded() ) {
        this.timestamp = res.result();
        //System.out.println("updated modules timestamp to " + timestamp);
          fut.handle(new Success<>(timestamp));
      } else {
        fut.handle(res);
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


  public void create(RoutingContext ctx) {
    try {
      final ModuleDescriptor md = Json.decodeValue(ctx.getBodyAsString(),
        ModuleDescriptor.class);
      if (md.getId() == null || md.getId().isEmpty()) {
        ctx.response().setStatusCode(400).end("No Id in tenant");
      } else if (!md.getId().matches("^[a-z0-9._-]+$")) {
        ctx.response().setStatusCode(400).end("Invalid id");
      } else {
        moduleManager.create(md, cres -> {
          if (cres.failed()) {
            System.out.println("Failed to start service, will not update the DB. " + md);
            if (cres.getType() == INTERNAL) {
              ctx.response().setStatusCode(500).end(cres.cause().getMessage());
            } else { // must be some kind of bad request
              ctx.response().setStatusCode(400).end(cres.cause().getMessage());
            }
          } else {
            moduleStore.insert(md, ires -> {
              if (ires.succeeded()) {
                sendReloadSignal(sres -> {
                  if (sres.succeeded()) {
                    final String s = Json.encodePrettily(md);
                    ctx.response().setStatusCode(201)
                      .putHeader("Location", ctx.request().uri() + "/" + ires.result())
                      .end(s);
                  } else { // TODO - What to if this fails ??
                    ctx.response().setStatusCode(500).end(sres.cause().getMessage());
                  }
                });
              } else {
                // This can only happen in some kind of race condition, we should
                // have detected duplicates when creating in the manager. This
                // TODO - How to test these cases?
                System.out.println("create failed " + ires.cause().getMessage());
                moduleManager.delete(md.getId(), dres->{ // remove from runtime too
                  if ( dres.succeeded()) {
                    ctx.response().setStatusCode(500).end(ires.cause().getMessage());
                    // Note, we return ires.cause, the reason why the insert failed
                  } else {
                    // TODO - What to do now - the system may be inconsistent!
                    ctx.response().setStatusCode(500).end(ires.cause().getMessage());
                  }
                });
              }
            });
          }
        });
      }
    } catch (DecodeException ex) {
      ctx.response().setStatusCode(400).end(ex.getMessage());
    }
  }

  public void update(RoutingContext ctx) {
    try {
      final ModuleDescriptor md = Json.decodeValue(ctx.getBodyAsString(),
        ModuleDescriptor.class);
      moduleManager.update(md, cres -> {
        if (cres.failed()) {
          System.out.println("Failed to update service, will not update the DB. " + md);
          if (cres.getType() == NOT_FOUND) {
            ctx.response().setStatusCode(404).end(cres.cause().getMessage());
          } else {
            ctx.response().setStatusCode(500).end(cres.cause().getMessage());
          }
        } else {
          moduleStore.update(md, ires -> {
            if (ires.succeeded()) {
              sendReloadSignal(sres->{
                if ( sres.succeeded()) {
                  final String s = Json.encodePrettily(md);
                  ctx.response().setStatusCode(200)
                    .end(s);
                } else { // TODO - What to if this fails ??
                  ctx.response().setStatusCode(500).end(sres.cause().getMessage());
                }
              });
            } else {
              System.out.println("Module db update failred " + ires.cause().getMessage());
              ctx.response().setStatusCode(500).end(ires.cause().getMessage());
            }
          });
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
    //cli.find(collection, jq, res -> {
    moduleStore.get(id, res -> {
      if (res.succeeded()) {
        ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(Json.encodePrettily(res.result()));
      } else if (res.getType() == NOT_FOUND) {
        ctx.response().setStatusCode(404).end(res.cause().getMessage());
      } else { // must be internal error then
        ctx.response().setStatusCode(500).end(res.cause().getMessage());
      }
    });
  }

  public void list(RoutingContext ctx) {
    moduleStore.getAll(res -> {
      if (res.succeeded()) {
        List<ModuleDescriptorBrief> ml = new ArrayList<>(res.result().size());
        for (ModuleDescriptor md : res.result()) {
          ml.add(new ModuleDescriptorBrief(md));
        }
        ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(Json.encodePrettily(ml));
      } else {
        ctx.response().setStatusCode(500).end(res.cause().getMessage());
      }
    });
    // moduleManager.listIds(ctx);
  }

  /**
   * Delete a module.
   * TODO - Is the logic the right way around? What to check first for notfound?
   * Deletes first from the running system, then from the database.
   * @param ctx
   */
  public void delete(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");
    moduleManager.delete(id, sres->{
      if ( sres.failed()) {
        System.out.println("delete (runtime) failed: " + sres.getType() + ":" + sres.cause().getMessage());
        if ( sres.getType() == NOT_FOUND)
          ctx.response().setStatusCode(404).end(sres.cause().getMessage());
        else
          ctx.response().setStatusCode(500).end(sres.cause().getMessage());
      } else {
        moduleStore.delete(id, rres -> {
          if (rres.succeeded()) {
            sendReloadSignal(res -> {
              if (res.succeeded()) {
                ctx.response().setStatusCode(204).end();
              } else { // TODO - What can be done if sending signal fails?
                // Probably best to report failure of deleting the module
                // we can not really undelete it here...
                ctx.response().setStatusCode(500).end(rres.cause().getMessage());
              }
            });
          } else {
            if (rres.getType() == NOT_FOUND) {
              ctx.response().setStatusCode(404).end(rres.cause().getMessage());
            } else {
              ctx.response().setStatusCode(500).end(rres.cause().getMessage());
            }
          }
        });
      }
    });
  }

  // TODO - Refactor this so that this part generates a listIds of modules,
  // and the module manager restarts and stops what is needed. Later.
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
    moduleManager.deleteAll(res->{
      if ( res.failed()) {
        System.out.println("ReloadModules: Failed to delete all");
        fut.handle(res);
      } else {
        System.out.println("ReloadModules: Should restart all modules");
        loadModules(fut);
      }
    });

  }


  private void loadModules(Handler<ExtendedAsyncResult<Void>> fut) {
    //cli.find(collection, jq, res -> {
    moduleStore.getAll( res -> {
      if (res.failed()) {
        fut.handle( new Failure<>(INTERNAL,res.cause()));
      } else {
        Iterator<ModuleDescriptor> it = res.result().iterator();
        loadR(it,fut);
      }
    });
  }

  private void loadR(Iterator<ModuleDescriptor> it, Handler<ExtendedAsyncResult<Void>> fut) {
    if ( !it.hasNext() ) {
      System.out.println("All modules deployed");
      fut.handle(new Success<>());
    } else {
      ModuleDescriptor md = it.next();
      moduleManager.create(md, res-> {
        if ( res.failed()) {
          fut.handle(new Failure<>(res.getType(),res.cause() ));
        } else {
          loadR(it, fut);
        }
      });
    }

  }
} // class
