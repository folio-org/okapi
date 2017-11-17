package org.folio.okapi.service.impl;

import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import java.util.List;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.service.EnvStore;

public class EnvStoreMongo implements EnvStore {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final MongoUtil util;
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
  public void init(boolean reset, Handler<ExtendedAsyncResult<Void>> fut) {
    util.init(reset, fut);
  }

  @Override
  public void getAll(Handler<ExtendedAsyncResult<List<EnvEntry>>> fut) {
    util.getAll(EnvEntry.class, fut);
  }
}
