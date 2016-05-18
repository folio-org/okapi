/*
 * Copyright (c) 2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.bean;

public class ProcessDeploymentDescriptor {

  private String cmdlineStart;
  private String cmdlineStop;
  private String exec;

  public ProcessDeploymentDescriptor() {
  }

  public String getCmdlineStart() {
    return cmdlineStart;
  }

  public void setCmdlineStart(String cmdlineStart) {
    this.cmdlineStart = cmdlineStart;
  }

  public String getCmdlineStop() {
    return cmdlineStop;
  }

  public void setCmdlineStop(String cmdlineStop) {
    this.cmdlineStop = cmdlineStop;
  }

  public String getExec() {
    return exec;
  }

  public void setExec(String exec) {
    this.exec = exec;
  }
}
