package org.folio.okapi.service.impl;

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
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import java.util.Iterator;
import java.util.Map;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.AnyDescriptor;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.bean.Ports;
import org.folio.okapi.common.Config;
import org.folio.okapi.common.HttpClientLegacy;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.service.ModuleHandle;
import org.folio.okapi.util.TcpPortWaiting;

@java.lang.SuppressWarnings({"squid:S1192"})
public class DockerModuleHandle implements ModuleHandle {

  private final Logger logger = OkapiLogger.get();

  private final int hostPort;
  private final Ports ports;
  private final String image;
  private final String[] cmd;
  private final String dockerUrl;
  private final String containerHost;
  private final EnvEntry[] env;
  private final AnyDescriptor dockerArgs;
  private final boolean dockerPull;
  private final HttpClient client;
  private final StringBuilder logBuffer;
  private int logSkip;
  private final String id;
  private final Messages messages = Messages.getInstance();
  private final TcpPortWaiting tcpPortWaiting;
  private String containerId;
  private final SocketAddress socketAddress;
  static final String DEFAULT_DOCKER_URL = "unix:///var/run/docker.sock";
  static final String DEFAULT_DOCKER_VERSION = "v1.25";

  public DockerModuleHandle(Vertx vertx, LaunchDescriptor desc,
    String id, Ports ports, String containerHost, int port, JsonObject config) {
    this.hostPort = port;
    this.ports = ports;
    this.id = id;
    this.containerHost = containerHost;
    this.image = desc.getDockerImage();
    this.cmd = desc.getDockerCMD();
    this.env = desc.getEnv();
    this.dockerArgs = desc.getDockerArgs();
    this.client = vertx.createHttpClient();
    this.logBuffer = new StringBuilder();
    this.logSkip = 0;
    logger.info("Docker handler with native: {}", vertx.isNativeTransportEnabled());
    Boolean b = desc.getDockerPull();
    this.dockerPull = b == null || b.booleanValue();
    StringBuilder socketFile = new StringBuilder();
    this.dockerUrl = setupDockerAddress(socketFile,
      Config.getSysConf("dockerUrl", DEFAULT_DOCKER_URL, config));
    if (socketFile.length() > 0) {
      socketAddress = SocketAddress.domainSocketAddress(socketFile.toString());
    } else {
      socketAddress = null;
    }
    tcpPortWaiting = new TcpPortWaiting(vertx, containerHost, port);
    if (desc.getWaitIterations() != null) {
      tcpPortWaiting.setMaxIterations(desc.getWaitIterations());
    }
  }

  static String setupDockerAddress(StringBuilder socketAddress, String u) {
    if (u.startsWith("tcp://")) {
      u = "http://" + u.substring(6);
    } else if (u.startsWith("unix://")) {
      socketAddress.append(u.substring(7));
      u = "http://localhost";
    }
    while (u.endsWith("/")) {
      u = u.substring(0, u.length() - 1);
    }
    return u + "/" + DEFAULT_DOCKER_VERSION;
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

  private HttpClientRequest request(HttpMethod method, String url,
    Handler<HttpClientResponse> response) {
    if (socketAddress != null) {
      return HttpClientLegacy.requestAbs(client, method, socketAddress, dockerUrl + url, response);
    } else {
      return HttpClientLegacy.requestAbs(client, method, url, response);
    }
  }

  void postUrl(String url, String msg,
    Handler<AsyncResult<Void>> future) {

    HttpClientRequest req = request(HttpMethod.POST, url,
      res -> handle204(res, msg, future));
    req.exceptionHandler(d -> future.handle(Future.failedFuture(d.getCause())));
    req.end();
  }

  void deleteUrl(String url, String msg,
    Handler<AsyncResult<Void>> future) {

    HttpClientRequest req = request(HttpMethod.DELETE, url,
      res -> handle204(res, msg, future));
    req.exceptionHandler(d -> future.handle(Future.failedFuture(d.getCause())));
    req.end();
  }

  private void startContainer(Handler<AsyncResult<Void>> future) {
    logger.info("start container {} for image {}", containerId, image);
    postUrl("/containers/" + containerId + "/start", "startContainer", future);
  }

  private void stopContainer(Handler<AsyncResult<Void>> future) {
    logger.info("stop container {} image {}", containerId, image);
    postUrl("/containers/" + containerId + "/stop", "stopContainer", future);
  }

  private void deleteContainer(Handler<AsyncResult<Void>> future) {
    logger.info("delete container {} image {}", containerId, image);
    deleteUrl("/containers/" + containerId, "deleteContainer", future);
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
      logger.info("{} {}", () -> id, () -> logBuffer.substring(0, logBuffer.length() - 1));
      logBuffer.setLength(0);
    }
  }

