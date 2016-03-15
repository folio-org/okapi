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
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
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
 * Services related to adding and deleting modules. All operations try to do the
 * thing on the locally running system first. If that succeeds, they update the
 * database, and tell other instances to reload the configuration.
 */
public class ModuleWebService {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  ModuleManager moduleManager;
  ModuleStore moduleStore;
  EventBus eb;
  private final String eventBusName = "okapi.conf.modules";
  final private Vertx vertx;
  final private TimeStampStore timeStampStore;
  final String timestampId = "modules";
  private Long timestamp = (long) -1;

  private void responseError(RoutingContext ctx, int code, Throwable cause) {
    responseText(ctx, code).end(cause.getMessage());
  }

  private HttpServerResponse responseText(RoutingContext ctx, int code) {
    return ctx.response()
            .setStatusCode(code)
            .putHeader("Content-Type", "text/plain");
  }

  private HttpServerResponse responseJson(RoutingContext ctx, int code) {
    return ctx.response()
            .setStatusCode(code)
            .putHeader("Content-Type", "application/json");
  }

  public ModuleWebService(Vertx vertx,
          ModuleManager moduleService, ModuleStore moduleStore,
          TimeStampStore timeStampStore) {
    this.vertx = vertx;
    this.moduleManager = moduleService;
    this.moduleStore = moduleStore;
    this.timeStampStore = timeStampStore;

    this.eb = vertx.eventBus();
    eb.consumer(eventBusName, message -> {
      Long receivedStamp = (Long) (message.body());
      if (this.timestamp < receivedStamp) {
        reloadModules(rres -> {
          if (rres.succeeded()) {
            logger.info("Reload of modules succeeded");
          } else {
            logger.fatal("Reload modules FAILED - No idea what to do about that!");
            // TODO - What can we do if reload fails here ?
            // We have nowehere to report failures. Declare the whole node dead?
          }
        });
      } else {
      }
    });
  }

  public void updateTimeStamp(Handler<ExtendedAsyncResult<Long>> fut) {
    timeStampStore.updateTimeStamp(timestampId, this.timestamp, res -> {
      if (res.succeeded()) {
        this.timestamp = res.result();
        fut.handle(new Success<>(timestamp));
      } else {
        fut.handle(res);
      }
    });
  }

  private void sendReloadSignal(Handler<ExtendedAsyncResult<Long>> fut) {
    updateTimeStamp(res -> {
      if (res.failed()) {
        fut.handle(res);
      } else {
        eb.publish(eventBusName, res.result());
        fut.handle(new Success<>(null));
      }
    });
  }

  public void create(RoutingContext ctx) {
    try {
      final ModuleDescriptor md = Json.decodeValue(ctx.getBodyAsString(),
              ModuleDescriptor.class);
      if (md.getId() == null || md.getId().isEmpty()) {
        responseText(ctx, 400).end("No Id in tenant");
      } else if (!md.getId().matches("^[a-z0-9._-]+$")) {
        responseText(ctx, 400).end("Invalid id");
      } else {
        moduleManager.create(md, cres -> {
          if (cres.failed()) {
            logger.error("Failed to start service, will not update the DB. " + md);
            if (cres.getType() == INTERNAL) {
              responseError(ctx, 500, cres.cause());
            } else { // must be some kind of bad request
              responseError(ctx, 400, cres.cause());
            }
          } else {
            moduleStore.insert(md, ires -> {
              if (ires.succeeded()) {
                sendReloadSignal(sres -> {
                  if (sres.succeeded()) {
                    final String s = Json.encodePrettily(md);
                    responseJson(ctx, 201)
                            .putHeader("Location", ctx.request().uri() + "/" + ires.result())
                            .end(s);
                  } else { // TODO - What to if this fails ??
                    responseError(ctx, 500, sres.cause());
                  }
                });
              } else {
                // This can only happen in some kind of race condition, we should
                // have detected duplicates when creating in the manager. This
                // TODO - How to test these cases?
                logger.warn("create failed " + ires.cause().getMessage());
                moduleManager.delete(md.getId(), dres -> { // remove from runtime too
                  if (dres.succeeded()) {
                    responseError(ctx, 500, ires.cause());
                    // Note, we return ires.cause, the reason why the insert failed
                  } else {
                    // TODO - What to do now - the system may be inconsistent!
                    responseError(ctx, 500, ires.cause());
                  }
                });
              }
            });
          }
        });
      }
    } catch (DecodeException ex) {
      responseError(ctx, 400, ex);
    }
  }

