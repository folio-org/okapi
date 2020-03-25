package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.vertx.core.json.DecodeException;
import org.folio.okapi.service.ModuleHandle;

/**
 * Description of one deployed module. Refers to one running instance of a
 * module on a node in the cluster.
 */
@JsonInclude(Include.NON_NULL)
public class DeploymentDescriptor {

  private String instId;
  private String srvcId;
  private String nodeId;
  private String url;
  private LaunchDescriptor descriptor;

  @JsonIgnore
  private ModuleHandle moduleHandle;

  /**
   * Construct deployment descriptor.
   */
  public DeploymentDescriptor() {
  }

  /**
   * Construct deployment descriptor based on remote URL.
   * @param instId instance ID
   * @param srvcId service ID (normally module ID)
   * @param url URL for service
   * @param descriptor launch descriptor
   * @param moduleHandle module handle
   */
  public DeploymentDescriptor(String instId, String srvcId,
                              String url, LaunchDescriptor descriptor, ModuleHandle moduleHandle) {
    this.instId = instId;
    this.srvcId = srvcId;
    this.url = url;
    this.descriptor = descriptor;
    this.moduleHandle = moduleHandle;
  }

  /**
   * Construct deployment descriptor.
   * @param instId instance ID
   * @param srvcId service ID (normally module ID)
   * @param descriptor launch descriptor
   */
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

  /**
   * Sets service ID (normally same as module ID).
   * Throws DecodeException if empty
   * @param srvcId the service ID
   */
  public void setSrvcId(String srvcId) {
    if (srvcId.isEmpty()) {
      throw new DecodeException("Empty srvcId not allowed");
    }
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
