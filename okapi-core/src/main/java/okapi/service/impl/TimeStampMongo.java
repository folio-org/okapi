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
import io.vertx.ext.mongo.MongoClient;
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
public class TimeStampMongo implements TimeStampStore {

  MongoClient cli;
  final private String collection = "okapi.timestamps";

  public TimeStampMongo(MongoHandle mongo) {
    this.cli = mongo.getClient();
  }

  @Override
  public void updateTimeStamp(String stampId, long currentStamp, Handler<ExtendedAsyncResult<Long>> fut) {
    long ts = System.currentTimeMillis();
    if (ts < currentStamp) // the clock jumping backwards, or something
    {
      ts = currentStamp + 1;
    }
    final Long tsL = ts; // just to make it a final thing for the callback...
    final String q = "{ \"_id\": \"" + stampId + "\", "
            + "\"timestamp\": \" " + Long.toString(ts) + "\" }";
    JsonObject doc = new JsonObject(q);
    cli.save(collection, doc, res -> {
      if (res.succeeded()) {
        fut.handle(new Success<>(tsL));
      } else {
        fut.handle(new Failure<>(INTERNAL, "Updating timestamp  " + stampId + " failed: "
                + res.cause().getMessage()));
      }
    });
  }

  @Override
  public void getTimeStamp(String stampId, Handler<ExtendedAsyncResult<Long>> fut) {
    final String q = "{ \"_id\": \"" + stampId + "\"}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, res -> {
      if (res.succeeded()) {
        List<JsonObject> l = res.result();
        if (l.size() > 0) {
          JsonObject d = l.get(0);
          Long ts = d.getLong("timestamp");
          fut.handle(new Success<>(ts));
        } else {
          fut.handle(new Failure<>(INTERNAL, "Corrupt database - no timestamp for " + stampId));
        }
      } else {
        fut.handle(new Failure<>(INTERNAL, "Reading timestamp " + stampId + " failed "
                + res.cause().getMessage()));
      }
    });
  }

}
