package org.folio.okapi.util;

public class TenantInstallOptions {

  private boolean preRelease = false;
  private boolean simulate = false;
  private boolean deploy = false;
  private boolean purge = false;
  private String tenantParameters;
  private boolean npmSnapshot = false;

  public void setPreRelease(boolean v) {
    preRelease = v;
  }

  public boolean getPreRelease() {
    return preRelease;
  }

  public void setSimulate(boolean v) {
    simulate = v;
  }

  public boolean getSimulate() {
    return simulate;
  }

  public void setDeploy(boolean v) {
    deploy = v;
  }

  public boolean getDeploy() {
    return deploy;
  }

  public void setPurge(boolean v) {
    purge = v;
  }

  public boolean getPurge() {
    return purge;
  }

  public void setTenantParameters(String v) {
    tenantParameters = v;
  }

  public String getTenantParameters() {
    return tenantParameters;
  }

  public void setNpmSnapshot(boolean v) {
    npmSnapshot = v;
  }

  public boolean getNpmSnapshot() {
    return npmSnapshot;
  }

}
