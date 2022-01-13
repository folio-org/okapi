package org.folio.okapi.util;

import java.util.Optional;

public class TenantInstallOptions {

  private Optional<Boolean> preRelease = Optional.empty();
  private boolean simulate = false;
  private boolean deploy = false;
  private boolean purge = false;
  private String tenantParameters;
  private Optional<Boolean> npmSnapshot = Optional.empty();
  private boolean depCheck = true;
  private String invoke;
  private boolean async = false;
  private boolean ignoreErrors = false;
  private boolean reinstall = false;

  public void setPreRelease(Optional<Boolean> v) {
    preRelease = v;
  }

  public Optional<Boolean> getPreRelease() {
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

  public void setNpmSnapshot(Optional<Boolean> v) {
    npmSnapshot = v;
  }

  public Optional<Boolean> getNpmSnapshot() {
    return npmSnapshot;
  }

  public void setDepCheck(boolean v) {
    depCheck = v;
  }

  public boolean getDepCheck() {
    return depCheck;
  }

  public void setInvoke(String v) {
    invoke = v;
  }

  /**
   * Check if module is to be invoked during install.
   * @param id module ID.
   * @return whether to invoke the module.
   */
  public boolean checkInvoke(String id) {
    if (invoke == null || "true".equals(invoke)) {
      return true;
    }
    if ("false".equals(invoke)) {
      return false;
    }
    return id.matches(invoke);
  }

  public void setAsync(boolean v) {
    async = v;
  }

  public boolean getAsync() {
    return async;
  }

  public boolean getIgnoreErrors() {
    return ignoreErrors;
  }

  public void setIgnoreErrors(boolean ignoreErrors) {
    this.ignoreErrors = ignoreErrors;
  }

  public boolean getReinstall() {
    return reinstall;
  }

  public void setReinstall(boolean reinstall) {
    this.reinstall = reinstall;
  }
}
