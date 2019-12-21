package org.folio.okapi.managers;

import io.vertx.core.Handler;
import java.util.List;
import java.util.SortedMap;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.service.TenantStore;

public class TenantStoreFaulty implements TenantStore {
  final ErrorType code;
  final String msg;
  
  public TenantStoreFaulty(ErrorType type, String msg) {
    this.code = type;
    this.msg = msg;
  }
  
  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    fut.handle(new Failure<>(code, msg));
  }

  @Override
  public void updateModules(String id, SortedMap<String, Boolean> enabled, Handler<ExtendedAsyncResult<Void>> fut) {
    fut.handle(new Failure<>(code, msg));
  }

  @Override
  public void insert(Tenant t, Handler<ExtendedAsyncResult<Void>> fut) {
    fut.handle(new Failure<>(code, msg));
  }

  @Override
  public void updateDescriptor(TenantDescriptor td, Handler<ExtendedAsyncResult<Void>> fut) {
    fut.handle(new Failure<>(code, msg));
  }

  @Override
  public void listTenants(Handler<ExtendedAsyncResult<List<Tenant>>> fut) {
    fut.handle(new Failure<>(code, msg));
  }

  @Override
  public void init(boolean reset, Handler<ExtendedAsyncResult<Void>> fut) {
    fut.handle(new Failure<>(code, msg));
  }
  
}
