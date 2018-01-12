package org.folio.okapi.common;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ErrorTest {
  @Test
  public void testFailure() {
    Failure<Void> f = new Failure<>(ErrorType.NOT_FOUND, "Not found");
    assertEquals(ErrorType.NOT_FOUND, f.getType());
    assertTrue(f.failed());
    assertFalse(f.succeeded());
    assertEquals("Not found", f.cause().getMessage());
    assertEquals(null, f.result());
    assertEquals(404, ErrorType.httpCode(f.getType()));
    assertEquals(400, ErrorType.httpCode(ErrorType.USER));
    assertEquals(404, ErrorType.httpCode(ErrorType.NOT_FOUND));
    assertEquals(403, ErrorType.httpCode(ErrorType.FORBIDDEN));
    assertEquals(500, ErrorType.httpCode(ErrorType.INTERNAL));

    String nullStr = null;
    Failure<Void> g = new Failure<>(ErrorType.NOT_FOUND, nullStr);
    assertNull(g.cause().getMessage());
  }

  @Test
  public void testSuccess() {
    Success<Integer> s = new Success<>(42);
    assertFalse(s.failed());
    assertTrue(s.succeeded());
    assertEquals(42, (int) s.result());
    assertEquals(200, ErrorType.httpCode(s.getType()));

    Success<Void> t = new Success<>();
    assertFalse(s.failed());
    assertTrue(s.succeeded());
    assertEquals(200, ErrorType.httpCode(s.getType()));
  }

  private void func(ErrorType x, Handler<ExtendedAsyncResult<String>> fut) {
    if (x == ErrorType.OK) {
      fut.handle(new Success<>("123"));
    } else {
      Exception e = new IllegalArgumentException("my exception");
      fut.handle(new Failure<>(x, e));
    }
  }

  @Test
  public void testFuncOk() {
    func(ErrorType.OK, res -> {
      assertTrue(res.succeeded());
      assertFalse(res.failed());
      assertEquals(null, res.cause());
      assertEquals("123", res.result());
    });
  }

  @Test
  public void testFuncInternal() {
    func(ErrorType.INTERNAL, res -> {
      assertTrue(res.failed());
      assertNotEquals(null, res.cause());
      assertEquals("my exception", res.cause().getMessage());
    });
  }

  @Test
  public void testConfig() {
    JsonObject conf = new JsonObject();
    final String varName = "foo-bar92304239";

    assertEquals("123", Config.getSysConf(varName, "123", conf));
    assertEquals(null, Config.getSysConf(varName, null, conf));

    conf.put(varName, "124");
    assertEquals("124", Config.getSysConf(varName, "123", conf));

    System.setProperty(varName, "129");
    assertEquals("129", Config.getSysConf(varName, "123", conf));

    System.setProperty(varName, "");
    assertEquals("124", Config.getSysConf(varName, "123", conf));

    System.setProperty(varName, "");
    assertEquals("", Config.getSysConf(varName, "", conf));
  }
}
