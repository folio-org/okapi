/*
 * Copyright (C) 2015 Index Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okapi.bean;

/**
 * Tells how a module is to be deployed. Either by exec'ing a command (and
 * killing the process afterwards), or by using command lines to start and
 * stop it.
 */
public class LaunchDescriptor {

  private String cmdlineStart;
  private String cmdlineStop;
  private String exec;

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
}
