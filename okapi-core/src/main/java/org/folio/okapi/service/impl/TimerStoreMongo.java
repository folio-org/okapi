package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import io.vertx.ext.mongo.MongoClient;
import java.util.List;
import org.folio.okapi.bean.TimerDescriptor;
import org.folio.okapi.service.TimerStore;

public class TimerStoreMongo implements TimerStore {
  private static final String COLLECTION = "okapi.timers";
  private final MongoUtil<TimerDescriptor> util;

  public TimerStoreMongo(MongoClient cli) {
    this.util = new MongoUtil<>(COLLECTION, cli);
  }

  @Override
  public Future<Void> init(boolean reset) {
    return util.init(reset);
  }

  @Override
  public Future<List<TimerDescriptor>> getAll() {
    return util.getAll(TimerDescriptor.class);
  }

  @Override
  public Future<Void> put(TimerDescriptor timerDescriptor) {
    return util.add(timerDescriptor, timerDescriptor.getId());
  }
}
