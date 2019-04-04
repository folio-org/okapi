package org.folio.okapi.common;
import org.junit.Test;
import static org.junit.Assert.*;

public class URLDecoderTest {
  
  @Test
  public void t1() {
  assertEquals("a/b", URLDecoder.decode("a%2Fb"));
  assertEquals("1-2", URLDecoder.decode("1-2"));
  assertEquals("1 2", URLDecoder.decode("1+2"));
  assertEquals("1 2", URLDecoder.decode("1+2", true));
  assertEquals("1+2", URLDecoder.decode("1+2", false));
  assertEquals("1+2", URLDecoder.decode("1+2", false));
  }
  
}
