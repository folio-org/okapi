/*
 * Copyright (C) 2016 Index Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okapi.util;

import org.folio.okapi.util.LockedStringMap;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
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
    map.getKeys(res -> {
      assertTrue(res.succeeded());
      assertTrue("[]".equals(res.result().toString()));
      testadd(context);
    });
  }

  public void testadd(TestContext context) {
    map.addOrReplace(false, "k1", "k2", "FOOBAR", res -> {
      assertTrue(res.succeeded());
      testgetK12(context);
    });
  }

  private void testgetK12(TestContext context) {
    //System.out.println("testgetK12");
    map.getString("k1", "k2", res -> {
      assertTrue(res.succeeded());
      assertEquals("FOOBAR", res.result());
      testgetK1(context);
    });
  }

  private void testgetK1(TestContext context) {
    //System.out.println("testgetK1");
    map.getString("k1", res -> {
      assertTrue(res.succeeded());
      assertEquals("[FOOBAR]", res.result().toString());
      addAnother(context);
    });
  }

  public void addAnother(TestContext context) {
    //System.out.println("addAnother");
    map.addOrReplace(false, "k1", "k2.2", "SecondFoo", res -> {
      assertTrue(res.succeeded());
      addSecondK1(context);
    });
  }

  public void addSecondK1(TestContext context) {
    //System.out.println("Adding second K1");
    map.addOrReplace(false, "k1.1", "x", "SecondKey", res -> {
      assertTrue(res.succeeded());
      testgetK1Again(context);
    });
  }

  private void testgetK1Again(TestContext context) {
    map.getString("k1", res -> {
      assertTrue(res.succeeded());
      //System.out.println("K1Again: '"+res.result().toString()+"'");
      assertEquals("[FOOBAR, SecondFoo]", res.result().toString());
      listKeys(context);
    });
  }

  public void listKeys(TestContext context) {
    map.getKeys(res -> {
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
