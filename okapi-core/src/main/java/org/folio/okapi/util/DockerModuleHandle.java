package org.folio.okapi.util;

// Docker Module. Using the Docker HTTP API.
// We don't do local unix sockets. The dockerd must unfortunately be listening on localhost.
// https://docs.docker.com/engine/reference/commandline/dockerd/#bind-docker-to-another-hostport-or-a-unix-socket
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.Iterator;
import java.util.Map;
import org.folio.okapi.bean.AnyDescriptor;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.bean.Ports;

public class DockerModuleHandle implements ModuleHandle {
  private final Logger logger = LoggerFactory.getLogger("okapi");

  private final Vertx vertx;
  private final int hostPort;
  private final Ports ports;
  private final String image;
  private final String[] cmd;
  private final String dockerUrl;
  private final EnvEntry[] env;
  private final AnyDescriptor dockerArgs;
  private final boolean dockerPull;

  private String containerId;

  public DockerModuleHandle(Vertx vertx, LaunchDescriptor desc,
          Ports ports, int port) {
    this.vertx = vertx;
    this.hostPort = port;
    this.ports = ports;
    this.image = desc.getDockerImage();
    this.cmd = desc.getDockerCMD();
    this.env = desc.getEnv();
    this.dockerArgs = desc.getDockerArgs();
    Boolean b = desc.getDockerPull();
    if (b != null && b.booleanValue() == false) {
      this.dockerPull = false;
    } else {
      this.dockerPull = true;
    }
    String u = System.getProperty("dockerUrl", "http://localhost:4243");
    while (u.endsWith("/")) {
      u = u.substring(0, u.length() - 1);
    }
    this.dockerUrl = u;
  }

