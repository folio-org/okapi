/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class MainCluster {
  public static void main(String[] args) {
    VertxOptions vopt = new VertxOptions();

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
