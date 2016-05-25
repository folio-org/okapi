/*
 * Copyright (C) 2016 Index Data
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

public class HealthDescriptor {

  private String instId;
  private String srvcId;
  private String healthMessage;
  private boolean healthStatus;

  public String getInstId() {
    return instId;
  }

  public void setInstId(String instId) {
    this.instId = instId;
  }

  public String getSrvcId() {
    return srvcId;
  }

  public void setSrvcId(String srvcId) {
    this.srvcId = srvcId;
  }

  public String getHealthMessage() {
    return healthMessage;
  }

  public void setHealthMessage(String healthStatus) {
    this.healthMessage = healthStatus;
  }

  public boolean isHealthStatus() {
    return healthStatus;
  }

  public void setHealthStatus(boolean healthStatus) {
    this.healthStatus = healthStatus;
  }
}
