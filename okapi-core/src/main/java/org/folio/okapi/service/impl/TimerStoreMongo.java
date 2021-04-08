package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import java.util.LinkedList;
import java.util.List;
import org.folio.okapi.bean.TimerDescriptor;
import org.folio.okapi.service.TimerStore;

public class TimerStoreMongo implements TimerStore {
  private static final String COLLECTION = "okapi.timers";
  private final MongoClient cli;
  private final MongoUtil<TimerDescriptor> util;

  public TimerStoreMongo(MongoClient cli) {
    this.cli = cli;
    this.util = new MongoUtil<>(COLLECTION, cli);
  }

  @Override
  public Future<Void> init(boolean reset) {
    return util.init(reset);
  }

  @Override
  public Future<List<TimerDescriptor>> getAll(String tenantId) {
    int prefixLen = tenantId.length() + 1;
    return util.getAll(TimerDescriptor.class).compose(x -> {
      List<TimerDescriptor> res = new LinkedList<>();
      for (TimerDescriptor timerDescriptor : x) {
        String tenantTimerId = timerDescriptor.getId();
        if (tenantTimerId.startsWith(tenantId + ".")) {
          timerDescriptor.setId(tenantTimerId.substring(prefixLen));
          res.add(timerDescriptor);
        }
      }
      return Future.succeededFuture(res);
    });
  }

  @Override
  public Future<Void> put(String tenantId, TimerDescriptor timerDescriptor) {
    // TODO: there must be a better way
    String encoded = Json.encode(timerDescriptor);
    TimerDescriptor timerDescriptor1 = new JsonObject(encoded).mapTo(TimerDescriptor.class);
    String newId = tenantId + "." + timerDescriptor.getId();
    timerDescriptor1.setId(newId);
    return util.add(timerDescriptor1, newId);
  }
}
