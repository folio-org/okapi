package org.folio.okapi.common;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.folio.okapi.common.logging.FolioLocal;
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

  private static final String KEY = "requestId";
  private static final String VALUE = "VALUE";
  private static final String EMPTY_STRING = "";

  private Vertx vertx;

  @Before
  public void setup() {
    new FolioLocal().init(null);
    vertx = Vertx.vertx();
  }

  @After
  public void tearDown() {
    vertx.close();
  }

  @Test
  public void putWithoutContextTest(TestContext context) {
    FolioLoggingContext loggingContext = new FolioLoggingContext();
    FolioLoggingContext.put(KEY, VALUE);
    context.assertEquals(EMPTY_STRING, loggingContext.lookup(KEY));
  }


  @Test
  public void lookupWithoutContextTest(TestContext context) {
    FolioLoggingContext loggingContext = new FolioLoggingContext();
    context.assertEquals(EMPTY_STRING, loggingContext.lookup(KEY));
  }


  @Test
  public void lookupPutTest(TestContext context) {
    Async async = context.async();
    vertx.runOnContext(e -> {
      FolioLoggingContext loggingContext = new FolioLoggingContext();
      FolioLoggingContext.put(KEY, VALUE);
      vertx.runOnContext(c -> {
        context.assertEquals(VALUE, loggingContext.lookup(KEY));
        context.assertEquals(VALUE, loggingContext.lookup(null, KEY));
            async.complete();
          }
      );
    });
  }

  @Test
  public void lookupNullTest(TestContext context) {
    Async async = context.async();
    vertx.runOnContext(x -> context.verify(y -> {
      context.assertEquals("", new FolioLoggingContext().lookup(null));
      async.complete();
    }));
  }

  @Test
  public void lookupUnknownTest(TestContext context) {
    Async async = context.async();
    vertx.runOnContext(x -> context.verify(y -> {
      var folioLoggingContext = new FolioLoggingContext();
      var e = assertThrows(IllegalArgumentException.class, () -> folioLoggingContext.lookup("foo"));
      assertThat(e.getMessage(),
          allOf(startsWith("key expected to be one of ["), endsWith("] but was: foo")));
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
          context.assertEquals(EMPTY_STRING, loggingContext.lookup(KEY));
          async.complete();
        }
    );
  }

}
