/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
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
  public void updateTimeStamp(String stampId, Handler<ExtendedAsyncResult<Long>> fut) {
    // TODO - Get the current timestamp, check if in the future
    // If so, just increment it by 1 ms, and hope we will catch up in time
    // This may work with daylight saving changes, but is not a generic solution
    long ts = System.currentTimeMillis();
    final String q = "{ \"_id\": \"" + stampId + "\", "
                 + "\"timestamp\": \" " + Long.toString(ts)+ "\" }";
    JsonObject doc = new JsonObject(q);
    cli.save(collection, doc, res-> {
      if ( res.succeeded() ) {
          fut.handle(new Success<>(new Long(ts)));
      } else {
        fut.handle(new Failure<>(INTERNAL, "Updating timestamp  " + stampId + " failed: "
                 + res.cause().getMessage() ));
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
          System.out.println("Got time stamp " + stampId + ":" + ts);
          fut.handle(new Success<>(ts));
        } else {
          fut.handle(new Failure<>(INTERNAL,"Corrupt database - no timestamp for " + stampId ));
        }
      } else {
        fut.handle(new Failure<>(INTERNAL, "Reading timestamp " + stampId + " failed "
                 + res.cause().getMessage() ));
      }
    });
  }



}
