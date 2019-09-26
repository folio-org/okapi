package org.folio.okapi;

import com.hazelcast.config.Config;
import com.hazelcast.config.ClasspathXmlConfig;
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
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.spi.cluster.hazelcast.ConfigUtil;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import static java.lang.System.*;
import static java.lang.Integer.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.util.DropwizardHelper;
import org.folio.okapi.common.Messages;

@java.lang.SuppressWarnings({"squid:S3776"})
public class MainDeploy {

  private static final String CANNOT_LOAD_STR = "Cannot load ";

  private VertxOptions vopt = new VertxOptions();
  private Config hConfig = null;
  private JsonObject conf;
  private String clusterHost = null;
  private int clusterPort = -1;
  private Messages messages = Messages.getInstance();

  public MainDeploy() {
    this.conf = new JsonObject();
  }

  public MainDeploy(JsonObject conf) {
    this.conf = conf;
  }

  public void init(String[] args, Handler<AsyncResult<Vertx>> fut) {
    final Logger logger = OkapiLogger.get();
    Messages.setLanguage(getProperty("lang", "en"));

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
        deploy(new MainVerticle(), Vertx.vertx(vopt), fut);
        break;
      case "cluster":
      case "proxy":
      case "deployment":
        deployClustered(logger, fut);
        break;
      default:
        fut.handle(Future.failedFuture(messages.getMessage("10601", mode)));
    }
  }

  private boolean readConf(String fname, Handler<AsyncResult<Vertx>> fut) {
    try {
      byte[] encoded = Files.readAllBytes(Paths.get(fname));
      this.conf = new JsonObject(new String(encoded, StandardCharsets.UTF_8));
    } catch (IOException ex) {
      fut.handle(Future.failedFuture(CANNOT_LOAD_STR + fname));
      return true;
    }
    return false;
  }

  private void enableMetrics() {
    final String graphiteHost = getProperty("graphiteHost", "localhost");
    final Integer graphitePort = parseInt(
      getProperty("graphitePort", "2003"));
    final TimeUnit tu = TimeUnit.valueOf(getProperty("reporterTimeUnit", "SECONDS"));
    final Integer reporterPeriod = parseInt(getProperty("reporterPeriod", "1"));
    final String hostName = getProperty("host", "localhost");
    DropwizardHelper.config(graphiteHost, graphitePort, tu, reporterPeriod, vopt, hostName);
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
          hConfig = new ClasspathXmlConfig(resource);
        } catch (Exception e) {
          fut.handle(Future.failedFuture(CANNOT_LOAD_STR + resource + ": " + e));
          return true;
        }
      } else if ("-hazelcast-config-file".equals(args[i]) && i < args.length - 1) {
        String resource = args[++i];
        try {
          hConfig = new FileSystemXmlConfig(resource);
        } catch (Exception e) {
          fut.handle(Future.failedFuture(CANNOT_LOAD_STR + resource + ": " + e));
          return true;
        }
      } else if ("-hazelcast-config-url".equals(args[i]) && i < args.length - 1) {
        String resource = args[++i];
        try {
          hConfig = new UrlXmlConfig(resource);
        } catch (Exception e) {
          fut.handle(Future.failedFuture(CANNOT_LOAD_STR + resource + ": " + e));
          return true;
        }
      } else if ("-cluster-host".equals(args[i]) && i < args.length - 1) {
        clusterHost = args[++i];
      } else if ("-cluster-port".equals(args[i]) && i < args.length - 1) {
        clusterPort = Integer.parseInt(args[++i]);
      } else if ("-enable-metrics".equals(args[i])) {
        enableMetrics();
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

  private void printUsage() {
    out.println("Usage: command [options]\n"
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
      + "  -cluster-port port            Vertx cluster port\n"
      + "  -enable-metrics\n"
    );
  }

  private void deployClustered(final Logger logger, Handler<AsyncResult<Vertx>> fut) {
    if (hConfig == null) {
      hConfig = new Config();  
      if (clusterHost != null) {
        NetworkConfig network = hConfig.getNetworkConfig();
        InterfacesConfig iFace = network.getInterfaces();
        iFace.setEnabled(true).addInterface(clusterHost);
      }
      else {
        clusterHost = "0.0.0.0"
      }
    }
    hConfig.setProperty("hazelcast.logging.type", "slf4j");

    HazelcastClusterManager mgr = new HazelcastClusterManager(hConfig);
    vopt.setClusterManager(mgr);
    if (clusterHost != null) {
      logger.info("clusterHost=" + clusterHost);
      vopt.setClusterHost(clusterHost);
    } else {
      logger.warn("clusterHost not set");
    }
    if (clusterPort != -1) {
      logger.info("clusterPort=" + clusterPort);
      vopt.setClusterPort(clusterPort);
    } else {
      logger.warn("clusterPort not set");
    }
    vopt.setClustered(true);

    Vertx.clusteredVertx(vopt, res -> {
      if (res.succeeded()) {
        MainVerticle v = new MainVerticle();
        v.setClusterManager(mgr);
        deploy(v, res.result(), fut);
      } else {
        fut.handle(Future.failedFuture(res.cause()));
      }
    });
  }

  private void deploy(Verticle v, Vertx vertx, Handler<AsyncResult<Vertx>> fut) {
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
