package org.folio.okapi.service.impl;

import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import java.util.List;
import java.util.SortedMap;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;
import org.folio.okapi.service.TenantStore;

/**
 * Stores Tenants in a Mongo database.
 */
@java.lang.SuppressWarnings({"squid:S1192"})
public class TenantStoreMongo implements TenantStore {

  private final Logger logger = OkapiLogger.get();
  private final MongoClient cli;
  private final MongoUtil<Tenant> util;
  private static final String COLLECTION = "okapi.tenants";
  private final Messages messages = Messages.getInstance();

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
    this.util = new MongoUtil<>(COLLECTION, cli);
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
        logger.warn("updateDescriptor: find failed: {}", res.cause().getMessage());
        fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
      } else {
        List<JsonObject> l = res.result();
        if (l.isEmpty()) {
          Tenant t = new Tenant(td);
          insert(t, fut);
        } else {
          JsonObject d = l.get(0);
          final Tenant t = decodeTenant(d);
          Tenant nt = new Tenant(td, t.getEnabled());
          JsonObject document = encodeTenant(nt, id);
          cli.replaceDocuments(COLLECTION, jq, document, ures -> {
            if (ures.succeeded()) {
              fut.handle(new Success<>());
            } else {
              logger.warn("Failed to update descriptor for {}: {}",
                id, ures.cause().getMessage());
              fut.handle(new Failure<>(ErrorType.INTERNAL, ures.cause()));
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
  public void updateModules(String id, SortedMap<String, Boolean> enabled,
                            Handler<ExtendedAsyncResult<Void>> fut) {
    JsonObject jq = new JsonObject().put("_id", id);
    cli.find(COLLECTION, jq, gres -> {
      if (gres.failed()) {
        logger.debug("updateModules: {} find failed: {}", id, gres.cause().getMessage());
        fut.handle(new Failure<>(ErrorType.INTERNAL, gres.cause()));
      } else {
        List<JsonObject> l = gres.result();
        if (l.isEmpty()) {
          logger.debug("updatesModules: {} not found", id);
          fut.handle(new Failure<>(ErrorType.NOT_FOUND, messages.getMessage("11200", id)));
        } else {
          JsonObject d = l.get(0);
          final Tenant t = decodeTenant(d);
          t.setEnabled(enabled);
          JsonObject document = encodeTenant(t, id);
          cli.save(COLLECTION, document, sres -> {
            if (sres.failed()) {
              logger.debug("updateModules: {} saving failed: {}", id, sres.cause().getMessage());
              fut.handle(new Failure<>(ErrorType.INTERNAL, sres.cause()));
            } else {
              fut.handle(new Success<>());
            }
          });
        }
      }
    });
  }

}
