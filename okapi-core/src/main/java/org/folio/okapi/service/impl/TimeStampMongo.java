package org.folio.okapi.service.impl;

import org.folio.okapi.service.TimeStampStore;
import io.vertx.core.Handler;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.core.json.JsonObject;
import java.util.List;
import static org.folio.okapi.common.ErrorType.INTERNAL;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

/**
 * Time stamps, as stored in Mongo
 */
public class TimeStampMongo implements TimeStampStore {

  MongoClient cli;
  final private String collection = "okapi.timestamps";

  public TimeStampMongo(MongoClient cli) {
    this.cli = cli;
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
