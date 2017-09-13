package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Description of a node in the Okapi cluster.
 *
 */
@JsonInclude(Include.NON_NULL)
public class NodeDescriptor {

  private String nodeId;
  private String url;
  private String nodeName;

  /**
   * Get the value of nodeName
   *
   * @return the value of nodeName
   */
  public String getNodeName() {
    return nodeName;
  }

  /**
   * Set the value of nodeName
   *
   * @param nodeName new value of nodeName
   */
  public void setNodeName(String nodeName) {
    this.nodeName = nodeName;
  }

  /**
   *
   * @return
   */
  public String getNodeId() {
    return nodeId;
  }

  /**
   *
   * @param name
   */
  public void setNodeId(String name) {
    if (name == null || name.isEmpty()) {
      name = null;
    }
    this.nodeId = name;
  }

  /**
   *
   * @return
   */
  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

}
