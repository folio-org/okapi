package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.*;
import java.util.Set;
import java.util.TreeMap;

/**
 * Internal representation of a Tenant.
 * Includes stuff like what modules have been enabled for this tenant, etc.
 * This is what gets stored in the database.
 *
 */
public class Tenant {

  @JsonProperty
  final private TenantDescriptor descriptor;
  // id and name, and that kind of stuff

  @JsonProperty
  private TreeMap<String, Boolean> enabled;
  // Note that this can not just be a Map, or the order of enabled modules
  // will be undefined. That should not harm in real life, but it messes up
  // our tests!

  @JsonIgnore
  private long timestamp;  // DEPRECATED - not needed any more
  // TODO - Remove when going to Okapi 2.0

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
   * Copy constructor. Makes separate copies of everything.
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
   * Get the name. The JsonIgnore tells JSON not to encode the name as a
   * top-level thing.
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

  public void setEnabled(TreeMap<String, Boolean> enabled) {
    this.enabled = enabled;
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