  private void getContainerLog(Handler<AsyncResult<Void>> future) {
    final String url = "/containers/" + containerId
      + "/logs?stderr=1&stdout=1&follow=1";
    HttpClientRequest req = request(HttpMethod.GET, url, res -> {
      if (res.statusCode() == 200) {
        // stream OK. Continue other work but keep fetching!
        // remove 8 bytes of binary data and final newline
        res.handler(this::logHandler);
        tcpPortWaiting.waitReady(null, future);
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

  void getUrl(String url, Handler<AsyncResult<JsonObject>> future) {
    HttpClientRequest req = request(HttpMethod.GET, url, res -> {
      Buffer body = Buffer.buffer();
      res.exceptionHandler(d -> {
        logger.warn("{}: {}", url, d.getMessage());
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
      logger.warn("{}: {}", url, d.getMessage());
      future.handle(Future.failedFuture(url + ": " + d.getMessage()));
    });
    req.end();
  }

  private void getImage(Handler<AsyncResult<JsonObject>> future) {
    getUrl("/images/" + image + "/json", future);
  }

  private void pullImage(Handler<AsyncResult<Void>> future) {
    logger.info("pull image {}", image);
    postUrlJson("/images/create?fromImage=" + image, "pullImage", "", future);
  }

  void postUrlJson(String url, String msg, String doc,
    Handler<AsyncResult<Void>> future) {

    HttpClientRequest req = request(HttpMethod.POST, url, res -> {
      Buffer body = Buffer.buffer();
      res.exceptionHandler(d -> future.handle(Future.failedFuture(d.getCause())));
      res.handler(body::appendBuffer);
      res.endHandler(d -> {
        if (res.statusCode() >= 200 && res.statusCode() <= 201) {
          containerId = body.toJsonObject().getString("Id");
          future.handle(Future.succeededFuture());
        } else {
          String m = msg + " HTTP error "
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
    logger.info("create container from image {}", image);
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
    j.put("NetworkDisabled", false);
    JsonObject hp = new JsonObject().put("HostPort", Integer.toString(hostPort));
    JsonArray ep = new JsonArray().add(hp);
    JsonObject pb = new JsonObject();
    pb.put(Integer.toString(exposedPort) + "/tcp", ep);
    JsonObject hc = new JsonObject();
    hc.put("PortBindings", pb);
    hc.put("PublishAllPorts", Boolean.FALSE);
    hc.put("NetworkMode", "bridge");
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
    doc = doc.replace("%p", Integer.toString(hostPort)).replace("%c", containerHost);
    logger.info("createContainer {}", doc);
    postUrlJson("/containers/create", "createContainer", doc, future);
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
        logger.warn("getImage failed 1 : {}", res1.cause().getMessage());
        startFuture.handle(Future.failedFuture(res1.cause()));
        return;
      }
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
          startFuture.handle(res2);
          return;
        }
        startContainer(res3 -> {
          if (res3.failed()) {
            deleteContainer(x -> startFuture.handle(res3));
            return;
          }
          getContainerLog(res4 -> {
            if (res4.failed()) {
              this.stop(x -> startFuture.handle(res4));
              return;
            }
            startFuture.handle(res4);
          });
        });
      });
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
