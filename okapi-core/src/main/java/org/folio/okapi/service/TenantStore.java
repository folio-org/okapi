package org.folio.okapi.service;

import io.vertx.core.Handler;
import java.util.List;
import java.util.SortedMap;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.common.ExtendedAsyncResult;

public interface TenantStore {

  void delete(String id, Handler<ExtendedAsyncResult<Void>> fut);

  void updateModules(String id, SortedMap<String, Boolean> enabled, Handler<ExtendedAsyncResult<Void>> fut);

  void insert(Tenant t, Handler<ExtendedAsyncResult<String>> fut);

  void updateDescriptor(TenantDescriptor td, Handler<ExtendedAsyncResult<Void>> fut);

  void listTenants(Handler<ExtendedAsyncResult<List<Tenant>>> fut);

}
