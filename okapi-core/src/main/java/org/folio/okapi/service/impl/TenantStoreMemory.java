package org.folio.okapi.service.impl;

import org.folio.okapi.service.TenantStore;
import io.vertx.core.Handler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

/**
 * Mock storage for tenants. All in memory, so it starts with a clean slate
 * every time the program starts.
 *
 */
public class TenantStoreMemory implements TenantStore {

  Map<String, Tenant> tenants = new HashMap<>();

  public TenantStoreMemory() {
  }

  @Override
  public void insert(Tenant t,
          Handler<ExtendedAsyncResult<String>> fut) {
    String id = t.getId();
    tenants.put(id, new Tenant(t));
    fut.handle(new Success<>(id));
  }

  @Override
  public void updateDescriptor(TenantDescriptor td, Handler<ExtendedAsyncResult<Void>> fut) {
    final String id = td.getId();
    Tenant t;
    if (!tenants.containsKey(id)) {
      t = new Tenant(td);
    } else {
      Tenant oldT = tenants.get(id);
      t = new Tenant(td, oldT.getEnabled());
    }
    // TODO - Validate that we don't change the id
    tenants.put(id, t);
    fut.handle(new Success<>());
  }

  @Override
  public void listTenants(Handler<ExtendedAsyncResult<List<Tenant>>> fut) {
    List<Tenant> tl = new ArrayList<>();
    for (String id : tenants.keySet()) {
      Tenant t = tenants.get(id);
      tl.add(t);
    }
    fut.handle(new Success<>(tl));
  }

  @Override
  public void get(String id, Handler<ExtendedAsyncResult<Tenant>> fut) {
    Tenant t = tenants.get(id);
    if (t != null) {
      fut.handle(new Success<>(new Tenant(t)));
    } else {
      fut.handle(new Failure<>(NOT_FOUND, "Tenant " + id + " not found"));
    }
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    if (tenants.containsKey(id)) {
      tenants.remove(id);
      fut.handle(new Success<>());
    } else {
      fut.handle(new Failure<>(NOT_FOUND, "Tenant " + id + " not found"));
    }
  }

  @Override
  public void enableModule(String id, String module, long timestamp,
          Handler<ExtendedAsyncResult<Void>> fut) {
    Tenant t = tenants.get(id);
    if (t == null) {
      fut.handle(new Failure<>(NOT_FOUND, "Tenant " + id + " not found"));
    } else {
      t.setTimestamp(timestamp);
      t.enableModule(module);
      fut.handle(new Success<>());
    }

  }

  @Override
  public void disableModule(String id, String module, long timestamp,
          Handler<ExtendedAsyncResult<Void>> fut) {
    Tenant t = tenants.get(id);
    if (t == null) {
      fut.handle(new Failure<>(USER, "Tenant " + id + " not found"));
    } else if (!t.isEnabled(module)) {
      fut.handle(new Failure<>(NOT_FOUND, "Module " + module + " for Tenant " + id + " not found, can not disable"));
    } else {
      t.setTimestamp(timestamp);
      t.disableModule(module);
      fut.handle(new Success<>());
    }

  }

}
