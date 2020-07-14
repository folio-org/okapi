package org.folio.okapi.common;

import org.folio.okapi.common.logging.FolioLoggingContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class FolioLoggingContextTest {


  private static String KEY = "KEY";
  private static String VALUE = "VALUE";

  private Vertx vertx;

  @Before
  public void setup() {
    vertx = Vertx.vertx();
  }

  @After
  public void tearDown() {
    vertx.close();
  }

  @Test
  public void lookupWithoutContextTest() {
    FolioLoggingContext loggingContext = new FolioLoggingContext();
    loggingContext.lookup(null);
  }


  @Test
  public void lookupPutTest(TestContext context) {
    vertx.runOnContext(e -> {
      FolioLoggingContext loggingContext = new FolioLoggingContext();
      FolioLoggingContext.put(KEY, VALUE);
      context.assertEquals(loggingContext.lookup(KEY), VALUE);
    });
  }

  @Test
  public void lookupNullTest(TestContext context) {
    FolioLoggingContext loggingContext = new FolioLoggingContext();
    vertx.runOnContext(e -> {
      loggingContext.lookup(null);
    });
  }

  @Test
  public void putNullTest(TestContext context) {
    Async async = context.async();
    FolioLoggingContext loggingContext = new FolioLoggingContext();
    vertx.runOnContext(e -> {
          FolioLoggingContext.put(KEY, null);
          context.assertEquals(loggingContext.lookup(KEY), "");
          async.complete();
        }
    );
  }

}
