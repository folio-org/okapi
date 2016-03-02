/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.service;

import io.vertx.core.Handler;
import java.util.List;
import okapi.bean.ModuleDescriptor;
import okapi.util.ExtendedAsyncResult;

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

  void listIds(Handler<ExtendedAsyncResult<List<String>>> fut);

}
