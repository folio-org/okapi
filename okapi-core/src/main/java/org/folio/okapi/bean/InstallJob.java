package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstallJob {

  private String id;

  private Boolean complete;

  private String startDate;

  private String endDate;

  private List<TenantModuleDescriptor> modules;

  public void setId(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public Boolean getComplete() {
    return complete;
  }

  public void setComplete(Boolean complete) {
    this.complete = complete;
  }

  public String getStartDate() {
    return startDate;
  }

  public void setStartDate(String date) {
    this.startDate = date;
  }

  public String getEndDate() {
    return endDate;
  }

  public void setEndDate(String endDate) {
    this.endDate = endDate;
  }

  public List<TenantModuleDescriptor> getModules() {
    return modules;
  }

  public void setModules(List<TenantModuleDescriptor> modules) {
    this.modules = modules;
  }

}
