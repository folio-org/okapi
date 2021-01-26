package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import java.util.List;
import java.util.SortedMap;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.common.OkapiLogger;
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
  public Future<Void> init(boolean reset) {
    return util.init(reset);
  }

  @Override
  public Future<Void> insert(Tenant t) {
    return util.insert(t, t.getId());
  }

  @Override
  public Future<Void> updateDescriptor(TenantDescriptor td) {
    final String id = td.getId();
    JsonObject jq = new JsonObject().put("_id", id);
    return cli.find(COLLECTION, jq).compose(res -> {
      if (res.isEmpty()) {
        Tenant t = new Tenant(td);
        return insert(t);
      }
      JsonObject d = res.get(0);
      final Tenant t = decodeTenant(d);
      Tenant nt = new Tenant(td, t.getEnabled());
      JsonObject document = encodeTenant(nt, id);
      return cli.replaceDocuments(COLLECTION, jq, document).mapEmpty();
    });
  }

  @Override
  public Future<List<Tenant>> listTenants() {
    return util.getAll(Tenant.class);
  }

  @Override
  public Future<Boolean> delete(String id) {
    return util.delete(id);
  }

  @Override
  public Future<Boolean> updateModules(String id, SortedMap<String, Boolean> enabled) {
    JsonObject jq = new JsonObject().put("_id", id);
    return cli.find(COLLECTION, jq).compose(res -> {
      if (res.isEmpty()) {
        logger.debug("updatesModules: {} not found", id);
        return Future.succeededFuture(Boolean.FALSE);
      }
      JsonObject d = res.get(0);
      final Tenant t = decodeTenant(d);
      t.setEnabled(enabled);
      JsonObject document = encodeTenant(t, id);
      return cli.save(COLLECTION, document).map(Boolean.TRUE);
    });
  }

}
