package org.folio.okapi.service;

import io.vertx.core.Future;
import java.util.List;
import org.folio.okapi.bean.ModuleDescriptor;

public interface ModuleStore {

  Future<Boolean> delete(String id);

  Future<List<ModuleDescriptor>> getAll();

  Future<Void> insert(ModuleDescriptor md);

  Future<Void> init(boolean reset);
}
