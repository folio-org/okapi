/*
 * Copyright (c) 2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.service;

import io.vertx.core.Handler;
import java.util.List;
import okapi.bean.Tenant;
import okapi.bean.TenantDescriptor;
import okapi.util.ExtendedAsyncResult;

/**
 *
 * @author heikki
 */
public interface TenantStore {

  void delete(String id, Handler<ExtendedAsyncResult<Void>> fut);

  void enableModule(String id, String module, long timestamp, Handler<ExtendedAsyncResult<Void>> fut);

  void disableModule(String id, String module, long timestamp, Handler<ExtendedAsyncResult<Void>> fut);

  void get(String id, Handler<ExtendedAsyncResult<Tenant>> fut);

  void insert(Tenant t, Handler<ExtendedAsyncResult<String>> fut);

  void update(Tenant t, Handler<ExtendedAsyncResult<String>> fut);

  void updateDescriptor(String id, TenantDescriptor td, Handler<ExtendedAsyncResult<Void>> fut);

  void listIds(Handler<ExtendedAsyncResult<List<String>>> fut);

  void listTenants(Handler<ExtendedAsyncResult<List<Tenant>>> fut);
  // TODO - Add list parameters, like which fields, start, and maxrecs

}
