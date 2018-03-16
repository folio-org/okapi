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
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import java.util.concurrent.TimeUnit;
import static java.lang.System.*;
import static java.lang.Integer.*;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.util.DropwizardHelper;

public class MainDeploy {

  private static final String CANNOT_LOAD_STR = "Cannot load ";

  private VertxOptions vopt = new VertxOptions();
  private Config hConfig = null;
  private final JsonObject conf;
  private String clusterHost = null;
  private int clusterPort = -1;

  public MainDeploy() {
    this.conf = new JsonObject();
  }

  public MainDeploy(JsonObject conf) {
    this.conf = conf;
  }

  public void init(String[] args, Handler<AsyncResult<Vertx>> fut) {
    final Logger logger = OkapiLogger.get();

    if (args.length < 1) {
      fut.handle(Future.failedFuture("Missing command; use help"));
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
        fut.handle(Future.failedFuture("Unknown command '" + mode + "'"));
    }
  }

  private boolean parseOptions(String[] args, Handler<AsyncResult<Vertx>> fut) {
    int i = 0;
    while (i < args.length) {
      if (!args[i].startsWith("-")) {
        if ("help".equals(args[i])) {
          out.println("Usage: command [options]\n"
            + "Commands:\n"
            + "  help         Display help\n"
            + "  cluster      Run in clustered mode\n"
            + "  dev          Development mode\n"
            + "  deployment   Deployment only. Clustered mode\n"
            + "  proxy        Proxy + discovery. Clustered mode\n"
            + "Options:\n"
            + "  -hazelcast-config-cp file     Read config from class path\n"
            + "  -hazelcast-config-file file   Read config from local file\n"
            + "  -hazelcast-config-url url     Read config from URL\n"
            + "  -cluster-host ip              Vertx cluster host\n"
            + "  -cluster-port port            Vertx cluster port\n"
            + "  -enable-metrics\n"
          );
          fut.handle(Future.succeededFuture(null));
          return true;
        }
        conf.put("mode", args[i]);
      } else if ("-hazelcast-config-cp".equals(args[i]) && i < args.length - 1) {
        i++;
        String resource = args[i];
        try {
          hConfig = new ClasspathXmlConfig(resource);
        } catch (Exception e) {
          fut.handle(Future.failedFuture(CANNOT_LOAD_STR + resource + ": " + e));
          return true;
        }
      } else if ("-hazelcast-config-file".equals(args[i]) && i < args.length - 1) {
        i++;
        String resource = args[i];
        try {
          hConfig = new FileSystemXmlConfig(resource);
        } catch (Exception e) {
          fut.handle(Future.failedFuture(CANNOT_LOAD_STR + resource + ": " + e));
          return true;
        }
      } else if ("-hazelcast-config-url".equals(args[i]) && i < args.length - 1) {
        i++;
        String resource = args[i];
        try {
          hConfig = new UrlXmlConfig(resource);
        } catch (Exception e) {
          fut.handle(Future.failedFuture(CANNOT_LOAD_STR + resource + ": " + e));
          return true;
        }
      } else if ("-cluster-host".equals(args[i]) && i < args.length - 1) {
        i++;
        clusterHost = args[i];
      } else if ("-cluster-port".equals(args[i]) && i < args.length - 1) {
        i++;
        clusterPort = Integer.parseInt(args[i]);
      } else if ("-enable-metrics".equals(args[i])) {
        final String graphiteHost = getProperty("graphiteHost", "localhost");
        final Integer graphitePort = parseInt(
          getProperty("graphitePort", "2003"));
        final TimeUnit tu = TimeUnit.valueOf(getProperty("reporterTimeUnit", "SECONDS"));
        final Integer reporterPeriod = parseInt(getProperty("reporterPeriod", "1"));
        final String hostName = getProperty("host", "localhost");
        DropwizardHelper.config(graphiteHost, graphitePort, tu, reporterPeriod, vopt, hostName);
      } else {
        fut.handle(Future.failedFuture("Invalid option: " + args[i]));
        return true;
      }
      i++;
    }
    return false;
  }

  private void deployClustered(final Logger logger, Handler<AsyncResult<Vertx>> fut) {
    if (hConfig == null) {
      hConfig = new Config();
      if (clusterHost != null) {
        NetworkConfig network = hConfig.getNetworkConfig();
        InterfacesConfig iFace = network.getInterfaces();
        iFace.setEnabled(true).addInterface(clusterHost);
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
