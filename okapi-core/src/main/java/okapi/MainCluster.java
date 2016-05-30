/*
 * Copyright (C) 2016 Index Data
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
import io.vertx.core.json.JsonObject;
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
import okapi.util.DropwizardHelper;

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
      }
      hConfig.setProperty("hazelcast.logging.type", "slf4j");

      ClusterManager mgr = new HazelcastClusterManager(hConfig);
      vopt.setClusterManager(mgr);
      Vertx.clusteredVertx(vopt, res -> {
        if (res.succeeded()) {
          Vertx vertx = res.result();
          DeploymentOptions opt = new DeploymentOptions().setConfig(conf);
          vertx.deployVerticle(MainVerticle.class.getName(), opt, dep -> {
            if (dep.failed()) {
              exit(1);
            }
          });
        } else {
          err.println("Failed to create a clustered vert.x");
          // We probably should not use logging here, as it depends
          // on vert.x, which just failed to start!
        }
      });
    }
  }
}
