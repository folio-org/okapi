/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.service.impl;

import okapi.service.TimeStampStore;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import java.util.List;
import static okapi.util.ErrorType.INTERNAL;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import okapi.util.Success;


/**
 * Time stamps, as stored in Mongo
 * 
 */
public class TimeStampMemory implements TimeStampStore {
  Long lastTs = (long)-1;

  public TimeStampMemory(Vertx vertx) {
  }

  @Override
  public void updateTimeStamp(String stampId, Handler<ExtendedAsyncResult<Long>> fut) {
    long ts = System.currentTimeMillis();
    lastTs = ts;
    fut.handle(new Success<>(ts));
  }

  @Override
  public void getTimeStamp(String stampId, Handler<ExtendedAsyncResult<Long>> fut) {
    fut.handle(new Success<>(lastTs));
  }

}
