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
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

/**
 * Stores Tenants in Postgres.
 */
public class TenantStorePostgres implements TenantStore {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private long lastTimestamp = 0;
  private PostgresHandle pg;


  public TenantStorePostgres(PostgresHandle pg) {
    logger.info("TenantStoreProgres");
    this.pg = pg;
  }

  @Override
  public void insert(Tenant t,
          Handler<ExtendedAsyncResult<String>> fut) {
    String id = t.getId();
    String s = Json.encodePrettily(t);
    JsonObject document = new JsonObject(s);
    pg.getConnection(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        fut.handle(new Success<>("HOLY"));
      }
    });
  }

  @Override
  public void update(Tenant t,
          Handler<ExtendedAsyncResult<String>> fut) {
    logger.fatal("update");
    fut.handle(new Failure<>(INTERNAL, "not implemented"));
  }

  @Override
  public void updateDescriptor(String id, TenantDescriptor td, Handler<ExtendedAsyncResult<Void>> fut) {
    logger.fatal("updateDescriptor");
    fut.handle(new Failure<>(INTERNAL, "not implemented"));
  }

  @Override
  public void listIds(Handler<ExtendedAsyncResult<List<String>>> fut) {
    logger.fatal("listIds");
    fut.handle(new Failure<>(INTERNAL, "not implemented"));
  }

  @Override
  public void listTenants(Handler<ExtendedAsyncResult<List<Tenant>>> fut) {
    logger.info("listTenants");
    List<Tenant> tl = new ArrayList<>();
    fut.handle(new Success<>(tl));
  }

  @Override
  public void get(String id, Handler<ExtendedAsyncResult<Tenant>> fut) {
    logger.fatal("get");
    fut.handle(new Failure<>(INTERNAL, "not implemented"));
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    logger.fatal("delete");
    fut.handle(new Failure<>(INTERNAL, "not implemented"));
  }

  @Override
  public void enableModule(String id, String module, long timestamp,
          Handler<ExtendedAsyncResult<Void>> fut) {
    logger.fatal("enableModule");
    fut.handle(new Failure<>(INTERNAL, "not implemented"));
  }

  @Override
  public void disableModule(String id, String module, long timestamp,
          Handler<ExtendedAsyncResult<Void>> fut) {
    logger.fatal("disableModule");
    fut.handle(new Failure<>(INTERNAL, "not implemented"));
  }
}
