package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstallJob {

  private String id;

  private Boolean complete;

  private List<TenantModuleDescriptor> modules;

  public void setId(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public List<TenantModuleDescriptor> getModules() {
    return modules;
  }

  public void setModules(List<TenantModuleDescriptor> modules) {
    this.modules = modules;
  }

  public Boolean getComplete() {
    return complete;
  }

  public void setComplete(Boolean complete) {
    this.complete = complete;
  }
}
