package org.folio.okapi.util;

public enum OkapiEvents {
  DEPLOYMENT_NODE_START("deployment.nodestart"),
  DEPLOYMENT_NODE_STOP("deployment.nodestop"),
  DEPLOYMENT_DEPLOY("deployment.servicedeploy"),
  DEPLOYMENT_UNDEPLOY("deployment.serviceundeploy");

  // boilerplate
  public final String eventName;
  private static final String BUS_BASE = "okapi.conf.";

  private OkapiEvents(String eventName) {
    this.eventName = BUS_BASE + eventName;
  }

  @Override
  public String toString() {
    return eventName;
  }

}
