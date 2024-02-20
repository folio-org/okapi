package org.folio.okapi;

import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.FileSystemXmlConfig;
import com.hazelcast.config.InterfacesConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.UrlXmlConfig;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxBuilder;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.file.FileSystemOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.spi.cluster.hazelcast.ConfigUtil;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.MetricsUtil;
import org.folio.okapi.common.OkapiLogger;

@java.lang.SuppressWarnings({"squid:S3776"})
public class MainDeploy {

  private static final Logger logger = OkapiLogger.get(MainDeploy.class);

  private static final String CANNOT_LOAD_STR = "Cannot load ";

  private final VertxOptions vopt = new VertxOptions();
  private Config hazelcastConfig = null;
  private JsonObject conf;
  private String clusterHost = null;
  private int clusterPort = -1;
  private final Messages messages = Messages.getInstance();

  public MainDeploy() {
    this.conf = new JsonObject();
  }

  public MainDeploy(JsonObject conf) {
    this.conf = conf;
  }

  // suppress "Catch Exception instead of Throwable" to also log Throwable
  @SuppressWarnings({"squid:S1181"})
  void init(String[] args, Handler<AsyncResult<Vertx>> fut) {
    vopt.setPreferNativeTransport(true);
    try {
      Messages.setLanguage(System.getProperty("lang", "en"));
      if (args.length < 1) {
        printUsage();
        fut.handle(Future.failedFuture(messages.getMessage("10600")));
        return;
      }
      if (parseOptions(args, fut)) {
        return;
      }
      final String mode = conf.getString("mode", "dev");
      switch (mode) {
        case "dev":
        case "initdatabase":
        case "purgedatabase":
          deploy(false, fut);
          break;
        case "cluster":
        case "proxy":
        case "deployment":
          deploy(true, fut);
          break;
        default:
          fut.handle(Future.failedFuture(messages.getMessage("10601", mode)));
      }
    } catch (Throwable t) {
      String message = t.getMessage();
      if ("Failed to create cache dir".equals(message)) {
        // https://issues.folio.org/browse/OKAPI-857 Okapi crashes on user change
        // https://github.com/eclipse-vertx/vert.x/blob/3.9.1/src/main/java/io/vertx/core/file/FileSystemOptions.java#L49
        message += " " + FileSystemOptions.DEFAULT_FILE_CACHING_DIR;
        t = new RuntimeException(message, t);
      }
      fut.handle(Future.failedFuture(t));
    }
  }

  private boolean readConf(String fileName, Handler<AsyncResult<Vertx>> fut) {
    try {
      byte[] encoded = Files.readAllBytes(Paths.get(fileName));
      this.conf = new JsonObject(new String(encoded, StandardCharsets.UTF_8));
    } catch (IOException ex) {
      fut.handle(Future.failedFuture(CANNOT_LOAD_STR + fileName));
      return true;
    }
    return false;
  }

  private boolean parseOptions(String[] args, Handler<AsyncResult<Vertx>> fut) {
    int i = 0;
    String mode = null;
    while (i < args.length) {
      if (!args[i].startsWith("-")) {
        if ("help".equals(args[i])) {
          printUsage();
          fut.handle(Future.succeededFuture(null));
          return true;
        }
        mode = args[i];
      } else if ("-hazelcast-config-cp".equals(args[i]) && i < args.length - 1) {
        String resource = args[++i];
        try {
          hazelcastConfig = new ClasspathXmlConfig(resource);
        } catch (Exception e) {
          fut.handle(Future.failedFuture(CANNOT_LOAD_STR + resource + ": " + e));
          return true;
        }
      } else if ("-hazelcast-config-file".equals(args[i]) && i < args.length - 1) {
        String resource = args[++i];
        try {
          hazelcastConfig = new FileSystemXmlConfig(resource);
        } catch (Exception e) {
          fut.handle(Future.failedFuture(CANNOT_LOAD_STR + resource + ": " + e));
          return true;
        }
      } else if ("-hazelcast-config-url".equals(args[i]) && i < args.length - 1) {
        String resource = args[++i];
        try {
          hazelcastConfig = new UrlXmlConfig(resource);
        } catch (Exception e) {
          fut.handle(Future.failedFuture(CANNOT_LOAD_STR + resource + ": " + e));
          return true;
        }
      } else if ("-cluster-host".equals(args[i]) && i < args.length - 1) {
        clusterHost = args[++i];
      } else if ("-cluster-port".equals(args[i]) && i < args.length - 1) {
        clusterPort = Integer.parseInt(args[++i]);
      } else if ("-conf".equals(args[i]) && i < args.length - 1) {
        if (readConf(args[++i], fut)) {
          return true;
        }
      } else {
        fut.handle(Future.failedFuture(messages.getMessage("10602", args[i])));
        return true;
      }
      i++;
    }
    if (mode != null) {
      conf.put("mode", mode);
    }
    return false;
  }

