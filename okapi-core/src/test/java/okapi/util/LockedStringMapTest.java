/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.util;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.Set;
import okapi.bean.Ports;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.runner.RunWith;

/**
 *
 * @author heikki
 */
@RunWith(VertxUnitRunner.class)
public class LockedStringMapTest {

  public LockedStringMapTest() {
  }

  Vertx vertx;
  Async async;
  LockedStringMap map = new LockedStringMap();

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
  }

  @After
  public void tearDown(TestContext context) {
    async = context.async();
    vertx.close(x -> {
      async.complete();
    });
  }


  @Test
  public void testit(TestContext context) {
    async = context.async();
    map.init(vertx, "FooMap", res -> {
      testadd(context);
    });
  }

  public void testadd(TestContext context) {
    map.add("k1", "k2", "FOOBAR", res -> {
      assertTrue(res.succeeded());
      testgetK12(context);
    });
  }

  private void testgetK12(TestContext context) {
    map.get("k1","k2",res -> {
      assertTrue(res.succeeded());
      assertEquals("FOOBAR", res.result());
      testgetK1(context);
    });
  }

  private void testgetK1(TestContext context) {
    map.get("k1",res -> {
      assertTrue(res.succeeded());
      assertEquals("[FOOBAR]", res.result().toString());
      done(context);
    });
  }

  private void done(TestContext context) {
    async.complete();
  }

}
