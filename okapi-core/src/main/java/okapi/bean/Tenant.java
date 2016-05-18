/*
 * Copyright (c) 2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.*;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class Tenant {

  @JsonProperty
  final private TenantDescriptor descriptor;

  @JsonProperty
  final private TreeMap<String, Boolean> enabled;
  // Note that this can not just be a Map, or the order of enabled modules
  // will be undefined. That should not harm in real life, but it messes up
  // our tests!

  @JsonIgnore
  private long timestamp;

  public Tenant(TenantDescriptor descriptor) {
    this.descriptor = descriptor;
    this.enabled = new TreeMap<>();
    this.timestamp = 0;
  }

  public Tenant(TenantDescriptor descriptor, TreeMap<String, Boolean> enabled) {
    this.descriptor = descriptor;
    this.enabled = enabled;
    this.timestamp = 0;
  }

  public Tenant() {
    this.descriptor = new TenantDescriptor();
    this.enabled = new TreeMap<>();
    this.timestamp = 0;
  }

  /**
   * Copy constructor. Makes separate copies of everything
   *
   * @param other
   */
  public Tenant(Tenant other) {
    this.descriptor = new TenantDescriptor(other.descriptor);
    this.enabled = new TreeMap<>(other.enabled);
    this.timestamp = 0;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  /**
   * Get the name. The JsonIgnore tells Json not to encode the name as a
   * top-level thing
   *
   * @return
   */
  @JsonIgnore
  public String getName() {
    return descriptor.getName();
  }

  @JsonIgnore
  public String getId() {
    return descriptor.getId();
  }

  public TenantDescriptor getDescriptor() {
    return descriptor;
  }

  public TreeMap<String, Boolean> getEnabled() {
    return enabled;
  }

  public void enableModule(String n) {
    enabled.put(n, true);
  }

  public void disableModule(String n) {
    enabled.remove(n, true);
  }

  public boolean isEnabled(String m) {
    return enabled.getOrDefault(m, false);
  }

  public Set<String> listModules() {
    return enabled.keySet();
  }
}
