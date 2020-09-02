package org.folio.okapi.util;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.OkapiLogger;
import org.junit.After;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class LockedStringMapTest {

  private final Logger logger = OkapiLogger.get();

  private Vertx vertx;
  private LockedStringMap map = new LockedStringMap();

  @Before
  public void setUp(TestContext context) {
    logger.debug("starting LockedStringMapTest");
    vertx = Vertx.vertx();
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void test(TestContext context) {
    {
      Async async = context.async();
      map.init(vertx, "FooMap").onComplete(context.asyncAssertSuccess(x -> async.complete()));
      async.await();
    }
    {
      Async async = context.async();
      map.getKeys(res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("[]", res.result().toString());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.addOrReplace(false, "k1", "k2", "FOOBAR", res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.getString("k1", "k2").onComplete(res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("FOOBAR", res.result());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.getString("k1", "k3").onComplete(res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals(null, res.result());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.getString("foo", "k2").onComplete(res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals(null, res.result());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.addOrReplace(true, "k1", "k2", "FOOBAR", res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }

    {
      Async async = context.async();
      map.addOrReplace(false, "k1", "k2", "FOOBAR", res -> {
        context.assertFalse(res.succeeded());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.getPrefix("k1", res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("[FOOBAR]", res.result().toString());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.addOrReplace(false, "k1", "k2.2", "SecondFoo", res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.addOrReplace(false, "k1.1", "x", "SecondKey", res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.getPrefix("k1", res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("[FOOBAR, SecondFoo]", res.result().toString());
        async.complete();
      });
      async.await();
    }

    {
      Async async = context.async();
      map.getKeys(res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("[k1, k1.1]", res.result().toString());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.remove("k1", "k2", res -> {
        context.assertTrue(res.succeeded());
        context.assertTrue(res.result()); // k1, k2 deleted ok
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.remove("k1", "k2", res -> {
        context.assertFalse(res.succeeded());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.getKeys(res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("[k1, k1.1]", res.result().toString());
        async.complete();
      });
      async.await();
    }

    {
      Async async = context.async();
      map.remove("k1", "k2.2", res -> {
        context.assertTrue(res.succeeded());
        context.assertTrue(res.result()); // no keys left
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.getKeys(res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("[k1.1]", res.result().toString());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.remove("k1.1", "x", res -> {
        context.assertTrue(res.succeeded());
        context.assertTrue(res.result());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.getKeys(res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("[]", res.result().toString());
        async.complete();
      });
      async.await();
    }
  }


}
