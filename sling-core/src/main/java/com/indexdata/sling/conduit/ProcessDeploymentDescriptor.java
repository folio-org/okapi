/*
 * Copyright (c) 1995-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.sling.conduit;

/**
 *
 * @author jakub
 */
public class ProcessDeploymentDescriptor {
  private String cmdlineStart;
  private String cmdlineStop;

  public ProcessDeploymentDescriptor() {
  }
  
  public ProcessDeploymentDescriptor(String cmdlineStart, String cmdlineStop) {
    this.cmdlineStart = cmdlineStart;
    this.cmdlineStop = cmdlineStop;
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
 
}
