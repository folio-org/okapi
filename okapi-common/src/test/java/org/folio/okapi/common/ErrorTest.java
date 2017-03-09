package org.folio.okapi.common;
import org.junit.Test;
import static org.junit.Assert.*;

public class ErrorTest {
  @Test
  public void testFailure() {
    Failure f = new Failure<>(ErrorType.NOT_FOUND, "Not found");
    assertEquals(f.getType(), ErrorType.NOT_FOUND);
    assertTrue(f.failed());
    assertFalse(f.succeeded());
    assertEquals("Not found", f.cause().getMessage());
  }

  @Test
  public void testSuccess() {
    Success<Integer> s = new Success<>(42);
    assertFalse(s.failed());
    assertTrue(s.succeeded());
    assertEquals(42, s.result().intValue());
  }
}
