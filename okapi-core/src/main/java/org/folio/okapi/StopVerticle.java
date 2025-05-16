package org.folio.okapi;

import io.vertx.core.impl.NoStackTraceException;

public class StopVerticle extends NoStackTraceException {
  StopVerticle(String msg) {
    super(msg);
  }
}
