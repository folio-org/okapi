package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.service.TenantStore;

public class TenantStoreNull implements TenantStore {

  @Override
  public Future<Boolean> delete(String id) {
    return Future.succeededFuture(Boolean.TRUE);
  }

  @Override
  public Future<Boolean> updateModules(String id, SortedMap<String, Boolean> enabled) {
    return Future.succeededFuture(Boolean.TRUE);
  }

  @Override
  public Future<Void> insert(Tenant t) {
    return Future.succeededFuture();
  }

  @Override
  public Future<Void> updateDescriptor(TenantDescriptor td) {
    return Future.succeededFuture();
  }

  @Override
  public Future<List<Tenant>> listTenants() {
    return Future.succeededFuture(new LinkedList<>());
  }

  @Override
  public Future<Void> init(boolean reset) {
    return Future.succeededFuture();
  }
}
