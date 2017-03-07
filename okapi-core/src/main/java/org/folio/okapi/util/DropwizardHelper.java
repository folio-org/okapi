package org.folio.okapi.util;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import io.vertx.core.VertxOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * Helpers for the DropWizard instrumentation.
 */
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
    final String prefix = "folio.okapi." + hostName ;
    GraphiteReporter reporter = GraphiteReporter.forRegistry(registry)
            .prefixedWith(prefix)
            .build(graphite);
    reporter.start(period, tu);

    logger.info("Metrics remote:" + graphiteHost + ":"
            + port + " this:" + prefix);
  }

  /**
   * Register a gauge.
   * That is, a number of some sort that gets polled at intervals, and
   * reported to Graphite. For example, the number of known modules.
   *
   * @param key The key for the metric to report
   * @param g A Gauge with a lambda to get the value
   *
   * Call like this:
   *     DropwizardHelper.registerGauge("moduleCount", () -> modules.size());

   */
  public static void registerGauge(String key, Gauge g) {
    try {
      MetricRegistry reg = SharedMetricRegistries.getOrCreate("okapi");
      reg.removeMatching((String name, Metric metric) -> key.equals(name));
      reg.register(key, g);
    } catch (Exception e) {
      logger.warn("registerGauge caught an exception: " + e);
    }
  }

  /**
   * Get a timer.
   * Returns a timer. Get one at the beginning of some operation. When done,
   * call timer.close();
   * @param metricKey
   * @return
   */
  public static Timer.Context getTimerContext(String metricKey) {
    Timer timer = SharedMetricRegistries.getOrCreate("okapi").timer(metricKey);
    return timer.time();
  }

  /**
   * Mark an event.
   * Tells the metrics system that an event has occurred, for example a request
   * has arrived. The system will calculate rates and counts of these.
   * @param metricKey
   */
  public static void markEvent(String metricKey) {
    SharedMetricRegistries.getOrCreate("okapi").meter(metricKey).mark();
  }

}
