package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonRawValue;

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
  private Boolean dockerPull;
  private String[] dockerCMD;
  private EnvEntry[] env;
  private AnyDescriptor dockerArgs;

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

  public Boolean getDockerPull() {
    return dockerPull;
  }

  public void setDockerPull(Boolean dockerPull) {
    this.dockerPull = dockerPull;
  }

  public String[] getDockerCMD() {
    return dockerCMD;
  }

  public void setDockerCMD(String[] dockerCMD) {
    this.dockerCMD = dockerCMD;
  }

  public EnvEntry[] getEnv() {
    return env;
  }

  public void setEnv(EnvEntry[] env) {
    this.env = env;
  }

  public AnyDescriptor getDockerArgs() {
    return dockerArgs;
  }

  public void setDockerArgs(AnyDescriptor dockerArgs) {
    this.dockerArgs = dockerArgs;
  }
}
