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
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import java.util.Iterator;
import java.util.Map;
import org.folio.okapi.bean.AnyDescriptor;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.bean.Ports;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;

@java.lang.SuppressWarnings({"squid:S1192"})
public class DockerModuleHandle implements ModuleHandle {

  private final Logger logger = OkapiLogger.get();

  private final int hostPort;
  private final Ports ports;
  private final String image;
  private final String[] cmd;
  private final String dockerUrl;
  private final EnvEntry[] env;
  private final AnyDescriptor dockerArgs;
  private final boolean dockerPull;
  private final HttpClient client;
  private final StringBuilder logBuffer;
  private int logSkip;
  private final String id;
  private Messages messages = Messages.getInstance();

  private String containerId;

  public DockerModuleHandle(Vertx vertx, LaunchDescriptor desc,
    String id, Ports ports, int port) {
    this.hostPort = port;
    this.ports = ports;
    this.id = id;
    this.image = desc.getDockerImage();
    this.cmd = desc.getDockerCMD();
    this.env = desc.getEnv();
    this.dockerArgs = desc.getDockerArgs();
    this.client = vertx.createHttpClient();
    this.logBuffer = new StringBuilder();
    this.logSkip = 0;
    Boolean b = desc.getDockerPull();
    this.dockerPull = b == null || b.booleanValue();
    String u = System.getProperty("dockerUrl", "http://localhost:4243");
    while (u.endsWith("/")) {
      u = u.substring(0, u.length() - 1);
    }
    if (!u.contains("/v")) {
      u += "/v1.25";
    }
    this.dockerUrl = u;
  }

  private void handle204(HttpClientResponse res, String msg,
    Handler<AsyncResult<Void>> future) {
    Buffer body = Buffer.buffer();
    res.handler(body::appendBuffer);
    res.endHandler(d -> {
      if (res.statusCode() == 204) {
        future.handle(Future.succeededFuture());
      } else {
        String m = msg + " HTTP error "
          + Integer.toString(res.statusCode()) + "\n"
          + body.toString();
        logger.error(m);
        future.handle(Future.failedFuture(m));
      }
    });

  }

  private void postUrl(String url, String msg,
    Handler<AsyncResult<Void>> future) {

    HttpClientRequest req = client.postAbs(url, res -> handle204(res, msg, future));
    req.exceptionHandler(d -> future.handle(Future.failedFuture(d.getCause())));
    req.end();
  }

  private void deleteUrl(String url,
                         Handler<AsyncResult<Void>> future) {

    HttpClientRequest req = client.deleteAbs(url, res -> handle204(res, "deleteContainer", future));
    req.exceptionHandler(d -> future.handle(Future.failedFuture(d.getCause())));
    req.end();
  }

  private void startContainer(Handler<AsyncResult<Void>> future) {
    logger.info("start container " + containerId + " image " + image);
    postUrl(dockerUrl + "/containers/" + containerId + "/start",
      "startContainer", future);
  }

  private void stopContainer(Handler<AsyncResult<Void>> future) {
    logger.info("stop container " + containerId + " image " + image);
    postUrl(dockerUrl + "/containers/" + containerId + "/stop",
      "stopContainer", future);
  }

  private void deleteContainer(Handler<AsyncResult<Void>> future) {
    logger.info("delete container " + containerId + " image " + image);
    deleteUrl(dockerUrl + "/containers/" + containerId,
      future);
  }

  private void logHandler(Buffer b) {
    if (logSkip == 0 && b.getByte(0) < 3) {
      logSkip = 8;
    }
    if (b.length() > logSkip) {
      logBuffer.append(b.getString(logSkip, b.length()));
      logSkip = 0;
    } else {
      logSkip = logSkip - b.length();
    }
    if (logBuffer.length() > 0 && logBuffer.charAt(logBuffer.length() - 1) == '\n') {
      logger.info(id + " " + logBuffer.substring(0, logBuffer.length() - 1));
      logBuffer.setLength(0);
    }
  }

