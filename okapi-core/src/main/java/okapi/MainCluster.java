/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.hazelcast.config.Config;
import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.FileSystemXmlConfig;
import com.hazelcast.config.UrlXmlConfig;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import static java.lang.System.*;
import static java.lang.Integer.*;
import okapi.util.DropwizardConfig;

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
    for (int i = 1; i < args.length; i++) {
      if ("-hazelcast-config-cp".equals(args[i]) && i < args.length - 1) {
        i++;
        String resource = args[i];
        try {
          hConfig = new ClasspathXmlConfig(resource);
        } catch (Exception e) {
          err.println("Cannot load " + resource + ": " + e.getMessage());
          exit(1);
        }
      } else if ("-hazelcast-config-file".equals(args[i]) && i < args.length - 1) {
        i++;
        String resource = args[i];
        try {
          hConfig = new FileSystemXmlConfig(resource);
        } catch (Exception e) {
          err.println("Cannot load " + resource + ": " + e.getMessage());
          exit(1);
        }
      } else if ("-hazelcast-config-url".equals(args[i]) && i < args.length - 1) {
        i++;
        String resource = args[i];
        try {
        hConfig = new UrlXmlConfig(resource);
        } catch (Exception e) {
          err.println("Cannot load " + resource + ": " + e.getMessage());
          exit(1);
        }
      } else if ("-enable-metrics".equals(args[i])) {
        i++;
        final String graphiteHost = getProperty("graphiteHost", "localhost");
        final Integer graphitePort = parseInt(
                getProperty("graphitePort", "2003"));
        final TimeUnit tu = TimeUnit.valueOf(getProperty("reporterTimeUnit", "SECONDS"));
        final Integer reporterPeriod = parseInt(getProperty("reporterPeriod", "1"));

        DropwizardConfig.config(graphiteHost, graphitePort, tu, reporterPeriod, vopt);
      } else {
        err.println("Invalid option: " + args[i]);
        exit(1);
      }
    }
    if ("help".equals(args[0])) {
      out.println("Usage: command [options]\n" +
              "Commands:\n" +
               "  help      Display help\n" +
               "  cluster   Run in clustered mode\n" +
               "  dev       Dev mode\n" +
               "Options:\n" +
               "  -hazelcast-config-cp file     Read config from class path\n" +
               "  -hazelcast-config-file file   Read config from local file\n" +
               "  -hazelcast-config-url url     Read config from URL\n" +
               "  -enable-metrics\n"
              );
      exit(0);
    } else if ("dev".equals(args[0])) {
      Vertx vertx = Vertx.vertx(vopt);
      DeploymentOptions opt = new DeploymentOptions();
      vertx.deployVerticle(MainVerticle.class.getName(), opt);
    } else if ("cluster".equals(args[0])) {
      if (hConfig == null) {
        hConfig = new Config();
      }
      hConfig.setProperty("hazelcast.logging.type", "slf4j");

      ClusterManager mgr = new HazelcastClusterManager(hConfig);
      vopt.setClusterManager(mgr);
      Vertx.clusteredVertx(vopt, res -> {
        if (res.succeeded()) {
          Vertx vertx = res.result();
          DeploymentOptions opt = new DeploymentOptions();
          vertx.deployVerticle(MainVerticle.class.getName(), opt);
        } else {
          err.println("Failed to create a clustered vert.x");
          // We probably should not use logging here, as it depends
          // on vert.x, which just failed to start!
        }
      });
    } else {
      err.println("Unknown command / option" + args[0]);
      exit(1);
    }
  }
}
