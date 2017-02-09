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
    map.getString("k1", "k2", res -> {
      assertTrue(res.succeeded());
      assertEquals("FOOBAR", res.result());
      testgetK1(context);
    });
  }

  private void testgetK1(TestContext context) {
    map.getString("k1", res -> {
      assertTrue(res.succeeded());
      assertEquals("[FOOBAR]", res.result().toString());
      addAnother(context);
    });
  }

  public void addAnother(TestContext context) {
    map.addOrReplace(false, "k1", "k2.2", "SecondFoo", res -> {
      assertTrue(res.succeeded());
      addSecondK1(context);
    });
  }

  public void addSecondK1(TestContext context) {
    map.addOrReplace(false, "k1.1", "x", "SecondKey", res -> {
      assertTrue(res.succeeded());
      testgetK1Again(context);
    });
  }

  private void testgetK1Again(TestContext context) {
    map.getString("k1", res -> {
      assertTrue(res.succeeded());
      assertEquals("[FOOBAR, SecondFoo]", res.result().toString());
      listKeys(context);
    });
  }

  public void listKeys(TestContext context) {
    map.getKeys(res -> {
      assertTrue(res.succeeded());
      assertTrue("[k1, k1.1]".equals(res.result().toString()));
      deleteKey1(context);
    });
  }

  private void deleteKey1(TestContext context) {
    map.remove("k1", "k2", res -> {
      assertTrue(res.succeeded());
      assertFalse(res.result()); // there is still k1/k2.2 left
      listKeys1(context);
    });
  }

  private void listKeys1(TestContext context) {
    map.getKeys(res -> {
      assertTrue(res.succeeded());
      assertTrue("[k1, k1.1]".equals(res.result().toString()));
      deleteKey2(context);
    });
  }

  private void deleteKey2(TestContext context) {
    map.remove("k1", "k2.2", res -> {
      assertTrue(res.succeeded());
      assertTrue(res.result()); // no keys left
      testgetk1(context);
    });
  }

  private void testgetk1(TestContext context) {
    map.getString("k1", res -> {
      assertTrue(res.succeeded());
      assertEquals("[]", res.result().toString());
      listKeys2(context);
    });
  }

  private void listKeys2(TestContext context) {
    map.getKeys(res -> {
      assertTrue(res.succeeded());
      assertTrue("[k1.1]".equals(res.result().toString()));
      done(context);
    });
  }

  private void done(TestContext context) {
    System.out.println("OK");
    async.complete();
  }

}