  private void getContainerLog(Handler<AsyncResult<Void>> future) {
    final String url = dockerUrl + "/containers/" + containerId
      + "/logs?stderr=1&stdout=1&follow=1";
    HttpClientRequest req = client.getAbs(url, res -> {
      if (res.statusCode() == 200) {
        // stream OK. Continue other work but keep fetching!
        // remove 8 bytes of binary data and final newline
        res.handler(this::logHandler);
        future.handle(Future.succeededFuture());
      } else {
        String m = "getContainerLog HTTP error "
          + Integer.toString(res.statusCode());
        logger.error(m);
        future.handle(Future.failedFuture(m));
      }
    });
    req.exceptionHandler(d -> future.handle(Future.failedFuture(d.getCause())));
    req.end();
  }

  private void getUrl(String url, Handler<AsyncResult<JsonObject>> future) {
    HttpClientRequest req = client.getAbs(url, res -> {
      Buffer body = Buffer.buffer();
      res.exceptionHandler(d -> {
        logger.warn(url + ": " + d.getMessage());
        future.handle(Future.failedFuture(url + ": " + d.getMessage()));
      });
      res.handler(body::appendBuffer);
      res.endHandler(d -> {
        if (res.statusCode() == 200) {
          JsonObject b = body.toJsonObject();
          logger.info(b.encodePrettily());
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

  private void getImage(Handler<AsyncResult<JsonObject>> future) {
    getUrl(dockerUrl + "/images/" + image + "/json", future);
  }

  private void pullImage(Handler<AsyncResult<Void>> future) {
    logger.info("pull image " + image);
    postUrlBody(dockerUrl + "/images/create?fromImage=" + image, "", future);
  }

  private void postUrlBody(String url, String doc,
    Handler<AsyncResult<Void>> future) {
    HttpClientRequest req = client.postAbs(url, res -> {
      Buffer body = Buffer.buffer();
      res.exceptionHandler(d -> future.handle(Future.failedFuture(d.getCause())));
      res.handler(body::appendBuffer);
      res.endHandler(d -> {
        if (res.statusCode() >= 200 && res.statusCode() <= 201) {
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
    req.exceptionHandler(d -> future.handle(Future.failedFuture(d.getCause())));
    req.putHeader("Content-Type", "application/json");
    req.end(doc);
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
      for (String aCmd : cmd) {
        a.add(aCmd);
      }
      j.put("Cmd", a);
    }
    if (dockerArgs != null) {
      for (Map.Entry<String, Object> entry : dockerArgs.properties().entrySet()) {
        j.put(entry.getKey(), entry.getValue());
      }
    }
    String doc = j.encodePrettily();
    doc = doc.replace("%p", Integer.toString(hostPort));
    logger.info("createContainer\n" + doc);
    postUrlBody(dockerUrl + "/containers/create", doc, future);
  }

  private int getExposedPort(JsonObject b) {
    JsonObject config = b.getJsonObject("Config");
    if (config == null) {
      throw (new IllegalArgumentException(messages.getMessage("11302")));
    }
    JsonObject exposedPorts = config.getJsonObject("ExposedPorts");
    if (exposedPorts == null) {
      throw (new IllegalArgumentException(messages.getMessage("11301")));
    }
    int exposedPort = 0;
    Iterator<Map.Entry<String, Object>> iterator = exposedPorts.iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, Object> next = iterator.next();
      String key = next.getKey();
      String sPort = key.split("/")[0];
      if (exposedPort == 0) {
        exposedPort = Integer.valueOf(sPort);
      }
    }
    return exposedPort;
  }

  private void prepareContainer(Handler<AsyncResult<Void>> startFuture) {
    getImage(res1 -> {
      if (res1.failed()) {
        logger.warn("getImage failed 1 : " + res1.cause().getMessage());
        startFuture.handle(Future.failedFuture(res1.cause()));
      } else {
        if (hostPort == 0) {
          startFuture.handle(Future.failedFuture(messages.getMessage("11300")));
          return;
        }
        int exposedPort = 0;
        try {
          exposedPort = getExposedPort(res1.result());
        } catch (Exception ex) {
          startFuture.handle(Future.failedFuture(ex));
          return;
        }
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
    });
  }

  @Override
  public void start(Handler<AsyncResult<Void>> startFuture) {
    if (dockerPull) {
      pullImage(res -> prepareContainer(startFuture));
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
