/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi;

import com.hazelcast.config.Config;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class MainCluster {
  public static void main(String[] args) {
    System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory");
    Config hazelcastConfig = new Config();
    hazelcastConfig.setProperty("hazelcast.logging.type", "slf4j");
    ClusterManager mgr = new HazelcastClusterManager(hazelcastConfig);
    VertxOptions vopt = new VertxOptions().setClusterManager(mgr);
    Vertx.clusteredVertx(vopt, res->{
      if ( res.succeeded() ) {
        DeploymentOptions opt = new DeploymentOptions();
        res.result().deployVerticle(MainVerticle.class.getName(), opt);
      } else {
        System.out.println("Failed to create a clustered vert.x");
      }
    });

  }
}
