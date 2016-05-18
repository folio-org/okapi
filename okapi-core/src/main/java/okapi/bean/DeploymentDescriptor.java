/*
 * Copyright (c) 2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.bean;

import okapi.util.ModuleHandle;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class DeploymentDescriptor {

  private String instId;
  private String srvcId;
  private String nodeId;
  private String url;
  private ProcessDeploymentDescriptor descriptor;

  @JsonIgnore
  private ModuleHandle moduleHandle;

  public DeploymentDescriptor() {
  }

  public DeploymentDescriptor(String instId, String srvcId,
          String url,
          ProcessDeploymentDescriptor descriptor,
          ModuleHandle moduleHandle) {
    this.instId = instId;
    this.srvcId = srvcId;
    this.url = url;
    this.descriptor = descriptor;
    this.moduleHandle = moduleHandle;
  }

  public DeploymentDescriptor(String instId,
          ProcessDeploymentDescriptor descriptor) {
    this.instId = instId;
    this.url = null;
    this.descriptor = descriptor;
    this.moduleHandle = null;
  }

  public String getInstId() {
    return instId;
  }

  public void setInstId(String id) {
    this.instId = id;
  }

  public String getSrvcId() {
    return srvcId;
  }

  public void setSrvcId(String srvcId) {
    this.srvcId = srvcId;
  }

  public String getNodeId() {
    return nodeId;
  }

  public void setNodeId(String nodeId) {
    this.nodeId = nodeId;
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
