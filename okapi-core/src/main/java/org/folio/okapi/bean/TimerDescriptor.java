package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimerDescriptor {
  private String id;

  private RoutingEntry routingEntry;

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

}
