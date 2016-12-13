package org.folio.okapi.service;

import io.vertx.core.Handler;
import java.util.List;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.common.ExtendedAsyncResult;

/**
 *
 *
 */
public interface ModuleStore {

  void delete(String id, Handler<ExtendedAsyncResult<Void>> fut);

  void get(String id, Handler<ExtendedAsyncResult<ModuleDescriptor>> fut);

  void getAll(Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut);

  void insert(ModuleDescriptor md, Handler<ExtendedAsyncResult<String>> fut);

  void update(ModuleDescriptor md, Handler<ExtendedAsyncResult<String>> fut);
}
