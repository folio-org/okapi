package org.folio.okapi.common;

import org.folio.okapi.common.logging.FolioLoggingContext;
import org.junit.After;
import org.junit.Assert;
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
  public void lookupWithoutContextTest(TestContext context) {
    FolioLoggingContext loggingContext = new FolioLoggingContext();
    context.assertEquals(null, loggingContext.lookup(KEY));
  }


  @Test
  public void lookupPutTest(TestContext context) {
    Async async = context.async();
    vertx.runOnContext(e -> {
      FolioLoggingContext loggingContext = new FolioLoggingContext();
      FolioLoggingContext.put(KEY, VALUE);
      vertx.runOnContext(c -> {
            context.assertEquals(VALUE, loggingContext.lookup(KEY));
            async.complete();
          }
      );
    });
  }

  @Test
  public void lookupNullTest(TestContext context) {
    Async async = context.async();
    FolioLoggingContext loggingContext = new FolioLoggingContext();
    vertx.runOnContext(run -> context.verify(block -> {
      Assert.assertThrows(IllegalArgumentException.class, () -> loggingContext.lookup(null));
      async.complete();
    }));
  }

  @Test
  public void putNullTest(TestContext context) {
    Async async = context.async();
    FolioLoggingContext loggingContext = new FolioLoggingContext();
    vertx.runOnContext(e -> {
          FolioLoggingContext.put(KEY, VALUE);
          FolioLoggingContext.put(KEY, null);
          context.assertEquals("", loggingContext.lookup(KEY));
          async.complete();
        }
    );
  }

}
