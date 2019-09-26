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
  public void testLocal(TestContext context) {
    Async async = context.async();

    AsyncLock lock1 = new AsyncLock(vertx);
    AsyncLock lock2 = new AsyncLock(vertx);
    lock1.pollTime = lock2.pollTime = 1;  // to getLockR called again (<< 5 ms)
    lock1.getLock("l", res -> {
      context.assertTrue(res.succeeded());
      vertx.setTimer(5, x -> async.complete()); // hold lock for 5 ms
    });
    lock2.getLock("l", res -> { });
    async.awaitSuccess(100);
  }

  @Test
  public void testCluster(TestContext context) {
    Async async = context.async();
    AsyncLock lock = new AsyncLock(vertx);
    lock.isCluster = true;
    
    lock.getLock("l", res -> {
      context.assertTrue(res.succeeded());
      async.complete();
    });
    async.awaitSuccess(20);
  }

}
