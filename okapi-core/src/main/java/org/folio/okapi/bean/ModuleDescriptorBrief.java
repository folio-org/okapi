package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.folio.okapi.util.ModuleId;

/**
 * A brief view of a ModuleDescriptor.
 *
 */
@JsonInclude(Include.NON_NULL)
public class ModuleDescriptorBrief {

  private String id;
  private String name;

  public ModuleDescriptorBrief() {
  }

  public ModuleDescriptorBrief(ModuleDescriptor m) {
    this.id = m.getId();
    this.name = m.getName();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @JsonIgnore
  public String getProduct() {
    return new ModuleId(id).getProduct();
  }
}