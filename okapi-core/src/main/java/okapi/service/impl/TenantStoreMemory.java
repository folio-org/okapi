/*
 * Copyright (c) 2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.service.impl;

import okapi.service.TenantStore;
import io.vertx.core.Handler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okapi.bean.Tenant;
import okapi.bean.TenantDescriptor;
import static okapi.util.ErrorType.*;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import okapi.util.Success;

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
  public void update(Tenant t,
          Handler<ExtendedAsyncResult<String>> fut) {
    String id = t.getId();
    tenants.put(id, new Tenant(t));
    fut.handle(new Success<>(id));
  }

  @Override
  public void updateDescriptor(String id, TenantDescriptor td, Handler<ExtendedAsyncResult<Void>> fut) {
    Tenant t = tenants.get(id);
    Tenant nt = new Tenant(td, t.getEnabled());
    // TODO - Validate that we don't change the id
    tenants.put(id, nt);
    fut.handle(new Success<>());
  }

  @Override
  public void listIds(Handler<ExtendedAsyncResult<List<String>>> fut) {
    List<String> tl = new ArrayList<>();
    for (String id : tenants.keySet()) {
      Tenant t = tenants.get(id);
      tl.add(t.getId());
    }
    fut.handle(new Success<>(tl));
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
