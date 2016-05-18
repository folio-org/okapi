/*
 * Copyright (c) 2015-2016, Index Data
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

public class DropwizardHelper {

  static Logger logger = LoggerFactory.getLogger("okapi");

  public static void config(String graphiteHost, int port, TimeUnit tu,
          int period, VertxOptions vopt, String hostName) {
    final String registryName = "okapi";
    MetricRegistry registry = SharedMetricRegistries.getOrCreate(registryName);

    DropwizardMetricsOptions metricsOpt = new DropwizardMetricsOptions();
    metricsOpt.setEnabled(true).setRegistryName(registryName);
    vopt.setMetricsOptions(metricsOpt);
    Graphite graphite = new Graphite(new InetSocketAddress(graphiteHost, port));
    final String prefix = "okapi." + hostName ;
    GraphiteReporter reporter = GraphiteReporter.forRegistry(registry)
            .prefixedWith(prefix)
            .build(graphite);
    reporter.start(period, tu);

    logger.info("Metrics remote:" + graphiteHost + ":"
            + port + " this:" + prefix);
  }


}
