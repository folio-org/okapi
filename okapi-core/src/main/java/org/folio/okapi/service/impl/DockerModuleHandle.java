package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.AnyDescriptor;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.bean.Ports;
import org.folio.okapi.common.Config;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.service.ModuleHandle;
import org.folio.okapi.util.TcpPortWaiting;
import org.folio.okapi.util.VariableSubstitutor;


// Docker Module. Using the Docker HTTP API.
// We don't do local unix sockets. The dockerd must unfortunately be listening on localhost.
// https://docs.docker.com/engine/reference/commandline/dockerd/#bind-docker-to-another-hostport-or-a-unix-socket

@java.lang.SuppressWarnings({"squid:S1192"})
public class DockerModuleHandle implements ModuleHandle {

  static final String DOCKER_REGISTRIES_EMPTY_LIST =
      "dockerRegistries=[] contains no registry and disables docker pull. "
          + "For unauthenticated pull from DockerHub use [ {} ].";

  private final Logger logger;

  private final int hostPort;
  private final Ports ports;
  private final String[] cmd;
  private final String dockerUrl;
  private final String containerHost;
  private final EnvEntry[] env;
  private final AnyDescriptor dockerArgs;
  private final boolean dockerPull;
  private final HttpClient client;
  private final StringBuilder logBuffer;
  private final JsonArray dockerRegistries;
  private int logSkip;
  private final String id;
  private final Messages messages = Messages.getInstance();
  private final TcpPortWaiting tcpPortWaiting;
  private String containerId;
  private final SocketAddress socketAddress;
  static final String DEFAULT_DOCKER_URL = "unix:///var/run/docker.sock";
  static final String DEFAULT_DOCKER_VERSION = "v1.35";
  private String image;

  DockerModuleHandle(Vertx vertx, LaunchDescriptor desc,
                     String id, Ports ports, String containerHost, int port, JsonObject config,
                     Logger logger) {
    this.logger = logger;
    this.hostPort = port;
    this.ports = ports;
    this.id = id;
    this.containerHost = containerHost;
    this.image = desc.getDockerImage();
    this.cmd = desc.getDockerCmd();
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
    dockerRegistries = config.getJsonArray("dockerRegistries");
    tcpPortWaiting = new TcpPortWaiting(vertx, containerHost, port);
    if (desc.getWaitIterations() != null) {
      tcpPortWaiting.setMaxIterations(desc.getWaitIterations());
    }
  }

