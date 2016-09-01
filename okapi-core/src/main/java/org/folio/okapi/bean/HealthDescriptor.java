package org.folio.okapi.bean;

/**
 * Health status for one deployed instance of a module.
 */
public class HealthDescriptor {

  private String instId;
  private String srvcId;
  private String healthMessage;
  private boolean healthStatus;

  public String getInstId() {
    return instId;
  }

  public void setInstId(String instId) {
    this.instId = instId;
  }

  public String getSrvcId() {
    return srvcId;
  }

  public void setSrvcId(String srvcId) {
    this.srvcId = srvcId;
  }

  public String getHealthMessage() {
    return healthMessage;
  }

  public void setHealthMessage(String healthStatus) {
    this.healthMessage = healthStatus;
  }

  public boolean isHealthStatus() {
    return healthStatus;
  }

  public void setHealthStatus(boolean healthStatus) {
    this.healthStatus = healthStatus;
  }
}
