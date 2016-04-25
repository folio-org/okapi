/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class DeploymentDescriptor {

  private String id;
  private String name;
  private String url;
  private ProcessDeploymentDescriptor descriptor;

  @JsonIgnore
  private ModuleHandle moduleHandle;

  public DeploymentDescriptor() {
  }

  public DeploymentDescriptor(String id, String name,
          String url,
          ProcessDeploymentDescriptor descriptor,
          ModuleHandle moduleHandle) {
    this.id = id;
    this.name = name;
    this.url = url;
    this.descriptor = descriptor;
    this.moduleHandle = moduleHandle;
  }

  public DeploymentDescriptor(String id, String name,
          ProcessDeploymentDescriptor descriptor) {
    this.id = id;
    this.name = name;
    this.url = null;
    this.descriptor = descriptor;
    this.moduleHandle = null;
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
