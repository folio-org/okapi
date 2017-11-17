package org.folio.okapi.service.impl;

import org.folio.okapi.service.TenantStore;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

/**
 * Stores Tenants in a Mongo database.
 */
@java.lang.SuppressWarnings({"squid:S1192"})
public class TenantStoreMongo implements TenantStore {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final MongoClient cli;
  private final MongoUtil<Tenant> util;
  private static final String COLLECTION = "okapi.tenants";

  private JsonObject encodeTenant(Tenant t, String id) {
    JsonObject j = new JsonObject(Json.encode(t));
    util.encode(j, id);
    return j;
  }

  private Tenant decodeTenant(JsonObject j) {
    util.decode(j);
    return Json.decodeValue(j.encode(), Tenant.class);
  }

  public TenantStoreMongo(MongoClient cli) {
    this.cli = cli;
    this.util = new MongoUtil(COLLECTION, cli);
  }

  @Override
  public void init(boolean reset, Handler<ExtendedAsyncResult<Void>> fut) {
    util.init(reset, fut);
  }

  @Override
  public void insert(Tenant t, Handler<ExtendedAsyncResult<Void>> fut) {
    util.insert(t, t.getId(), fut);
  }

  @Override
  public void updateDescriptor(TenantDescriptor td, Handler<ExtendedAsyncResult<Void>> fut) {
    final String id = td.getId();
    JsonObject jq = new JsonObject().put("_id", id);
    cli.find(COLLECTION, jq, res
      -> {
      if (res.failed()) {
        logger.warn("updateDescriptor: find failed: " + res.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        List<JsonObject> l = res.result();
        if (l.isEmpty()) {
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
          final Tenant t = decodeTenant(d);
          Tenant nt = new Tenant(td, t.getEnabled());
          JsonObject document = encodeTenant(nt, id);
          cli.replaceDocuments(COLLECTION, jq, document, ures -> {
            if (ures.succeeded()) {
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
    util.getAll(Tenant.class, fut);
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    util.delete(id, fut);
  }

  @Override
  public void updateModules(String id, SortedMap<String, Boolean> enabled, Handler<ExtendedAsyncResult<Void>> fut) {
    JsonObject jq = new JsonObject().put("_id", id);
    cli.find(COLLECTION, jq, gres -> {
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
          final Tenant t = decodeTenant(d);
          t.setEnabled(enabled);
          JsonObject document = encodeTenant(t, id);
          cli.save(COLLECTION, document, sres -> {
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
