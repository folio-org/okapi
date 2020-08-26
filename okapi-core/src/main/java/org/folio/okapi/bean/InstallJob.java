package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstallJob {

  private TenantModuleDescriptor[] modules;

  public TenantModuleDescriptor[] getModules() {
    return modules;
  }

  public void setModules(TenantModuleDescriptor[] modules) {
    this.modules = modules;
  }

  @JsonIgnore
  public void setModules(List<TenantModuleDescriptor> modules) {
    this.modules = new TenantModuleDescriptor[modules.size()];
    this.modules = modules.toArray(this.modules);
  }

}
