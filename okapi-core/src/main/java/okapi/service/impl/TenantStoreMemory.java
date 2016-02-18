/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package okapi.service.impl;

import io.vertx.core.Handler;
import java.util.HashMap;
import java.util.Map;
import okapi.bean.Tenant;
import okapi.bean.TenantDescriptor;
import okapi.util.ExtendedAsyncResult;

/**
 * Mock storage for tenants. 
 * All in memory, so it starts with a clean slate every time the program starts.
 * 
 */
public class TenantStoreMemory {
  Map<String, Tenant> enabled = new HashMap<>();

  public TenantStoreMemory() {
  }
  
  public void put(String id, TenantDescriptor td, Handler<ExtendedAsyncResult<String>> fut) {
    enabled.put(id, new Tenant(td));
  }
  
}
