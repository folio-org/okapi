package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.mongo.MongoClient;
import java.util.List;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.service.EnvStore;


public class EnvStoreMongo implements EnvStore {

  private final MongoUtil<EnvEntry> util;
  private static final String COLLECTION = "okapi.env";

  public EnvStoreMongo(MongoClient cli) {
    this.util = new MongoUtil<>(COLLECTION, cli);
  }

  @Override
  public void add(EnvEntry env, Handler<ExtendedAsyncResult<Void>> fut) {
    util.add(env, env.getName(), fut);
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    util.delete(id, fut);
  }

  @Override
  public Future<Void> init(boolean reset) {
    return util.init(reset);
  }

  @Override
  public Future<List<EnvEntry>> getAll() {
    return util.getAll(EnvEntry.class);
  }
}
