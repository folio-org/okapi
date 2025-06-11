package org.folio.okapi.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.folio.okapi.bean.TimerDescriptor;
import org.folio.okapi.service.TimerStore;

import io.vertx.core.Future;

public class TimerStoreMemory implements TimerStore {

  private Map<String, TimerDescriptor> map = new HashMap<>();

  public TimerStoreMemory() {
  }

  public TimerStoreMemory(TimerDescriptor timerDescriptor) {
    put(timerDescriptor);
  }

  @Override
  public Future<Void> init(boolean reset) {
    if (reset) {
      map.clear();
    }
    return Future.succeededFuture();
  }

  @Override
  public Future<List<TimerDescriptor>> getAll() {
    var list = new ArrayList<>(map.values());
    return Future.succeededFuture(list);
  }

  @Override
  public Future<Void> put(TimerDescriptor timerDescriptor) {
    map.put(timerDescriptor.getId(), timerDescriptor);
    return Future.succeededFuture();
  }

  @Override
  public Future<Boolean> delete(String id) {
    var timerDescriptor = map.remove(id);
    return Future.succeededFuture(timerDescriptor != null);
  }

}
