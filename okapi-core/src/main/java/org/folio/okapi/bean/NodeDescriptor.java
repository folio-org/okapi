package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Description of a node in the Okapi cluster.
 */
@JsonInclude(Include.NON_NULL)
public class NodeDescriptor {

  private String nodeId;
  private String url;
  private String nodeName;

  /**
   * Return node name.
   *
   * @return node name
   */
  public String getNodeName() {
    return nodeName;
  }

  /**
   * Set the node name.
   *
   * @param nodeName new value of nodeName
   */
  public void setNodeName(String nodeName) {
    this.nodeName = nodeName;
  }

  /**
   * get node ID.
   * @return node ID string
   */
  public String getNodeId() {
    return nodeId;
  }

  /**
   * set node ID.
   * @param id node ID
   */
  public void setNodeId(String id) {
    if (id == null || id.isEmpty()) {
      id = null;
    }
    this.nodeId = id;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

}
