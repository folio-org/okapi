package org.folio.okapi.service.impl;

import io.vertx.core.Handler;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Success;
import org.folio.okapi.service.TenantStore;

public class TenantStoreNull implements TenantStore {

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    fut.handle(new Success<>());
  }

  @Override
  public void updateModules(String id, SortedMap<String, Boolean> enabled, Handler<ExtendedAsyncResult<Void>> fut) {
    fut.handle(new Success<>());
  }

  @Override
  public void insert(Tenant t, Handler<ExtendedAsyncResult<Void>> fut) {
    fut.handle(new Success<>());
  }

  @Override
  public void updateDescriptor(TenantDescriptor td, Handler<ExtendedAsyncResult<Void>> fut) {
    fut.handle(new Success<>());
  }

  @Override
  public void listTenants(Handler<ExtendedAsyncResult<List<Tenant>>> fut) {
    fut.handle(new Success<>(new LinkedList<>()));
  }

  @Override
  public void init(boolean reset, Handler<ExtendedAsyncResult<Void>> fut) {
    fut.handle(new Success<>());
  }
}
