package org.folio.okapi.util;

public class TenantInstallOptions {

  private boolean preRelease = false;
  private boolean simulate = false;
  private boolean deploy = false;

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
}
