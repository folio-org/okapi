package org.folio.okapi.service;

import java.util.List;

import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.common.ExtendedAsyncResult;

import io.vertx.core.Handler;

public interface ModuleStore {

  void delete(String id, Handler<ExtendedAsyncResult<Void>> fut);

  void getAll(Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut);

  void insert(ModuleDescriptor md, Handler<ExtendedAsyncResult<Void>> fut);

  void update(ModuleDescriptor md, Handler<ExtendedAsyncResult<Void>> fut);

  void init(boolean reset, Handler<ExtendedAsyncResult<Void>> fut);
}
