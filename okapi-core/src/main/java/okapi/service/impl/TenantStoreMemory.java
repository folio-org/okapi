/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package okapi.service.impl;

import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okapi.bean.Tenant;
import static okapi.util.ErrorType.*;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import okapi.util.Success;

/**
 * Mock storage for tenants. 
 * All in memory, so it starts with a clean slate every time the program starts.
 * 
 */
public class TenantStoreMemory {
  Map<String, Tenant> tenants = new HashMap<>();

  public TenantStoreMemory() {
  }
  
  public void insert(Tenant t,
                     Handler<ExtendedAsyncResult<String>> fut) {
    String id = t.getName(); // TODO - Should be getId()  Issue #43
    tenants.put(id, t);
    System.out.println("TenantStore: Inserted tenant " + id + ":" + Json.encode(t));
    fut.handle(new Success<>(id));
  }

  public void listIds(Handler<ExtendedAsyncResult<List<String>>> fut) {
    List<String> tl = new ArrayList<>();
    for ( String id : tenants.keySet() ) {
      Tenant t = tenants.get(id);
      System.out.println("TenantStore: listIds: Looking at " + id + ": " + Json.encode(tenants.get(id)));
      tl.add(t.getId());
    }
    System.out.println("TenantStore: listIds " + Json.encode(tl));
    fut.handle(new Success<>(tl));
  }

  public void get(String id,Handler<ExtendedAsyncResult<Tenant>> fut ) {
    Tenant t = tenants.get(id);
    fut.handle(new Success<>(t));
  }

  public void delete(String id,Handler<ExtendedAsyncResult<Void>> fut ) {
    if ( tenants.containsKey(id)) {
      tenants.remove(id);
    fut.handle(new Success<>());
    } else {
      fut.handle(new Failure<>(NOT_FOUND,"Tenant " + id + " not found"));
    }
  }

}
