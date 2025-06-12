package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import java.util.Collections;
import java.util.List;
import org.folio.okapi.bean.TimerDescriptor;
import org.folio.okapi.service.TimerStore;

public class TimerStoreNull implements TimerStore {
  @Override
  public Future<Void> init(boolean reset) {
    return Future.succeededFuture();
  }

  @Override
  public Future<List<TimerDescriptor>> getAll() {
    return Future.succeededFuture(Collections.emptyList());
  }

  @Override
  public Future<Void> put(TimerDescriptor timerDescriptor) {
    return Future.succeededFuture();
  }

  @Override
  public Future<Boolean> delete(String id) {
    return Future.succeededFuture(Boolean.TRUE);
  }
}
