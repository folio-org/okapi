package org.folio.okapi.bean;

/**
 * Health status for one module.
 */
public class HealthModule {

  private String id;
  private String status;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

}
