package org.folio.okapi.service;

import io.vertx.core.Handler;
import java.util.List;
import java.util.TreeMap;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.common.ExtendedAsyncResult;

/**
 *
 * @author heikki
 */
public interface TenantStore {

  void delete(String id, Handler<ExtendedAsyncResult<Void>> fut);

  void enableModule(String id, String module, Handler<ExtendedAsyncResult<Void>> fut);

  void disableModule(String id, String module, Handler<ExtendedAsyncResult<Void>> fut);

  void updateModules(String id, TreeMap<String, Boolean> enabled, Handler<ExtendedAsyncResult<Void>> fut);

  void get(String id, Handler<ExtendedAsyncResult<Tenant>> fut);

  void insert(Tenant t, Handler<ExtendedAsyncResult<String>> fut);

  void updateDescriptor(TenantDescriptor td, Handler<ExtendedAsyncResult<Void>> fut);

  void listTenants(Handler<ExtendedAsyncResult<List<Tenant>>> fut);
  // TODO - Add list parameters, like which fields, start, and maxrecs

}
