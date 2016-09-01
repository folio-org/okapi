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

  public String getNodeId() {
    return nodeId;
  }

  public void setNodeId(String name) {
    this.nodeId = name;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

}
