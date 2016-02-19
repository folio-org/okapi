/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.bean;

import java.util.HashMap;
import java.util.Set;

public class Tenant {
  private final TenantDescriptor td;
  
  private final HashMap<String,Boolean> enabled = new HashMap<>();
  
  public Tenant(TenantDescriptor td) {
    this.td = td;
  }

  public String getName() {
    return td.getName();
  }

  public String getId() {
    return td.getId();
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

  public Set<String> listModules() {
     return enabled.keySet();
  }
}
