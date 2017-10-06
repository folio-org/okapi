package org.folio.okapi.bean;

/**
 * Description of a Tenant. This is what gets POSTed to "/_/proxy/tenants" to
 * create new tenants, etc. Carries an id, and some human-readable info about
 * the tenant.
 *
 */
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class TenantDescriptor {

  private String id;
  private String name;
  private String description;

  public void setName(String name) {
    this.name = name;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public TenantDescriptor() {
    this.id = null;
    this.name = null;
    this.description = null;
  }
}
