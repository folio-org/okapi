package org.folio.okapi.managers;

import io.vertx.core.Future;
import java.util.List;
import java.util.SortedMap;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.service.TenantStore;

public class TenantStoreFaulty implements TenantStore {
  final ErrorType code;
  final String msg;
  
  public TenantStoreFaulty(ErrorType type, String msg) {
    this.code = type;
    this.msg = msg;
  }
  
  @Override
  public Future<Boolean> delete(String id) {
    return Future.failedFuture(msg);
  }

  @Override
  public Future<Boolean> updateModules(String id, SortedMap<String, Boolean> enabled) {
    return Future.failedFuture(msg);
  }

  @Override
  public Future<Void> insert(Tenant t) {
    return Future.failedFuture(msg);
  }

  @Override
  public Future<Void> updateDescriptor(TenantDescriptor td) {
    return Future.failedFuture(msg);
  }

  @Override
  public Future<List<Tenant>> listTenants() {
    return Future.failedFuture(msg);
  }

  @Override
  public Future<Void> init(boolean reset) {
    return Future.failedFuture(msg);
  }
  
}
