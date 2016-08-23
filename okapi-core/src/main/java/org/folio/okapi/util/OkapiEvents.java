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
package org.folio.okapi.util;

public enum OkapiEvents {
  DEPLOYMENT_NODE_START("deployment.nodestart"),
  DEPLOYMENT_NODE_STOP("deployment.nodestop"),
  DEPLOYMENT_DEPLOY("deployment.servicedeploy"),
  DEPLOYMENT_UNDEPLOY("deployment.serviceundeploy");

  // boilerplate
  public final String eventName;
  private static final String BUS_BASE = "okapi.conf.";

  private OkapiEvents(String eventName) {
    this.eventName = BUS_BASE + eventName;
  }

  @Override
  public String toString() {
    return eventName;
  }

}
