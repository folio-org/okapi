package org.folio.okapi.common.logging;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;

class FolioLocalTest {

  @Test
  void newVertxCallsInit() {
    FolioLocal.initialized = false;
    Vertx.vertx();
    assertThat(FolioLocal.initialized, is(true));
  }
}
