package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A brief view of a ModuleDescriptor.
 *
 */
@JsonInclude(Include.NON_NULL)
public class ModuleDescriptorBrief implements Comparable {

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
  @Override
  public int compareTo(Object o) {
    ModuleDescriptorBrief m = (ModuleDescriptorBrief) o;
    return this.getId().compareTo(m.getId());
  }
}
