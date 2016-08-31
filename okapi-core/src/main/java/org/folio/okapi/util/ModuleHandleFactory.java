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
package org.folio.okapi.util;

import io.vertx.core.Vertx;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.bean.Ports;

public class ModuleHandleFactory {
  public static ModuleHandle create(Vertx vertx, LaunchDescriptor desc, Ports ports, int port) {
    ModuleHandle mh = null;
    if (desc.getDockerImage() == null) {
      mh = new ProcessModuleHandle(vertx, desc, ports, port);
    } else {
      mh = new DockerModuleHandle(vertx, desc, ports, port);
    }
    return mh;
  }
}
