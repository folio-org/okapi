package org.folio.okapi.service.impl;

import org.folio.okapi.service.TenantStore;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.MongoClientUpdateResult;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

/**
 * Stores Tenants in a Mongo database.
 */
public class TenantStoreMongo implements TenantStore {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  MongoClient cli;
  final private String collection = "okapi.tenants";

  public TenantStoreMongo(MongoClient cli) {
    this.cli = cli;
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
  public void updateDescriptor(TenantDescriptor td, Handler<ExtendedAsyncResult<Void>> fut) {
    final String id = td.getId();
    JsonObject jq = new JsonObject().put("_id", id);
    cli.find(collection, jq, res -> {
      if (res.failed()) {
        logger.warn("updateDescriptor: find failed: " + res.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        List<JsonObject> l = res.result();
        if (l.size() == 0) {
          Tenant t = new Tenant(td);
          insert(t, ires -> {
            if (ires.succeeded()) {
              fut.handle(new Success<>());
            } else {
              fut.handle(new Failure<>(ires.getType(), ires.cause()));
            }
          });
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
          cli.replaceDocuments(collection, jq, document, ures -> {
            if (ures.succeeded()) {
              MongoClientUpdateResult result = ures.result();
              fut.handle(new Success<>());
            } else {
              logger.warn("Failed to update descriptor for " + id
                      + ": " + ures.cause().getMessage());
              fut.handle(new Failure<>(INTERNAL, ures.cause()));
            }
          });
        }
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
    JsonObject jq = new JsonObject().put("_id", id);
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
    JsonObject jq = new JsonObject().put("_id", id);
    cli.removeDocument(collection, jq, rres -> {
      if (rres.failed()) {
        fut.handle(new Failure<>(INTERNAL, rres.cause()));
      } else if (rres.result().getRemovedCount() == 0) {
        fut.handle(new Failure<>(NOT_FOUND, "Tenant " + id + " not found (delete)"));
      } else {
        fut.handle(new Success<>());
      }
    });
  }

  @Override
  public void enableModule(String id, String module,
          Handler<ExtendedAsyncResult<Void>> fut) {
    JsonObject jq = new JsonObject().put("_id", id);
    cli.find(collection, jq, gres -> {
      if (gres.failed()) {
        logger.warn("enableModule: find failed: " + gres.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, gres.cause()));
      } else {
        List<JsonObject> l = gres.result();
        if (l.isEmpty()) {
          logger.warn("enableModule: " + id + " not found: ");
          fut.handle(new Failure<>(NOT_FOUND, "Tenant " + id + " not found"));
        } else {
          JsonObject d = l.get(0);
          d.remove("_id");
          final Tenant t = Json.decodeValue(d.encode(), Tenant.class);
          t.enableModule(module);
          String s = Json.encodePrettily(t);
          JsonObject document = new JsonObject(s);
          document.put("_id", id);
          cli.save(collection, document, sres -> {
            if (sres.failed()) {
              logger.warn("enableModule: save failed: " + gres.cause().getMessage());
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
  public void disableModule(String id, String module,
          Handler<ExtendedAsyncResult<Void>> fut) {
    JsonObject jq = new JsonObject().put("_id", id);
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

  @Override
  public void updateModules(String id, TreeMap<String, Boolean> enabled, Handler<ExtendedAsyncResult<Void>> fut) {
    JsonObject jq = new JsonObject().put("_id", id);
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
          t.setEnabled(enabled);
          String s = Json.encodePrettily(t);
          JsonObject document = new JsonObject(s);
          document.put("_id", id);
          cli.save(collection, document, sres -> {
            if (sres.failed()) {
              logger.debug("TenantStoreMongo: disable: saving failed: " + sres.cause().getMessage());
              fut.handle(new Failure<>(INTERNAL, sres.cause()));
            } else {
              fut.handle(new Success<>());
            }
          });
        }
      }
    });
  }

}
