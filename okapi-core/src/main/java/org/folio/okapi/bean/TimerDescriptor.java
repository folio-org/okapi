package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimerDescriptor {
  private RoutingEntry routingEntry;

  public RoutingEntry getRoutingEntry() {
    return routingEntry;
  }

  public void setRoutingEntry(RoutingEntry routingEntry) {
    this.routingEntry = routingEntry;
  }
}
