/*
 * Copyright (c) 2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.service.impl;

import okapi.service.TenantStore;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import java.util.ArrayList;
import java.util.List;
import okapi.bean.Tenant;
import okapi.bean.TenantDescriptor;
import static okapi.util.ErrorType.*;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import okapi.util.Success;

public class TenantStoreMongo implements TenantStore {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  MongoClient cli;
  final private String collection = "okapi.tenants";
  private long lastTimestamp = 0;

  public TenantStoreMongo(MongoHandle mongo) {
    this.cli = mongo.getClient();
  }

  @Override
  public void insert(Tenant t,
          Handler<ExtendedAsyncResult<String>> fut) {
    String id = t.getId();
    String s = Json.encodePrettily(t);
    JsonObject document = new JsonObject(s);
    document.put("_id", id);
    cli.insert(collection, document, res -> {
      if (res.succeeded()) {
        fut.handle(new Success<>(id));
      } else {
        logger.debug("TenantStoreMongo: Failed to insert " + id
                + ": " + res.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      }
    });
  }

  @Override
  public void update(Tenant t,
          Handler<ExtendedAsyncResult<String>> fut) {
    String id = t.getId();
    String s = Json.encodePrettily(t);
    JsonObject document = new JsonObject(s);
    document.put("_id", id);
    final String q = "{ \"_id\": \"" + id + "\"}";
    JsonObject jq = new JsonObject(q);
    cli.replace(collection, jq, document, res -> {
      if (res.succeeded()) {
        fut.handle(new Success<>(id));
      } else {
        logger.debug("TenantStoreMongo: Failed to update " + id
                + ": " + res.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      }
    });
  }

  @Override
  public void updateDescriptor(String id, TenantDescriptor td, Handler<ExtendedAsyncResult<Void>> fut) {
    final String q = "{ \"_id\": \"" + id + "\"}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, res -> {
      if (res.failed()) {
        logger.debug("updateDescriptor: find failed: " + res.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        List<JsonObject> l = res.result();
        if (l.size() == 0) {
          fut.handle(new Failure<>(NOT_FOUND, "Tenant " + id + " not found"));
        } else {
          JsonObject d = l.get(0);
          d.remove("_id");
          final Tenant t = Json.decodeValue(d.encode(), Tenant.class);
          Tenant nt = new Tenant(td, t.getEnabled());
          // TODO - Validate that we don't change the id
          // tenants.put(id, nt);
          String s = Json.encodePrettily(nt);
          JsonObject document = new JsonObject(s);
          document.put("_id", id);
          cli.replace(collection, jq, document, ures -> {
            if (ures.succeeded()) {
              fut.handle(new Success<>());
            } else {
              logger.debug("Failed to update descriptor for " + id
                      + ": " + ures.cause().getMessage());
              fut.handle(new Failure<>(INTERNAL, ures.cause()));
            }
          });
        }
      }
    });
  }

  @Override
  public void listIds(Handler<ExtendedAsyncResult<List<String>>> fut) {
    String q = "{}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        List<String> ids = new ArrayList<>(res.result().size());
        for (JsonObject jo : res.result()) {
          jo.remove("_id");
          final Tenant t = Json.decodeValue(jo.encode(), Tenant.class);
          ids.add(t.getId());
        }
        fut.handle(new Success<>(ids));
      }
    });
  }

  @Override
  public void listTenants(Handler<ExtendedAsyncResult<List<Tenant>>> fut) {
    String q = "{}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        List<Tenant> ts = new ArrayList<>(res.result().size());
        for (JsonObject jo : res.result()) {
          jo.remove("_id");
          final Tenant t = Json.decodeValue(jo.encode(), Tenant.class);
          ts.add(t);
        }
        fut.handle(new Success<>(ts));
      }
    });
  }

  @Override
  public void get(String id, Handler<ExtendedAsyncResult<Tenant>> fut) {
    final String q = "{ \"_id\": \"" + id + "\"}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, res -> {
      if (res.failed()) {
        logger.debug("TenantStoreMongo: get: find failed: " + res.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        List<JsonObject> l = res.result();
        if (l.size() == 0) {
          fut.handle(new Failure<>(NOT_FOUND, "Tenant " + id + " not found"));
        } else {
          JsonObject d = l.get(0);
          d.remove("_id");
          final Tenant t = Json.decodeValue(d.encode(), Tenant.class);
          fut.handle(new Success<>(t));
        }
      }
    });
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    String q = "{ \"_id\": \"" + id + "\"}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, fres -> {
      if (fres.failed()) {
        fut.handle(new Failure<>(INTERNAL, fres.cause()));
      } else {
        List<JsonObject> l = fres.result();
        if (l.size() == 0) {
          logger.debug("TeanntStoreMongo: delete. Not found " + id + ":" + q);
          fut.handle(new Failure<>(NOT_FOUND, "Tenant " + id + " not found (delete)"));
        } else {
          cli.remove(collection, jq, rres -> {
            if (rres.failed()) {
              fut.handle(new Failure<>(INTERNAL, rres.cause()));
            } else {
              fut.handle(new Success<>());
            }
          });
        }
      }
    });
  }

  @Override
  public void enableModule(String id, String module, long timestamp,
          Handler<ExtendedAsyncResult<Void>> fut) {
    final String q = "{ \"_id\": \"" + id + "\"}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, gres -> {
      if (gres.failed()) {
        logger.debug("enableModule: find failed: " + gres.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, gres.cause()));
      } else {
        List<JsonObject> l = gres.result();
        if (l.isEmpty()) {
          logger.debug("enableModule: " + id + " not found: ");
          fut.handle(new Failure<>(NOT_FOUND, "Tenant " + id + " not found"));
        } else {
          JsonObject d = l.get(0);
          d.remove("_id");
          final Tenant t = Json.decodeValue(d.encode(), Tenant.class);
          t.setTimestamp(timestamp);
          t.enableModule(module);
          String s = Json.encodePrettily(t);
          JsonObject document = new JsonObject(s);
          document.put("_id", id);
          cli.save(collection, document, sres -> {
            if (sres.failed()) {
              logger.debug("TenantStoreMongo: enable: saving failed: " + gres.cause().getMessage());
              fut.handle(new Failure<>(INTERNAL, gres.cause()));
            } else {
              fut.handle(new Success<>());
            }
          });
        }
      }
    });
  }

  @Override
  public void disableModule(String id, String module, long timestamp,
          Handler<ExtendedAsyncResult<Void>> fut) {
    final String q = "{ \"_id\": \"" + id + "\"}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, gres -> {
      if (gres.failed()) {
        logger.debug("disableModule: find failed: " + gres.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, gres.cause()));
      } else {
        List<JsonObject> l = gres.result();
        if (l.isEmpty()) {
          logger.debug("disableModule: not found: " + id);
          fut.handle(new Failure<>(NOT_FOUND, "Tenant " + id + " not found"));
        } else {
          JsonObject d = l.get(0);
          d.remove("_id");
          final Tenant t = Json.decodeValue(d.encode(), Tenant.class);
          t.setTimestamp(timestamp);
          t.disableModule(module);
          String s = Json.encodePrettily(t);
          JsonObject document = new JsonObject(s);
          document.put("_id", id);
          cli.save(collection, document, sres -> {
            if (sres.failed()) {
              logger.debug("TenantStoreMongo: disable: saving failed: " + gres.cause().getMessage());
              fut.handle(new Failure<>(INTERNAL, gres.cause()));
            } else {
              fut.handle(new Success<>());
            }
          });
        }
      }
    });
  }

}
