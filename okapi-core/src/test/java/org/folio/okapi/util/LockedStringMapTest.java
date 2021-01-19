package org.folio.okapi.util;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.okapi.common.OkapiLogger;
import org.junit.After;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.util.LinkedList;
import java.util.List;

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
  public void test1(TestContext context) {
    {
      Async async = context.async();
      map.init(vertx, "FooMap", true).onComplete(context.asyncAssertSuccess(x -> async.complete()));
      async.await();
    }
    {
      Async async = context.async();
      map.getKeys().onComplete(res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("[]", res.result().toString());
        async.complete();
      });
      async.await();
    }

    {
      Async async = context.async();
      map.addOrReplace(false, "k1", null, "v1").onComplete(res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.addOrReplace(false, "k1", null, "v2").onComplete(res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }

    {
      Async async = context.async();
      map.getString("k1", null).onComplete(res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }

  }

  @Test
  public void test2(TestContext context) {
    {
      Async async = context.async();
      map.init(vertx, "FooMap", false).onComplete(context.asyncAssertSuccess(x -> async.complete()));
      async.await();
    }
    {
      Async async = context.async();
      map.getKeys().onComplete(res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("[]", res.result().toString());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.size().onComplete(res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals(0, res.result());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.addOrReplace(false, "k1", "k2", "FOOBAR").onComplete(res -> {
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
      map.addOrReplace(true, "k1", "k2", "FOOBAR").onComplete(res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }

    {
      Async async = context.async();
      map.addOrReplace(false, "k1", "k2", "FOOBAR").onComplete(res -> {
        context.assertFalse(res.succeeded());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.getPrefix("k1").onComplete(res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("[FOOBAR]", res.result().toString());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.addOrReplace(false, "k1", "k2.2", "SecondFoo").onComplete(res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.size().onComplete(res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals(1, res.result());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.addOrReplace(false, "k1.1", "x", "SecondKey").onComplete(res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.size().onComplete(res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals(2, res.result());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.getPrefix("k1").onComplete(res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("[FOOBAR, SecondFoo]", res.result().toString());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.getPrefix("i").onComplete(res -> {
        context.assertTrue(res.succeeded());
        context.assertNull(res.result());
        async.complete();
      });
      async.await();
    }

    {
      Async async = context.async();
      map.getKeys().onComplete(res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("[k1, k1.1]", res.result().toString());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.removeNotFound("k1", "k2").onComplete(res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.removeNotFound("k1", "k2").onComplete(res -> {
        context.assertFalse(res.succeeded());
        context.assertEquals(ErrorType.NOT_FOUND, OkapiError.getType(res.cause()));
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.getKeys().onComplete(res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("[k1, k1.1]", res.result().toString());
        async.complete();
      });
      async.await();
    }

    {
      Async async = context.async();
      map.remove("k1", "k2.2").onComplete(res -> {
        context.assertTrue(res.succeeded());
        context.assertTrue(res.result()); // no keys left
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.getKeys().onComplete(res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("[k1.1]", res.result().toString());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.remove("k1.1", "x").onComplete(res -> {
        context.assertTrue(res.succeeded());
        context.assertTrue(res.result());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.removeNotFound("k", "x").onComplete(res -> {
        context.assertTrue(res.failed());
        context.assertEquals(ErrorType.NOT_FOUND, OkapiError.getType(res.cause()));
        context.assertEquals("k/x", res.cause().getMessage());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      map.getKeys().onComplete(res -> {
        context.assertTrue(res.succeeded());
        context.assertEquals("[]", res.result().toString());
        async.complete();
      });
      async.await();
    }

    {
      Async async = context.async();
      map.remove("k").onComplete(res -> {
        context.assertTrue(res.succeeded());
        context.assertFalse(res.result());
        async.complete();
      });
      async.await();
    }

    {
      Async async = context.async();
      map.removeNotFound("k").onComplete(res -> {
        context.assertTrue(res.failed());
        context.assertEquals(ErrorType.NOT_FOUND, OkapiError.getType(res.cause()));
        async.complete();
      });
      async.await();
    }

  }

  @Test
  public void testConcurrent(TestContext context) {
    {
      Async async = context.async();
      map.init(vertx, "FooMap", true).onComplete(context.asyncAssertSuccess(x -> async.complete()));
      async.await();
    }
    {
      List<Future<Void>> futures = new LinkedList<>();
      for (int i = 0; i < 10; i++) {
        futures.add(map.addOrReplace(true,"k", "l", Integer.toString(i)));
      }
      Async async = context.async();
      GenericCompositeFuture.all(futures).onComplete(context.asyncAssertSuccess(x -> async.complete()));
      async.await();
    }
    {
      List<Future<Void>> futures = new LinkedList<>();
      for (int i = 0; i < 10; i++) {
        futures.add(map.addOrReplace(true,"k", Integer.toString(i), Integer.toString(i)));
      }
      Async async = context.async();
      GenericCompositeFuture.all(futures).onComplete(context.asyncAssertSuccess(x -> async.complete()));
      async.await();
    }

    {
      List<Future<Boolean>> futures = new LinkedList<>();
      for (int i = 0; i < 10; i++) {
        futures.add(map.remove("k", "l"));
        futures.add(map.remove("k", Integer.toString(i)));
      }
      Async async = context.async();
      GenericCompositeFuture.all(futures).onComplete(context.asyncAssertSuccess(x -> async.complete()));
      async.await();
    }
  }
}
