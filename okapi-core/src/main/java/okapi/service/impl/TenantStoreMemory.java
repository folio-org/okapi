/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
 * Mock storage for tenants. 
 * All in memory, so it starts with a clean slate every time the program starts.
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
    tenants.put(id, t);
    fut.handle(new Success<>(id));
  }

  @Override
  public void listIds(Handler<ExtendedAsyncResult<List<String>>> fut) {
    List<String> tl = new ArrayList<>();
    for ( String id : tenants.keySet() ) {
      Tenant t = tenants.get(id);
      tl.add(t.getId());
    }
    fut.handle(new Success<>(tl));
  }

  @Override
  public void listTenants(Handler<ExtendedAsyncResult<List<TenantDescriptor>>> fut) {
    List<TenantDescriptor> tl = new ArrayList<>();
    for ( String id : tenants.keySet() ) {
      Tenant t = tenants.get(id);
      tl.add(t.getDescriptor());
    }
    fut.handle(new Success<>(tl));
  }


  @Override
  public void get(String id,Handler<ExtendedAsyncResult<Tenant>> fut ) {
    Tenant t = tenants.get(id);
    if (t != null)
      fut.handle(new Success<>(t));
    else
      fut.handle(new Failure<>(NOT_FOUND,"Tenant " + id + " not found"));
  }

  @Override
  public void delete(String id,Handler<ExtendedAsyncResult<Void>> fut ) {
    if ( tenants.containsKey(id)) {
      tenants.remove(id);
    fut.handle(new Success<>());
    } else {
      fut.handle(new Failure<>(NOT_FOUND,"Tenant " + id + " not found"));
    }
  }

  @Override
  public void enableModule(String id, String module,long timestamp,
        Handler<ExtendedAsyncResult<Void>> fut  ) {
    Tenant t = tenants.get(id);
    if ( t == null ) {
      fut.handle(new Failure<>(NOT_FOUND,"Tenant " + id + " not found"));
    } else {
      t.setTimestamp(timestamp);
      t.enableModule(module);
      fut.handle(new Success<>());
    }

  }



}
