package org.folio.okapi.util;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class AsyncLockTest {

  private Vertx vertx;

  @Before
  public void setUp() {
    vertx = Vertx.vertx();
  }

  @Test
  public void test1(TestContext context) {
    Async async = context.async();
    AsyncLock.pollTime = 1; // to getLockR called again (<< 10)
    AsyncLock lock1 = new AsyncLock(vertx);
    AsyncLock lock2 = new AsyncLock(vertx);
    lock1.getLock("l", res -> {
      context.assertTrue(res.succeeded());
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      async.complete();
    });
    lock2.getLock("l", res -> {
      async.complete();
    });
    async.awaitSuccess(100);
  }

  @Test
  public void test2(TestContext context) {
    Async async = context.async();
    AsyncLock.pollTime = 1; // to getLockR called again (<< 10)
    AsyncLock lock = new AsyncLock(vertx);
    lock.isCluster = true;
    
    lock.getLock("x y", res -> {
      context.assertTrue(res.succeeded());
      async.complete();
    });
    async.awaitSuccess(100);
  }

}
