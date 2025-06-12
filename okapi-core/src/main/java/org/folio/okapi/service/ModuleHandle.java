package org.folio.okapi.service;

import io.vertx.core.Future;

public interface ModuleHandle {

  Future<Void> start();

  Future<Void> stop();
}
