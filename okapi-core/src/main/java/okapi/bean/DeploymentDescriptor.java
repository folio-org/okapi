/*
 * Copyright (C) 2016 Index Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okapi.bean;

import okapi.util.ModuleHandle;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Description of one deployed module.
 * Refers to one running instance of a module on a node in the cluster.
 */
public class DeploymentDescriptor {

  private String instId;
  private String srvcId;
  private String nodeId;
  private String url;
  private LaunchDescriptor descriptor;

  @JsonIgnore
  private ModuleHandle moduleHandle;

  public DeploymentDescriptor() {
  }

  public DeploymentDescriptor(String instId, String srvcId,
          String url,
          LaunchDescriptor descriptor,
          ModuleHandle moduleHandle) {
    this.instId = instId;
    this.srvcId = srvcId;
    this.url = url;
    this.descriptor = descriptor;
    this.moduleHandle = moduleHandle;
  }

  public DeploymentDescriptor(String instId, String srvcId,
          LaunchDescriptor descriptor) {
    this.instId = instId;
    this.srvcId = srvcId;
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

  public LaunchDescriptor getDescriptor() {
    return descriptor;
  }

  public void setDescriptor(LaunchDescriptor descriptor) {
    this.descriptor = descriptor;
  }

  public ModuleHandle getModuleHandle() {
    return moduleHandle;
  }

}
