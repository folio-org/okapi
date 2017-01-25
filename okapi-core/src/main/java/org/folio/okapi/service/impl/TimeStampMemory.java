package org.folio.okapi.service.impl;

import org.folio.okapi.service.TimeStampStore;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Success;

/**
 * Time stamps, as stored in the in-memory back end.
 */
public class TimeStampMemory implements TimeStampStore {

  Long lastTs = (long) -1;

  public TimeStampMemory(Vertx vertx) {
  }

  @Override
  public void updateTimeStamp(String stampId, long currentStamp, Handler<ExtendedAsyncResult<Long>> fut) {
    long ts = System.currentTimeMillis();
    if (ts < currentStamp) // the clock jumping backwards, or something
    {
      ts = currentStamp + 1;
    }
    lastTs = ts;
    fut.handle(new Success<>(ts));
  }

  @Override
  public void getTimeStamp(String stampId, Handler<ExtendedAsyncResult<Long>> fut) {
    fut.handle(new Success<>(lastTs));
  }

}
