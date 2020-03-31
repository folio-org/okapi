package org.folio.okapi.util;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import io.vertx.core.VertxOptions;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;

/**
 * Helpers for the DropWizard instrumentation.
 */
@java.lang.SuppressWarnings({"squid:S1192"})
public class DropwizardHelper {

  private DropwizardHelper() {
    throw new IllegalAccessError("DropwizardHelper");
  }

  private static final Logger logger = OkapiLogger.get();

  /**
   * Configure Dropwizard helper.
   * @param graphiteHost graphite server host
   * @param port  graphits server port
   * @param tu time unit
   * @param period reporting period
   * @param vopt Vert.x options
   * @param hostName logical hostname for this node (reporting)
   */
  public static void config(String graphiteHost, int port, TimeUnit tu,
          int period, VertxOptions vopt, String hostName) {
    final String registryName = "okapi";
    MetricRegistry registry = SharedMetricRegistries.getOrCreate(registryName);

    DropwizardMetricsOptions metricsOpt = new DropwizardMetricsOptions();
    metricsOpt.setEnabled(true).setRegistryName(registryName);
    vopt.setMetricsOptions(metricsOpt);
    Graphite graphite = new Graphite(new InetSocketAddress(graphiteHost, port));
    final String prefix = "folio.okapi." + hostName;
    GraphiteReporter reporter = GraphiteReporter.forRegistry(registry)
            .prefixedWith(prefix)
            .build(graphite);
    reporter.start(period, tu);

    logger.info("Metrics remote {}:{} this {}", graphiteHost, port, prefix);
  }

  /**
   * Register a gauge.
   * That is, a number of some sort that gets polled at intervals, and
   * reported to Graphite. For example, the number of known modules.
   * Call like this:
   *     DropwizardHelper.registerGauge("moduleCount", () -> modules.size());
   *
   * @param key The key for the metric to report
   * @param g A Gauge with a lambda to get the value
   */
  public static void registerGauge(String key, Gauge g) {
    try {
      MetricRegistry reg = SharedMetricRegistries.getOrCreate("okapi");
      reg.removeMatching((String name, Metric metric) -> key.equals(name));
      reg.register(key, g);
    } catch (Exception e) {
      logger.warn("registerGauge {}", e.getMessage(), e);
    }
  }

  /**
   * Get a timer.
   * Returns a timer. Get one at the beginning of some operation. When done,
   * call timer.close();
   * @param metricKey string key
   * @return timer for the key
   */
  public static Timer.Context getTimerContext(String metricKey) {
    Timer timer = SharedMetricRegistries.getOrCreate("okapi").timer(metricKey);
    return timer.time();
  }

  /**
   * Mark an event.
   * Tells the metrics system that an event has occurred, for example a request
   * has arrived. The system will calculate rates and counts of these.
   * @param metricKey string key
   */
  public static void markEvent(String metricKey) {
    SharedMetricRegistries.getOrCreate("okapi").meter(metricKey).mark();
  }

}
