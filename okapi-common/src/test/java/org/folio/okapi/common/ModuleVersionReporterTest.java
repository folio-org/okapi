package org.folio.okapi.common;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModuleVersionReporterTest {

  private ModuleVersionReporter m;

  public ModuleVersionReporterTest() {
  }

  @Before
  public void setUp() {
    m = new ModuleVersionReporter("org.folio.okapi/okapi-common");
  }

  @After
  public void tearDown() {
  }

  @Test
  public void test1() {
    assertNotNull(m);
    assertNull(m.getModule());
    assertNull(m.getVersion());
    assertNotNull(m.getCommitId());
    assertEquals(40, m.getCommitId().length());
    assertNotNull(m.getRemoteOriginUrl());
  }
}
