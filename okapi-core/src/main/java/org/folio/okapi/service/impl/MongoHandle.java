package org.folio.okapi.service.impl;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import java.util.Iterator;
import java.util.List;
import org.folio.okapi.common.Config;
import static org.folio.okapi.common.ErrorType.INTERNAL;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

/**
 * Generic handle to the Mongo database. Encapsulates the configuration and
 * creation of Mongo client that can be passed on to other Mongo-based storage
 * modules.
 */
public class MongoHandle {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final MongoClient cli;

  // Little helper to get a config value:
  // First from System (-D on command line),
  // then from config (from the way the verticle gets deployed, e.g. in tests)
  // finally a default value.
  public MongoHandle(Vertx vertx, JsonObject conf) {
    JsonObject opt = new JsonObject();
    String h = Config.getSysConf("mongo_host", "127.0.0.1", conf);
    if (!h.isEmpty()) {
      opt.put("host", h);
    }
    String p = Config.getSysConf("mongo_port", "27017", conf);
    if (!p.isEmpty()) {
      opt.put("port", Integer.parseInt(p));
    }
    String db_name = Config.getSysConf("mongo_db_name", "", conf);
    if (!db_name.isEmpty()) {
      opt.put("db_name", db_name);
    }
    logger.info("Using mongo backend at " + h + " : " + p + " / " + db_name);
    this.cli = MongoClient.createShared(vertx, opt);
  }

  public MongoClient getClient() {
    return cli;
  }

  public void resetDatabases(Handler<ExtendedAsyncResult<Void>> fut) {
    dropDatabase(res -> {
      if (res.succeeded()) {
        fut.handle(new Success<>());
      } else {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      }
    });
  }
  /**
   * Drop all (relevant?) collections. The idea is that we can start our
   * integration tests on a clean slate.
   *
   */
  public void dropDatabase(Handler<ExtendedAsyncResult<Void>> fut) {
    cli.getCollections(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        List<String> collections = res.result();
        Iterator<String> it = collections.iterator();
        dropCollection(it, fut);
      }
    });
  }

  private void dropCollection(Iterator<String> it,
          Handler<ExtendedAsyncResult<Void>> fut) {
    if (!it.hasNext()) { // all done
      logger.info("Dropped all okapi collections");
      fut.handle(new Success<>());
    } else {
      String coll = it.next();
      if (coll.startsWith("okapi")) {
        cli.dropCollection(coll, res -> {
          if (res.failed()) {
            fut.handle(new Failure<>(INTERNAL, res.cause()));
          } else {
            logger.debug("Dropped whole collection " + coll);
            dropCollection(it, fut);
          }
        });
      } else {
        logger.debug("Not dropping collection '" + coll + "'");
        dropCollection(it, fut);
      }
    }
  }

}
