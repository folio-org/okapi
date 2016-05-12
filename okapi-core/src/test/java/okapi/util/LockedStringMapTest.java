/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.util;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
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
  private final Logger logger = LoggerFactory.getLogger("okapi");

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
      listEmpty(context);
    });
  }

  public void listEmpty(TestContext context) {
    map.getKeys(res->{
      assertTrue(res.succeeded());
      assertTrue("[]".equals(res.result().toString()));
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
    //System.out.println("testgetK12");
    map.get("k1","k2",res -> {
      assertTrue(res.succeeded());
      assertEquals("FOOBAR", res.result());
      testgetK1(context);
    });
  }

  private void testgetK1(TestContext context) {
    //System.out.println("testgetK1");
    map.get("k1",res -> {
      assertTrue(res.succeeded());
      assertEquals("[FOOBAR]", res.result().toString());
      addAnother(context);
    });
  }

  public void addAnother(TestContext context) {
    //System.out.println("addAnother");
    map.add("k1", "k2.2", "SecondFoo", res -> {
      assertTrue(res.succeeded());
      addSecondK1(context);
    });
  }

  public void addSecondK1(TestContext context) {
    //System.out.println("Adding second K1");
    map.add("k1.1", "x", "SecondKey", res -> {
      assertTrue(res.succeeded());
      testgetK1Again(context);
    });
  }
  private void testgetK1Again(TestContext context) {
    map.get("k1",res -> {
      assertTrue(res.succeeded());
      //System.out.println("K1Again: '"+res.result().toString()+"'");
      assertEquals("[FOOBAR, SecondFoo]", res.result().toString());
      listKeys(context);
    });
  }

  public void listKeys(TestContext context) {
    map.getKeys(res->{
      assertTrue(res.succeeded());
      //System.out.println("Got keys: '" +res.result().toString() + "'" );
      assertTrue("[k1, k1.1]".equals(res.result().toString()));
      done(context);
    });
  }


  private void done(TestContext context) {
    System.out.println("OK");
    async.complete();
  }

}