  // Suppress "Standard outputs should not be used directly"
  @java.lang.SuppressWarnings({"squid:S106"})
  private void printUsage() {
    System.out.println("Usage: command [options]\n"
        + "Commands:\n"
        + "  help         Display help\n"
        + "  cluster      Run in clustered mode\n"
        + "  dev          Development mode\n"
        + "  deployment   Deployment only. Clustered mode\n"
        + "  proxy        Proxy + discovery. Clustered mode\n"
        + "Options:\n"
        + "  -conf file                    Read Okapi configuration from local file\n"
        + "  -hazelcast-config-cp file     Read Hazelcast config from class path\n"
        + "  -hazelcast-config-file file   Read Hazelcast config from local file\n"
        + "  -hazelcast-config-url url     Read Hazelcast config from URL\n"
        + "  -cluster-host ip              Vertx cluster host\n"
        + "  -cluster-port port            Vertx cluster port\n");
  }

  private void deploy(boolean clustered, Handler<AsyncResult<Vertx>> fut) {
    MetricsUtil.init(vopt);
    if (clustered) {
      deployClustered(fut);
    } else {
      deployVerticle(new MainVerticle(), Vertx.vertx(vopt), fut);
    }
  }

  private void deployClustered(Handler<AsyncResult<Vertx>> fut) {
    if (hazelcastConfig == null) {
      hazelcastConfig = ConfigUtil.loadConfig();
      if (clusterHost != null) {
        NetworkConfig network = hazelcastConfig.getNetworkConfig();
        InterfacesConfig interfacesConfig = network.getInterfaces();
        interfacesConfig.setEnabled(true).addInterface(clusterHost);
      }
    }
    hazelcastConfig.setProperty("hazelcast.logging.type", "log4j2");

    HazelcastClusterManager mgr = new HazelcastClusterManager(hazelcastConfig);
    EventBusOptions eventBusOptions = vopt.getEventBusOptions();
    if (clusterHost != null) {
      logger.info("clusterHost={}", clusterHost);
      eventBusOptions.setHost(clusterHost);
    } else {
      logger.warn("clusterHost not set");
    }
    if (clusterPort != -1) {
      logger.info("clusterPort={}", clusterPort);
      eventBusOptions.setPort(clusterPort);
    } else {
      logger.warn("clusterPort not set");
    }

    Vertx.builder().with(vopt).withClusterManager(mgr).buildClustered()
    .onSuccess(vertx -> {
      MainVerticle v = new MainVerticle();
      v.setClusterManager(mgr);
      deployVerticle(v, vertx, fut);
    })
    .onFailure(e -> fut.handle(Future.failedFuture(e)));
  }

  private void deployVerticle(Verticle v, Vertx vertx, Handler<AsyncResult<Vertx>> fut) {
    DeploymentOptions opt = new DeploymentOptions().setConfig(conf);
    vertx.deployVerticle(v, opt, dep -> {
      if (dep.failed()) {
        fut.handle(Future.failedFuture(dep.cause()));
      } else {
        fut.handle(Future.succeededFuture(vertx));
      }
    });
  }
}