  DockerModuleHandle(Vertx vertx, LaunchDescriptor desc,
      String id, Ports ports, String containerHost, int port, JsonObject config) {
    this(vertx, desc, id, ports, containerHost, port, config, OkapiLogger.get());
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

  private Future<Void> handle204(HttpClientResponse result, String msg) {
    Buffer body = Buffer.buffer();
    result.handler(body::appendBuffer);
    Promise<Void> promise = Promise.promise();
    result.endHandler(d -> {
      if (result.statusCode() == 204) {
        promise.complete();
      } else {
        String m = msg + " HTTP error "
            + result.statusCode() + "\n"
            + body.toString();
        logger.error(m);
        promise.fail(m);
      }
    });
    return promise.future();
  }

  private Future<HttpClientResponse> request(HttpMethod method, String url,
                                             MultiMap headers, Buffer body) {

    try {
      RequestOptions requestOptions = new RequestOptions()
          .setMethod(method)
          .setAbsoluteURI(dockerUrl + url);
      if (socketAddress != null) {
        requestOptions.setServer(socketAddress);
      }
      return client.request(requestOptions).compose(request -> {
        if (headers != null) {
          request.headers().setAll(headers);
        }
        return request.send(body);
      });
    } catch (Throwable e) {
      return Future.failedFuture(e);
    }
  }

  Future<Void> postUrl(String url, String msg) {
    return request(HttpMethod.POST, url, null, Buffer.buffer())
        .compose(res -> handle204(res, msg));
  }

  Future<Void> deleteUrl(String url, String msg) {
    return request(HttpMethod.DELETE, url, null, Buffer.buffer())
        .compose(res -> handle204(res, msg));
  }

  Future<Void> startContainer() {
    logger.info("start container {} for image {}", containerId, image);
    return postUrl("/containers/" + containerId + "/start", "startContainer");
  }

  Future<Void> stopContainer() {
    logger.info("stop container {} image {}", containerId, image);
    return postUrl("/containers/" + containerId + "/stop", "stopContainer");
  }

  Future<Void> deleteContainer() {
    logger.info("delete container {} image {}", containerId, image);
    return deleteUrl("/containers/" + containerId + "?force=true", "deleteContainer");
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

  Future<Void> getContainerLog() {
    final String url = "/containers/" + containerId
        + "/logs?stderr=1&stdout=1&follow=1";
    return request(HttpMethod.GET, url, null, Buffer.buffer()).compose(res -> {
      if (res.statusCode() == 200) {
        // stream OK. Continue other work but keep fetching!
        // remove 8 bytes of binary data and final newline
        res.handler(this::logHandler);
        return tcpPortWaiting.waitReady(null);
      } else {
        String m = "getContainerLog HTTP error "
            + res.statusCode();
        logger.error(m);
        return Future.failedFuture(m);
      }
    });
  }

  Future<JsonObject> getUrl(String url) {
    return request(HttpMethod.GET, url, null, Buffer.buffer())
        .compose(res -> {
          Buffer body = Buffer.buffer();
          Promise<JsonObject> promise = Promise.promise();
          res.exceptionHandler(d -> {
            logger.warn("{}: {}", url, d.getMessage());
            promise.fail(url + ": " + d.getMessage());
          });
          res.handler(body::appendBuffer);
          res.endHandler(d -> {
            if (res.statusCode() != 200) {
              String m = url + " HTTP error "
                  + res.statusCode() + "\n"
                  + body.toString();
              logger.error(m);
              promise.fail(m);
              return;
            }
            try {
              JsonObject b = body.toJsonObject();
              promise.complete(b);
            } catch (DecodeException e) {
              logger.warn("{}", e.getMessage(), e);
              promise.fail(e);
            }
          });
          return promise.future();
        });
  }

  private static String getRegistryPrefix(JsonObject registry) {
    String prefix = registry.getString("registry", "");
    if (!prefix.isEmpty() && !prefix.endsWith("/")) {
      return prefix + "/";
    }
    return prefix;
  }

  Future<JsonObject> getImage() {
    if (dockerRegistries == null) {
      return getUrl("/images/" + image + "/json");
    }
    if (dockerRegistries.isEmpty()) {
      logger.warn(DOCKER_REGISTRIES_EMPTY_LIST);
    }
    Future<JsonObject> future = Future.failedFuture(DOCKER_REGISTRIES_EMPTY_LIST);
    for (int i = 0; i < dockerRegistries.size(); i++) {
      JsonObject registry = dockerRegistries.getJsonObject(i);
      String prefix = getRegistryPrefix(registry);
      future = future.recover(x -> getUrl("/images/" + prefix + image + "/json")
          .onSuccess(y -> {
            image = prefix + image;
          }));
    }
    return future;
  }

  Future<Void> pullImage() {
    if (dockerRegistries == null) {
      logger.info("pull image {}", image);
      return postUrlJson("/images/create?fromImage=" + image, null, "pullImage", "")
          .mapEmpty();
    }
    logger.info("pull Image using dockerRegistries");
    if (dockerRegistries.isEmpty()) {
      logger.warn(DOCKER_REGISTRIES_EMPTY_LIST);
    }
    Future<Void> future = Future.failedFuture(DOCKER_REGISTRIES_EMPTY_LIST);
    for (int i = 0; i < dockerRegistries.size(); i++) {
      JsonObject registry = dockerRegistries.getJsonObject(i);
      if (registry == null) {
        continue;
      }
      JsonObject authObject = new JsonObject();
      for (String member : Arrays.asList(
          "username", "password", "email", "serveraddress", "identitytoken")) {
        String value = registry.getString(member);
        if (value != null) {
          authObject.put(member, value);
        }
      }
      future = future.recover(x -> {
        String prefix = getRegistryPrefix(registry);
        logger.info("pull image {}", prefix + image);
        return postUrlJson("/images/create?fromImage=" + prefix + image,
            authObject, "pullImage", "").mapEmpty();
      });
    }
    return future;
  }

  Future<Buffer> postUrlJson(String url, JsonObject auth, String msg, String doc) {
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    headers.add("Content-Type", "application/json");
    if (auth != null && auth.size() > 0) {
      headers.add("X-Registry-Auth",
          new String(Base64.getEncoder().encode(auth.encodePrettily().getBytes())));
    }
    return request(HttpMethod.POST, url, headers, Buffer.buffer(doc)).compose(res -> {
      Promise<Buffer> promise = Promise.promise();
      Buffer body = Buffer.buffer();
      res.exceptionHandler(d -> promise.fail(d));
      res.handler(body::appendBuffer);
      res.endHandler(d -> {
        if (res.statusCode() >= 200 && res.statusCode() <= 201) {
          promise.complete(body);
        } else {
          String m = msg + " HTTP error "
              + res.statusCode() + "\n"
              + body.toString();
          logger.error(m);
          promise.fail(m);
        }
      });
      return promise.future();
    });
  }

  String getCreateContainerDoc(int exposedPort) {
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
    pb.put(exposedPort + "/tcp", ep);
    JsonObject hc = new JsonObject();
    hc.put("PortBindings", pb);
    hc.put("PublishAllPorts", Boolean.FALSE);
    j.put("HostConfig", hc);

    if (this.cmd != null && this.cmd.length > 0) {
      JsonArray a = new JsonArray();
      for (String cmdElement : cmd) {
        a.add(cmdElement);
      }
      j.put("Cmd", a);
    }
    if (dockerArgs != null) {
      JsonObject dockerArgsJson = new JsonObject(dockerArgs.properties()).copy();
      VariableSubstitutor.replace(dockerArgsJson, Integer.toString(hostPort), containerHost);
      j.mergeIn(dockerArgsJson);
    }

    String doc = j.encodePrettily();

    if (logger.isInfoEnabled()) {
      String logDoc = doc;

      if (j.containsKey("env")) {
        // don't show env variables that may contain sensitive credentials in the log
        j.put("env", new JsonArray().add("..."));
        logDoc = j.encodePrettily();
      }

      // Cannot use a lambda because Mockito does not support
      // matching a Supplier vararg when an Object vararg exists
      logger.info("createContainer {}", logDoc);
    }

    return doc;
  }

  Future<Void> createContainer(int exposedPort) {
    logger.info("create container from image {}", image);

    String doc = getCreateContainerDoc(exposedPort);
    return postUrlJson("/containers/create", null,"createContainer", doc).compose(res -> {
      containerId = res.toJsonObject().getString("Id");
      return Future.succeededFuture();
    });
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
      String port = key.split("/")[0];
      if (exposedPort == 0) {
        exposedPort = Integer.valueOf(port);
      }
    }
    return exposedPort;
  }

  private Future<Void> prepareContainer() {
    if (hostPort == 0) {
      return Future.failedFuture(messages.getMessage("11300"));
    }
    return getImage().map(res -> {
      try {
        return getExposedPort(res);
      } catch (Exception e) {
        logger.warn("{}", e.getMessage(), e);
        throw e;
      }
    }).compose(this::createContainer)
        .compose(res -> startContainer().onFailure(cause -> deleteContainer()))
        .compose(res -> getContainerLog().onFailure(cause -> stop()));
  }

  @Override
  public Future<Void> start() {
    if (dockerPull) {
      // ignore error for pullImage.. if image is not present locally prepareContainer will fail
      return pullImage().recover(x -> Future.succeededFuture()).compose(x -> prepareContainer());
    }
    return prepareContainer();
  }

  @Override
  public Future<Void> stop() {
    return stopContainer()
        .compose(
            x -> deleteContainer(),
            // if stopContainer fails with e run deleteContainer but return original failure e
            e -> deleteContainer().onComplete(x -> Future.failedFuture(e)))
        .onComplete(x -> ports.free(hostPort));
  }
}
