package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import io.vertx.ext.mongo.MongoClient;
import java.util.List;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.service.EnvStore;

public class EnvStoreMongo implements EnvStore {

  private final MongoUtil<EnvEntry> util;
  private static final String COLLECTION = "okapi.env";

  public EnvStoreMongo(MongoClient cli) {
    this.util = new MongoUtil<>(COLLECTION, cli);
  }

  @Override
  public Future<Void> add(EnvEntry env) {
    return util.add(env, env.getName());
  }

  @Override
  public Future<Boolean> delete(String id) {
    return util.delete(id);
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
