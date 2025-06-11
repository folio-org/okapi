package org.folio.okapi.util;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.LinkedList;
import java.util.List;

@RunWith(VertxUnitRunner.class)
public class EventBusCheckerTest {
  private Vertx vertx;

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void testBadNode(TestContext context) {
    Async async = context.async();
    List<String> list = new LinkedList<>();
    list.add("a");
    list.add("b");
    EventBusChecker.check(vertx, "a", "a", list)
        .onComplete(context.asyncAssertFailure(cause -> async.complete()));
    async.await();
  }

  @Test
  public void testBadReply(TestContext context) {
    Async async = context.async();
    List<String> list = new LinkedList<>();
    list.add("a");
    EventBusChecker.check(vertx, "a", "r", list)
        .onComplete(context.asyncAssertFailure(cause -> async.complete()));
    async.await();
  }


  @Test
  public void testGoodNode(TestContext context) {
    Async async = context.async();
    List<String> list = new LinkedList<>();
    list.add("a");
    EventBusChecker.check(vertx, "a", "a", list)
        .onComplete(context.asyncAssertSuccess(res -> async.complete()));
    async.await();
  }

}
