/*
 * Copyright (C) 2016 Index Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okapi.service.impl;

import okapi.service.TimeStampStore;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Success;

/**
 * Time stamps, as stored in the in-memory back end
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
