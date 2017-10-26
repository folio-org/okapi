package org.folio.okapi.util;

public class TenantInstallOptions {

  private boolean preRelease = false;
  private boolean simulate = false;
  private boolean autoDeploy = false;

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

  public void setAutoDeploy(boolean v) {
    autoDeploy = v;
  }

  public boolean getAutoDeploy() {
    return autoDeploy;
  }
}
