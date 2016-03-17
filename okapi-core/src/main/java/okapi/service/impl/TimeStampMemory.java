/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.service.impl;

import okapi.service.TimeStampStore;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Success;


/**
 * Time stamps, as stored in the in-memory back end
 * 
 */
public class TimeStampMemory implements TimeStampStore {
  Long lastTs = (long)-1;

  public TimeStampMemory(Vertx vertx) {
  }

  @Override
  public void updateTimeStamp(String stampId, long currentStamp, Handler<ExtendedAsyncResult<Long>> fut) {
    long ts = System.currentTimeMillis();
    if ( ts < currentStamp )  // the clock jumping backwards, or something
      ts = currentStamp + 1;
    lastTs = ts;
    fut.handle(new Success<>(ts));
  }

  @Override
  public void getTimeStamp(String stampId, Handler<ExtendedAsyncResult<Long>> fut) {
    fut.handle(new Success<>(lastTs));
  }

}
