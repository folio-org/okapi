/*
 * Copyright (c) 2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.util;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import io.vertx.core.VertxOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class DropwizardConfig {

  static Logger logger = LoggerFactory.getLogger("okapi");

  public static void config(String graphiteHost, int port, TimeUnit tu,
          int period, VertxOptions vopt) {
    String host = "localhost";
    try {
      host = InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      logger.warn("Can read hostname. Using localhost. " + e.getMessage());
    }
    final String registryName = "okapi";

    MetricRegistry registry = SharedMetricRegistries.getOrCreate(registryName);

    DropwizardMetricsOptions metricsOpt = new DropwizardMetricsOptions();
    metricsOpt.setEnabled(true).setRegistryName(registryName);
    vopt.setMetricsOptions(metricsOpt);
    Graphite graphite = new Graphite(new InetSocketAddress(graphiteHost, port));
    GraphiteReporter reporter = GraphiteReporter.forRegistry(registry)
            .prefixedWith(host + "-okapi")
            .build(graphite);
    reporter.start(period, tu);

    logger.info("Metrics remote:" + graphiteHost + ":"
            + port + " this:" + host + "-okapi");
  }
}
