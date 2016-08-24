/*
 * Copyright (C) 2015-2016 Index Data
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

// Docker Module .. Using the Docker HTTP API
// We don't do local unix sockets .. The dockerd must unfortunately be listening on localhost
// https://docs.docker.com/engine/reference/commandline/dockerd/#bind-docker-to-another-host-port-or-a-unix-socket
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.okapi.bean.Ports;
import org.folio.okapi.bean.LaunchDescriptor;

public class DockerModuleHandle implements ModuleHandle {
  private final Logger logger = LoggerFactory.getLogger("okapi");

  private final Vertx vertx;
  private final LaunchDescriptor desc;
  private final int port;
  private final Ports ports;

  public DockerModuleHandle(Vertx vertx, LaunchDescriptor desc,
          Ports ports, int port) {
    this.vertx = vertx;
    this.desc = desc;
    this.port = port;
    this.ports = ports;
  }

  @Override
  public void start(Handler<AsyncResult<Void>> startFuture) {
    startFuture.handle(Future.failedFuture("Not implemented"));
  }

  @Override
  public void stop(Handler<AsyncResult<Void>> stopFuture) {
    stopFuture.handle(Future.failedFuture("Not implemented"));
  }
}
