package org.folio.okapi.service;

import io.vertx.core.Future;

public interface Liveness {
  Future<Void> isAlive();
}