  private void startContainer(Handler<AsyncResult<Void>> future) {
    logger.info("start container " + containerId + " image " + image);
    HttpClient client = vertx.createHttpClient();
    final String url = dockerUrl + "/containers/" + containerId + "/start";
    HttpClientRequest req = client.postAbs(url, res -> {
      Buffer body = Buffer.buffer();
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
    logger.info("stop container " + containerId + " image " + image);
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
    logger.info("delete container " + containerId + " image " + image);
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

  private void getContainerLog(Handler<AsyncResult<Void>> future) {
    HttpClient client = vertx.createHttpClient();
    final String url = dockerUrl + "/containers/" + containerId + "/logs?stderr=1&stdout=1&follow=1";
    HttpClientRequest req = client.getAbs(url, res -> {
      if (res.statusCode() == 200) {
        // stream OK. Continue other work but keep fetching!
        res.handler(d -> {
          System.err.print(d.getString(8, d.length()));
        });
        future.handle(Future.succeededFuture());
      } else {
        String m = "getContainerLog HTTP error "
                + Integer.toString(res.statusCode());
        logger.error(m);
        future.handle(Future.failedFuture(m));
      }
    });
    req.exceptionHandler(d -> {
      future.handle(Future.failedFuture(d.getCause()));
    });
    req.end();
  }

  private void getImage(Handler<AsyncResult<JsonObject>> future) {
    HttpClient client = vertx.createHttpClient();
    final String url = dockerUrl + "/images/" + image + "/json";
    HttpClientRequest req = client.getAbs(url, res -> {
      Buffer body = Buffer.buffer();
      res.exceptionHandler(d -> {
        logger.warn(url + ": " + d.getMessage());
        future.handle(Future.failedFuture(url + ": " + d.getMessage()));
      });
      res.handler(d -> {
        body.appendBuffer(d);
      });
      res.endHandler(d -> {
        if (res.statusCode() == 200) {
          JsonObject b = body.toJsonObject();
          future.handle(Future.succeededFuture(b));
        } else {
          String m = url + " HTTP error "
            + Integer.toString(res.statusCode()) + "\n"
            + body.toString();
          logger.error(m);
          future.handle(Future.failedFuture(m));
        }
      });
    });
    req.exceptionHandler(d -> {
      logger.warn(url + ": " + d.getMessage());
      future.handle(Future.failedFuture(url + ": " + d.getMessage()));
    });
    req.end();
  }

  private void pullImage(Handler<AsyncResult<JsonObject>> future) {
    logger.info("pull image " + image + " initiated");
    HttpClient client = vertx.createHttpClient();
    final String url = dockerUrl + "/images/create?fromImage=" + image;
    HttpClientRequest req = client.postAbs(url, res -> {
      Buffer body = Buffer.buffer();
      res.exceptionHandler(d -> {
        logger.warn(url + ": " + d.getMessage());
        future.handle(Future.failedFuture(url + ": " + d.getMessage()));
      });
      res.handler(d -> {
        body.appendBuffer(d);
      });
      res.endHandler(d -> {
        logger.info("pull image " + image + " done");
        if (res.statusCode() == 200) {
          JsonObject b = body.toJsonObject();
          future.handle(Future.succeededFuture(b));
        } else {
          String m = url + " HTTP error "
            + Integer.toString(res.statusCode()) + "\n"
            + body.toString();
          logger.error(m);
          future.handle(Future.failedFuture(m));
        }
      });
    });
    req.exceptionHandler(d -> {
      logger.warn(url + ": " + d.getMessage());
      future.handle(Future.failedFuture(url + ": " + d.getMessage()));
    });
    req.end();
  }

  private void createContainer(int exposedPort, Handler<AsyncResult<Void>> future) {
    logger.info("create container from image " + image);
    JsonObject j = new JsonObject();
    j.put("AttachStdin", Boolean.FALSE);
    j.put("AttachStdout", Boolean.TRUE);
    j.put("AttachStderr", Boolean.TRUE);
    j.put("StopSignal", "SIGTERM");
    if (env != null) {
      JsonArray a = new JsonArray();
      for (EnvEntry nv : env) {
        a.add(nv.getName() + "=" + nv.getValue());
      }
      j.put("env", a);
    }
    j.put("Image", image);

    JsonObject hp = new JsonObject().put("HostPort", Integer.toString(hostPort));
    JsonArray ep = new JsonArray().add(hp);
    JsonObject pb = new JsonObject();
    pb.put(Integer.toString(exposedPort) + "/tcp", ep);
    JsonObject hc = new JsonObject();
    hc.put("PortBindings", pb);
    hc.put("PublishAllPorts", Boolean.FALSE);
    j.put("HostConfig", hc);

    if (this.cmd != null && this.cmd.length > 0) {
      JsonArray a = new JsonArray();
      for (int i = 0; i < cmd.length; i++) {
        a.add(cmd[i]);
      }
      j.put("Cmd", a);
    }
    if (dockerArgs != null) {
      for (Map.Entry<String, Object> entry : dockerArgs.properties().entrySet()) {
        j.put(entry.getKey(), entry.getValue());
      }
    }
    String doc = j.encodePrettily();
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

  private void prepareContainer(Handler<AsyncResult<Void>> startFuture) {
    getImage(res1 -> {
      if (res1.failed()) {
        logger.warn("getImage failed 1 : " + res1.cause().getMessage());
        startFuture.handle(Future.failedFuture(res1.cause()));
      } else {
        JsonObject b = res1.result();
        JsonObject config = b.getJsonObject("Config");
        JsonObject exposedPorts = config.getJsonObject("ExposedPorts");
        Iterator<Map.Entry<String, Object>> iterator = exposedPorts.iterator();
        int exposedPort = 0;
        while (iterator.hasNext()) {
          Map.Entry<String, Object> next = iterator.next();
          String key = next.getKey();
          String sPort = key.split("/")[0];
          if (exposedPort == 0) {
            exposedPort = Integer.valueOf(sPort);
          }
        }
        if (hostPort == 0) {
          startFuture.handle(Future.failedFuture("No exposedPorts in image"));
        } else {
          createContainer(exposedPort, res2 -> {
            if (res2.failed()) {
              startFuture.handle(Future.failedFuture(res2.cause()));
            } else {
              startContainer(res3 -> {
                if (res3.failed()) {
                  startFuture.handle(Future.failedFuture(res3.cause()));
                } else {
                  getContainerLog(startFuture);
                }
              });
            }
          });
        }
      }
    });
  }

  @Override
  public void start(Handler<AsyncResult<Void>> startFuture) {
    if (dockerPull) {
      pullImage(res -> {
        prepareContainer(startFuture);
      });
    } else {
      prepareContainer(startFuture);
    }
  }

  @Override
  public void stop(Handler<AsyncResult<Void>> stopFuture) {
    stopContainer(res -> {
      if (res.failed()) {
        stopFuture.handle(Future.failedFuture(res.cause()));
      } else {
        ports.free(hostPort);
        deleteContainer(stopFuture);
      }
    });
  }
}
