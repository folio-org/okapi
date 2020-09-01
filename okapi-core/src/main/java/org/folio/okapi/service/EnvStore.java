package org.folio.okapi.service;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.List;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.common.ExtendedAsyncResult;

public interface EnvStore {

  void add(EnvEntry env, Handler<ExtendedAsyncResult<Void>> fut);

  void delete(String id, Handler<ExtendedAsyncResult<Void>> fut);

  Future<Void> init(boolean reset);

  Future<List<EnvEntry>> getAll();
}
