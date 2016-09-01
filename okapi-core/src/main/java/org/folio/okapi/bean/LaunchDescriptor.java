package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Tells how a module is to be deployed. Either by exec'ing a command (and
 * killing the process afterwards), or by using command lines to start and stop
 * it.
 */
@JsonInclude(Include.NON_NULL)
public class LaunchDescriptor {

  private String cmdlineStart;
  private String cmdlineStop;
  private String exec;
  private String dockerImage;

  public LaunchDescriptor() {
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

  public String getDockerImage() {
    return dockerImage;
  }

  public void setDockerImage(String dockerImage) {
    this.dockerImage = dockerImage;
  }

}
