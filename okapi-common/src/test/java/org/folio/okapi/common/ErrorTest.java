package org.folio.okapi.common;
import io.vertx.core.Handler;
import org.junit.Test;
import org.junit.Assert;

public class ErrorTest {
  @Test
  public void testFailure() {
    Failure<Void> f = new Failure<>(ErrorType.NOT_FOUND, "Not found");
    Assert.assertEquals(ErrorType.NOT_FOUND, f.getType());
    Assert.assertTrue(f.failed());
    Assert.assertFalse(f.succeeded());
    Assert.assertEquals("Not found", f.cause().getMessage());
    Assert.assertEquals(null, f.result());
    Assert.assertEquals(404, ErrorType.httpCode(f.getType()));
    Assert.assertEquals(400, ErrorType.httpCode(ErrorType.USER));
    Assert.assertEquals(404, ErrorType.httpCode(ErrorType.NOT_FOUND));
    Assert.assertEquals(403, ErrorType.httpCode(ErrorType.FORBIDDEN));
    Assert.assertEquals(500, ErrorType.httpCode(ErrorType.INTERNAL));

    String nullStr = null;
    Failure<Void> g = new Failure<>(ErrorType.NOT_FOUND, nullStr);
    Assert.assertNull(g.cause().getMessage());
  }

  @Test
  public void testSuccess() {
    Success<Integer> s = new Success<>(42);
    Assert.assertFalse(s.failed());
    Assert.assertTrue(s.succeeded());
    Assert.assertEquals(42, (int) s.result());
    Assert.assertEquals(200, ErrorType.httpCode(s.getType()));

    Success<Void> t = new Success<>();
    Assert.assertFalse(s.failed());
    Assert.assertTrue(s.succeeded());
    Assert.assertEquals(200, ErrorType.httpCode(s.getType()));
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
      Assert.assertTrue(res.succeeded());
      Assert.assertFalse(res.failed());
      Assert.assertEquals(null, res.cause());
      Assert.assertEquals("123", res.result());
    });
  }

  @Test
  public void testFuncInternal() {
    func(ErrorType.INTERNAL, res -> {
      Assert.assertTrue(res.failed());
      Assert.assertNotEquals(null, res.cause());
      Assert.assertEquals("my exception", res.cause().getMessage());
    });
  }

}
