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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.okapi.bean.Ports;
import org.folio.okapi.bean.LaunchDescriptor;

public class DockerModuleHandle implements ModuleHandle {
  private final Logger logger = LoggerFactory.getLogger("okapi");

  private final Vertx vertx;
  private final int port;
  private final Ports ports;
  private final String image;
  private final String dockerUrl;
  private String containerId;

  public DockerModuleHandle(Vertx vertx, LaunchDescriptor desc,
          Ports ports, int port) {
    this.vertx = vertx;
    this.port = port;
    this.ports = ports;
    this.image = desc.getDockerImage();
    this.dockerUrl = "http://localhost:4243";
  }

  private void startContainer(Handler<AsyncResult<Void>> future) {
    logger.info("startContainer");
    HttpClient client = vertx.createHttpClient();
    final String url = dockerUrl + "/containers/" + containerId + "/start";
    HttpClientRequest req = client.postAbs(url, res -> {
      Buffer body = Buffer.buffer();
      /*
      res.exceptionHandler(d -> {
        future.handle(Future.failedFuture(d.getCause()));
      });
       */
      res.handler(d -> {
        body.appendBuffer(d);
      });
      res.endHandler(d -> {
        if (res.statusCode() == 204) {
          future.handle(Future.succeededFuture());
        } else {
          String m = "startContainer HTTP error "
                  + Integer.toString(res.statusCode()) + "\n"
                  + body.toString();
          logger.error(m);
          future.handle(Future.failedFuture(m));
        }
      });
    });
    req.exceptionHandler(d -> {
      future.handle(Future.failedFuture(d.getCause()));
    });
    req.end();
  }

  private void stopContainer(Handler<AsyncResult<Void>> future) {
    logger.info("stopContainer");
    HttpClient client = vertx.createHttpClient();
    HttpClientRequest req;
    final String url = dockerUrl + "/containers/" + containerId + "/stop";
    req = client.postAbs(url, res -> {
      Buffer body = Buffer.buffer();
      res.exceptionHandler(d -> {
        future.handle(Future.failedFuture(d.getCause()));
      });
      res.handler(d -> {
        body.appendBuffer(d);
      });
      res.endHandler(d -> {
        if (res.statusCode() == 204) {
          future.handle(Future.succeededFuture());
        } else {
          String m = "stopContainer HTTP error "
                  + Integer.toString(res.statusCode()) + "\n"
                  + body.toString();
          logger.error(m);
          future.handle(Future.failedFuture(m));
        }
      });
    });
    req.exceptionHandler(d -> {
      future.handle(Future.failedFuture(d.getCause()));
    });
    req.end();
  }

  private void deleteContainer(Handler<AsyncResult<Void>> future) {
    logger.info("deleteContainer");
    HttpClient client = vertx.createHttpClient();
    final String url = dockerUrl + "/containers/" + containerId;
    HttpClientRequest req = client.deleteAbs(url, res -> {
      Buffer body = Buffer.buffer();
      res.exceptionHandler(d -> {
        future.handle(Future.failedFuture(d.getCause()));
      });
      res.handler(d -> {
        body.appendBuffer(d);
      });
      res.endHandler(d -> {
        if (res.statusCode() == 204) {
          future.handle(Future.succeededFuture());
        } else {
          String m = "deleteContainer HTTP error "
                  + Integer.toString(res.statusCode()) + "\n"
                  + body.toString();
          logger.error(m);
          future.handle(Future.failedFuture(m));
        }
      });
    });
    req.exceptionHandler(d -> {
      future.handle(Future.failedFuture(d.getCause()));
    });
    req.end();
  }

  private void createContainer(Handler<AsyncResult<Void>> future) {
    String doc = "{\n"
            + "  \"AttachStdin\":false,\n"
            + "  \"AttachStdout\":true,\n"
            + "  \"AttachStderr\":true,\n"
            + "  \"Image\":\"" + image + "\",\n"
            + "  \"StopSignal\":\"SIGTERM\",\n"
            + "  \"HostConfig\":{\n"
            + "    \"PortBindings\":{\"8080/tcp\":[{\"HostPort\":\""
            + Integer.toString(port)
            + "\"}]},\n"
            + "    \"PublishAllPorts\":false\n"
            + "  }\n"
            + "}\n";
    HttpClient client = vertx.createHttpClient();
    logger.info("createContainer\n" + doc);
    final String url = dockerUrl + "/containers/create";
    HttpClientRequest req = client.postAbs(url, res -> {
      Buffer body = Buffer.buffer();
      res.exceptionHandler(d -> {
        future.handle(Future.failedFuture(d.getCause()));
      });
      res.handler(d -> {
        body.appendBuffer(d);
      });
      res.endHandler(d -> {
        if (res.statusCode() == 201) {
          containerId = body.toJsonObject().getString("Id");
          future.handle(Future.succeededFuture());
        } else {
          String m = "createContainer HTTP error "
                  + Integer.toString(res.statusCode()) + "\n"
                  + body.toString();
          logger.error(m);
          future.handle(Future.failedFuture(m));
        }
      });
    });
    req.exceptionHandler(d -> {
      future.handle(Future.failedFuture(d.getCause()));
    });
    req.putHeader("Content-Type", "application/json");
    req.end(doc);
  }

  @Override
  public void start(Handler<AsyncResult<Void>> startFuture) {
    createContainer(res -> {
      if (res.failed()) {
        startFuture.handle(Future.failedFuture(res.cause()));
      } else {
        startContainer(startFuture);
      }
    });
  }

  @Override
  public void stop(Handler<AsyncResult<Void>> stopFuture) {
    stopContainer(res -> {
      if (res.failed()) {
        stopFuture.handle(Future.failedFuture(res.cause()));
      } else {
        ports.free(port);
        deleteContainer(stopFuture);
      }
    });
  }
}
