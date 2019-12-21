package org.folio.okapi.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class LogHelperTest {
  @Test
  public void test() {
    String lev1 = LogHelper.getRootLogLevel();
    LogHelper.setRootLogLevel("DEBUG");
    String lev2 = LogHelper.getRootLogLevel();
    assertEquals("DEBUG", lev2);
    LogHelper.setRootLogLevel(lev1);
  }
}
