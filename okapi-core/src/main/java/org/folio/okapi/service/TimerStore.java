package org.folio.okapi.service;

import io.vertx.core.Future;
import java.util.List;
import org.folio.okapi.bean.TimerDescriptor;

public interface TimerStore {

  Future<Void> init(boolean reset);

  Future<List<TimerDescriptor>> getAll();

  Future<Void> put(TimerDescriptor timerDescriptor);

  Future<Boolean> delete(String id);
}
