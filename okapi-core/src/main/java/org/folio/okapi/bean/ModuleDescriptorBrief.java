package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A brief view of a ModuleDescriptor
 *
 */
@JsonInclude(Include.NON_NULL)
public class ModuleDescriptorBrief {

  private final String id;
  private final String name;

  public ModuleDescriptorBrief(ModuleDescriptor m) {
    this.id = m.getId();
    this.name = m.getName();
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

}
