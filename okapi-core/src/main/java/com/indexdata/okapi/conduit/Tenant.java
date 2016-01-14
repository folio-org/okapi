/*
 * Copyright (c) 2015-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.okapi.conduit;

import com.indexdata.okapi.conduit.TenantDescriptor;
import java.util.HashMap;

public class Tenant {
  private final TenantDescriptor td;
  
  private final HashMap<String,Boolean> enabled = new HashMap<>();
  
  public Tenant(TenantDescriptor td) {
    this.td = td;
  }

  public String getName() {
    return td.getName();
  }

  public TenantDescriptor getDescriptor() {
    return td;
  }

  public void enableModule(String n) {
    enabled.put(n, true);
  }
  
  public boolean isEnabled(String m) {
    return enabled.getOrDefault(m, false);
  }
}