  public void update(RoutingContext ctx) {
    try {
      final ModuleDescriptor md = Json.decodeValue(ctx.getBodyAsString(),
              ModuleDescriptor.class);
      moduleManager.update(md, cres -> {
        if (cres.failed()) {
          logger.warn("Failed to update service, will not update the DB. " + md);
          if (cres.getType() == NOT_FOUND) {
            responseError(ctx, 404, cres.cause());
          } else {
            responseError(ctx, 500, cres.cause());
          }
        } else {
          moduleStore.update(md, ires -> {
            if (ires.succeeded()) {
              sendReloadSignal(sres -> {
                if (sres.succeeded()) {
                  final String s = Json.encodePrettily(md);
                  responseJson(ctx, 200).end(s);
                } else { // TODO - What to if this fails ??
                  responseError(ctx, 500, sres.cause());
                }
              });
            } else {
              logger.error("Module db update failed " + ires.cause().getMessage());
              responseError(ctx, 500, ires.cause());
            }
          });
        }
      });
    } catch (DecodeException ex) {
      responseError(ctx, 400, ex);
    }
  }

  public void get(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");
    final String q = "{ \"id\": \"" + id + "\"}";
    JsonObject jq = new JsonObject(q);
    //cli.find(collection, jq, res -> {
    moduleStore.get(id, res -> {
      if (res.succeeded()) {
        responseJson(ctx, 200).end(Json.encodePrettily(res.result()));
      } else if (res.getType() == NOT_FOUND) {
        responseError(ctx, 404, res.cause());
      } else { // must be internal error then
        responseError(ctx, 500, res.cause());
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
        responseJson(ctx, 200).end(Json.encodePrettily(ml));
      } else {
        responseError(ctx, 500, res.cause());
      }
    });
    // moduleManager.listIds(ctx);
  }

  /**
   * Delete a module. TODO - Is the logic the right way around? What to check
   * first for notfound? Deletes first from the running system, then from the
   * database.
   *
   * @param ctx
   */
  public void delete(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");
    moduleManager.delete(id, sres -> {
      if (sres.failed()) {
        logger.error("delete (runtime) failed: " + sres.getType()
                + ":" + sres.cause().getMessage());
        if (sres.getType() == NOT_FOUND) {
          responseError(ctx, 404, sres.cause());
        } else {
          responseError(ctx, 500, sres.cause());
        }
      } else {
        moduleStore.delete(id, rres -> {
          if (rres.succeeded()) {
            sendReloadSignal(res -> {
              if (res.succeeded()) {
                responseText(ctx, 204).end();
              } else { // TODO - What can be done if sending signal fails?
                // Probably best to report failure of deleting the module
                // we can not really undelete it here...
                responseError(ctx, 500, res.cause());
              }
            });
          } else if (rres.getType() == NOT_FOUND) {
            responseError(ctx, 404, rres.cause());
          } else {
            responseError(ctx, 500, rres.cause());
          }
        });
      }
    });
  }

  // TODO - Refactor this so that this part generates a listIds of modules,
  // and the module manager restarts and stops what is needed. Later.
  public void reloadModules(RoutingContext ctx) {
    reloadModules(res -> {
      if (res.succeeded()) {
        responseText(ctx, 204).end();
      } else {
        responseError(ctx, 500, res.cause());
      }
    });
  }

  public void reloadModules(Handler<ExtendedAsyncResult<Void>> fut) {
    moduleManager.deleteAll(res -> {
      if (res.failed()) {
        logger.error("ReloadModules: Failed to delete all");
        fut.handle(res);
      } else {
        logger.debug("ReloadModules: Should restart all modules");
        loadModules(fut);
      }
    });
  }

  private void loadModules(Handler<ExtendedAsyncResult<Void>> fut) {
    //cli.find(collection, jq, res -> {
    moduleStore.getAll(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        Iterator<ModuleDescriptor> it = res.result().iterator();
        loadR(it, fut);
      }
    });
  }

  private void loadR(Iterator<ModuleDescriptor> it,
          Handler<ExtendedAsyncResult<Void>> fut) {
    if (!it.hasNext()) {
      logger.debug("All modules deployed");
      fut.handle(new Success<>());
    } else {
      ModuleDescriptor md = it.next();
      logger.debug("About to start module " + md.getId());
      moduleManager.create(md, res -> {
        if (res.failed()) {
          logger.debug("Failed to start module " + md.getId() + ": " + res.cause());
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          loadR(it, fut);
        }
      });
    }
  }
} // class
