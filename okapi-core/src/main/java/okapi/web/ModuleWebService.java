/*
 * Copyright (C) 2016 Index Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import okapi.bean.ModuleDescriptorBrief;
import okapi.bean.RoutingEntry;
import okapi.service.ModuleManager;
import okapi.service.ModuleStore;
import okapi.service.TimeStampStore;
import static okapi.util.ErrorType.*;
import static okapi.util.HttpResponse.*;
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
            // We have nowhere to report failures. Declare the whole node dead?
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

  // Helper to validate some features of a md
  // Returns "" if ok, otherwise an informative error message
  private String validate(ModuleDescriptor md) {
    if (md.getId() == null || md.getId().isEmpty()) {
      return "No Id in module";
    }
    if (!md.getId().matches("^[a-z0-9._-]+$")) {
      return "Invalid id";
    }
    for (RoutingEntry e : md.getRoutingEntries()) {
      String t = e.getType();
      if (!(t.equals("request-only")
        || (t.equals("request-response"))
        || (t.equals("headers")))) {
        return "Bad routing entry type: '" + t + "'";
      }
    }
    return "";
  }


  public void create(RoutingContext ctx) {
    try {
      logger.debug("Trying to decode md: " + ctx.getBodyAsString() );  // !!!
      final ModuleDescriptor md = Json.decodeValue(ctx.getBodyAsString(),
              ModuleDescriptor.class);
      String validerr = validate(md);
      if (!validerr.isEmpty()) {
        responseError(ctx, 400, validerr);
      } else {
        moduleManager.create(md, cres -> {
          if (cres.failed()) {
            logger.error("Failed to start service, will not update the DB. " + md);
            logger.error("Cause: " + cres.cause().getMessage());
            responseError(ctx, cres.getType(), cres.cause());
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
                    responseError(ctx, sres.getType(), sres.cause());
                  }
                });
              } else {
                // This can only happen in some kind of race condition, we should
                // have detected duplicates when creating in the manager. 
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
      logger.debug("Failed to decode md: " + ctx.getBodyAsString() );
      responseError(ctx, 400, ex);
    }
  }

  public void update(RoutingContext ctx) {
    try {
      final ModuleDescriptor md = Json.decodeValue(ctx.getBodyAsString(),
        ModuleDescriptor.class);
      String validerr = validate(md);
      if (!validerr.isEmpty()) {
        responseError(ctx, 400, validerr);
      } else {
        moduleManager.update(md, cres -> {
          if (cres.failed()) {
            logger.warn("Failed to update service, will not update the DB. " + md);
            responseError(ctx, cres.getType(), cres.cause());
          } else {
            moduleStore.update(md, ires -> {
              if (ires.succeeded()) {
                sendReloadSignal(sres -> {
                  if (sres.succeeded()) {
                    final String s = Json.encodePrettily(md);
                    responseJson(ctx, 200).end(s);
                  } else { // TODO - What to do if this fails ??
                    responseError(ctx, sres.getType(), sres.cause());
                  }
                });
              } else {
                responseError(ctx, ires.getType(), ires.cause());
              }
            });
          }
        });
      }
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
      } else {
        responseError(ctx, res.getType(), res.cause());
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
        responseError(ctx, res.getType(), res.cause());
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
        responseError(ctx, sres.getType(), sres.cause());
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
          } else {
            responseError(ctx, rres.getType(), rres.cause());
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
        responseError(ctx, res.getType(), res.cause());
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

  public void loadModules(Handler<ExtendedAsyncResult<Void>> fut) {
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
