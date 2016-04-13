/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi;

import com.hazelcast.config.Config;
import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.FileSystemXmlConfig;
import com.hazelcast.config.UrlXmlConfig;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class MainCluster {
  public static void main(String[] args) {
    System.setProperty("vertx.logger-delegate-factory-class-name",
            "io.vertx.core.logging.SLF4JLogDelegateFactory");
    if (args.length < 1) {
      System.err.println("Missing command; use help");
      System.exit(1);
    }
    Config hConfig = null;
    for (int i = 1; i < args.length; i++) {
      if ("-hazelcast-config-cp".equals(args[i]) && i < args.length - 1) {
        i++;
        String resource = args[i];
        try {
          hConfig = new ClasspathXmlConfig(resource);
        } catch (Exception e) {
          System.err.println("Cannot load " + resource + ": " + e.getMessage());
          System.exit(1);
        }
      } else if ("-hazelcast-config-file".equals(args[i]) && i < args.length - 1) {
        i++;
        String resource = args[i];
        try {
          hConfig = new FileSystemXmlConfig(resource);
        } catch (Exception e) {
          System.err.println("Cannot load " + resource + ": " + e.getMessage());
          System.exit(1);
        }
      } else if ("-hazelcast-config-url".equals(args[i]) && i < args.length - 1) {
        i++;
        String resource = args[i];
        try {
        hConfig = new UrlXmlConfig(resource);
        } catch (Exception e) {
          System.err.println("Cannot load " + resource + ": " + e.getMessage());
          System.exit(1);
        }
      } else {
        System.err.println("Invalid option: " + args[i]);
        System.exit(1);
      }
    }
    if ("help".equals(args[0])) {
      System.out.println("Usage: command [options]\n" +
              "Commands:\n" +
               "  help      Display help\n" +
               "  cluster   Run in clustered mode\n" +
               "  dev       Dev mode\n" +
               "Options:\n" +
               "  -hazelcast-config-cp file     Read config from class path\n" +
               "  -hazelcast-config-file file   Read config from local file\n" +
               "  -hazelcast-config-url url     Read config from URL\n"
              );
      System.exit(0);
    } else if ("dev".equals(args[0])) {
      Vertx vertx = Vertx.vertx();
      DeploymentOptions opt = new DeploymentOptions();
      vertx.deployVerticle(MainVerticle.class.getName(), opt);
    } else if ("cluster".equals(args[0])) {
      if (hConfig == null) {
        hConfig = new Config();
      }
      hConfig.setProperty("hazelcast.logging.type", "slf4j");

      ClusterManager mgr = new HazelcastClusterManager(hConfig);
      VertxOptions vopt = new VertxOptions().setClusterManager(mgr);
      Vertx.clusteredVertx(vopt, res -> {
        if (res.succeeded()) {
          Vertx vertx = res.result();
          DeploymentOptions opt = new DeploymentOptions();
          vertx.deployVerticle(MainVerticle.class.getName(), opt);
        } else {
          System.err.println("Failed to create a clustered vert.x");
          // We probably should not use logging here, as it depends
          // on vert.x, which just failed to start!
        }
      });
    } else {
      System.err.println("Unknown command: " + args[0]);
      System.exit(1);
    }
  }
}
