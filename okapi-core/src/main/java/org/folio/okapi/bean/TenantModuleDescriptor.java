package org.folio.okapi.bean;

/**
 * Association of a module to a tenant. This encapsulates the id of the module.
 * Each tenant has a list of such associations, listing what modules have been
 * enabled for it.
 *
 */
public class TenantModuleDescriptor {

  private String id; // For practical reasons, the UI folks prefer this to be
  // called 'id'. It is the id of a module.

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}
