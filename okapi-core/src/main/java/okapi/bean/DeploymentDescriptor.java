/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class DeploymentDescriptor {

  private String id;
  private String url;
  private ProcessDeploymentDescriptor descriptor;

  @JsonIgnore
  private ModuleHandle moduleHandle;

  public DeploymentDescriptor(String id, String url,
          ProcessDeploymentDescriptor descriptor,
          ModuleHandle moduleHandle) {
    this.id = id;
    this.url = url;
    this.descriptor = descriptor;
    this.moduleHandle = moduleHandle;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public ProcessDeploymentDescriptor getDescriptor() {
    return descriptor;
  }

  public void setDescriptor(ProcessDeploymentDescriptor descriptor) {
    this.descriptor = descriptor;
  }

  public ModuleHandle getModuleHandle() {
    return moduleHandle;
  }

}
