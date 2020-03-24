package org.folio.okapi.service;

import io.vertx.core.Handler;
import java.util.List;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.common.ExtendedAsyncResult;

public interface EnvStore {

  void add(EnvEntry env, Handler<ExtendedAsyncResult<Void>> fut);

  void delete(String id, Handler<ExtendedAsyncResult<Void>> fut);

  void init(boolean reset, Handler<ExtendedAsyncResult<Void>> fut);

  void getAll(Handler<ExtendedAsyncResult<List<EnvEntry>>> fut);
}
