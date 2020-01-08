package org.folio.okapi.common;

import org.junit.Test;
import static org.junit.Assert.*;

public class ModuleVersionReporterTest {


  @Test
  public void test1() {
    ModuleVersionReporter m = new ModuleVersionReporter("org.folio.okapi.okapi-common");
    assertNotNull(m);
    assertNull(m.getModule());
    assertNull(m.getVersion());
    assertNotNull(m.getCommitId());
    assertEquals(40, m.getCommitId().length());
    assertNotNull(m.getRemoteOriginUrl());
    m.logStart();
  }

  @Test
  public void test2() {
    ModuleVersionReporter m = new ModuleVersionReporter("doesNotExist", "doesNotExist");
    assertNull(m.getModule());
    assertNull(m.getVersion());
    assertNull(m.getCommitId());
    assertNull(m.getRemoteOriginUrl());
  }
}
