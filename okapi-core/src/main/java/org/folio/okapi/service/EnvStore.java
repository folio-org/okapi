package org.folio.okapi.service;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.List;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.common.ExtendedAsyncResult;

public interface EnvStore {

  Future<Void> add(EnvEntry env);

  Future<Boolean> delete(String id);

  Future<Void> init(boolean reset);

  Future<List<EnvEntry>> getAll();
}
