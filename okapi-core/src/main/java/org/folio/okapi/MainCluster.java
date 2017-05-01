package org.folio.okapi;

import com.hazelcast.config.Config;
import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.FileSystemXmlConfig;
import com.hazelcast.config.InterfacesConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.UrlXmlConfig;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import java.util.concurrent.TimeUnit;
import static java.lang.System.*;
import static java.lang.Integer.*;
import org.folio.okapi.util.DropwizardHelper;

public class MainCluster {

  public static void main(String[] args) {
    setProperty("vertx.logger-delegate-factory-class-name",
            "io.vertx.core.logging.SLF4JLogDelegateFactory");

    final Logger logger = LoggerFactory.getLogger("okapi");

    if (args.length < 1) {
      err.println("Missing command; use help");
      exit(1);
    }
    VertxOptions vopt = new VertxOptions();
    Config hConfig = null;
    JsonObject conf = new JsonObject();
    String clusterHost = null;
    int clusterPort = -1;

    for (int i = 0; i < args.length; i++) {
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
          exit(0);
        }
        conf.put("mode", args[i]);
      } else if ("-hazelcast-config-cp".equals(args[i]) && i < args.length - 1) {
        i++;
        String resource = args[i];
        try {
          hConfig = new ClasspathXmlConfig(resource);
        } catch (Exception e) {
          logger.error("Cannot load " + resource + ": " + e);
          exit(1);
        }
      } else if ("-hazelcast-config-file".equals(args[i]) && i < args.length - 1) {
        i++;
        String resource = args[i];
        try {
          hConfig = new FileSystemXmlConfig(resource);
        } catch (Exception e) {
          logger.error("Cannot load " + resource + ": " + e);
          exit(1);
        }
      } else if ("-hazelcast-config-url".equals(args[i]) && i < args.length - 1) {
        i++;
        String resource = args[i];
        try {
          hConfig = new UrlXmlConfig(resource);
        } catch (Exception e) {
          logger.error("Cannot load " + resource + ": " + e);
          exit(1);
        }
      } else if ("-cluster-host".equals(args[i]) && i < args.length - 1) {
        i++;
        clusterHost = args[i];
      } else if ("-cluster-port".equals(args[i]) && i < args.length - 1) {
        i++;
        clusterPort = Integer.parseInt(args[i]);
      } else if ("-enable-metrics".equals(args[i])) {
        i++;
        final String graphiteHost = getProperty("graphiteHost", "localhost");
        final Integer graphitePort = parseInt(
                getProperty("graphitePort", "2003"));
        final TimeUnit tu = TimeUnit.valueOf(getProperty("reporterTimeUnit", "SECONDS"));
        final Integer reporterPeriod = parseInt(getProperty("reporterPeriod", "1"));
        final String hostName = getProperty("host", "localhost");
        DropwizardHelper.config(graphiteHost, graphitePort, tu, reporterPeriod, vopt, hostName);
      } else {
        err.println("Invalid option: " + args[i]);
        exit(1);
      }
    }
    if (conf.getString("mode", "dev").equals("dev")) {
      Vertx vertx = Vertx.vertx(vopt);
      DeploymentOptions opt = new DeploymentOptions().setConfig(conf);
      vertx.deployVerticle(MainVerticle.class.getName(), opt, dep -> {
        if (dep.failed()) {
          exit(1);
        }
      });
    } else {
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
          Vertx vertx = res.result();
          DeploymentOptions opt = new DeploymentOptions().setConfig(conf);
          MainVerticle v = new MainVerticle();
          v.setClusterManager(mgr);
          vertx.deployVerticle(v, opt, dep -> {
            if (dep.failed()) {
              exit(1);
            }
          });
        } else {
          logger.fatal(res.cause().getMessage());
          exit(1);
        }
      });
    }
  }
}
