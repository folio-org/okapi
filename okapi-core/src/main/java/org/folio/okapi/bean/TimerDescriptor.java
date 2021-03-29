package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimerDescriptor {
  private String id;

  private RoutingEntry routingEntry;

  private boolean modified;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public RoutingEntry getRoutingEntry() {
    return routingEntry;
  }

  public void setRoutingEntry(RoutingEntry routingEntry) {
    this.routingEntry = routingEntry;
  }

  public boolean isModified() {
    return modified;
  }

  public void setModified(boolean modified) {
    this.modified = modified;
  }
}
