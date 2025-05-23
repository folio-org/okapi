package org.folio.okapi.common;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.influx.InfluxMeterRegistry;
import io.vertx.core.VertxBuilder;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.micrometer.MicrometerMetricsFactory;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxInfluxDbOptions;
import io.vertx.micrometer.VertxJmxMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.backends.JmxBackendRegistry;
import io.vertx.micrometer.backends.PrometheusBackendRegistry;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Logger;

/**
 * Provide the common need of Metrics handling.
 */
public class MetricsUtil {

  private static final Logger logger = OkapiLogger.get(MetricsUtil.class);

  public static final String METRICS_PREFIX = "org.folio";

  public static final String ENABLE_METRICS = "vertx.metrics.options.enabled";

  public static final String INFLUX_OPTS = "influxDbOptions";
  public static final String PROMETHEUS_OPTS = "prometheusOptions";
  public static final String JMX_OPTS = "jmxMetricsOptions";

  public static final String SIMPLE_OPTS = "simpleOptions"; // used for tests

  public static final String METRICS_FILTER = "metricsPrefixFilter";

  private static final String TAG_HOST = "host";
  private static final String HOST_ID = ManagementFactory.getRuntimeMXBean().getName();

  private static boolean enabled = false;

  public static boolean isEnabled() {
    return enabled;
  }

  static void setEnabled(boolean enabled) {
    MetricsUtil.enabled = enabled;
  }

  private static CompositeMeterRegistry registry = new CompositeMeterRegistry();

  static CompositeMeterRegistry getRegistry() {
    return registry;
  }

  private static JvmGcMetrics jvmGcMetrics;

  private MetricsUtil() {
  }

  /**
   * Initialize metrics utility.
   */
  public static void init(VertxBuilder vertxBuilder) {

    if (!"true".equalsIgnoreCase(System.getProperty(ENABLE_METRICS))) {
      logger.debug("Metrics is not enabled");
      enabled = false;
      return;
    }

    logger.info("Enabling metrics for " + HOST_ID);

    if (System.getProperty(SIMPLE_OPTS) != null) {
      registry.add(new SimpleMeterRegistry(SimpleConfig.DEFAULT, Clock.SYSTEM));
      logger.info("Added {} for {}", SIMPLE_OPTS, HOST_ID);
    }

    String influxDbOptionsString = System.getProperty(INFLUX_OPTS);
    if (influxDbOptionsString != null) {
      VertxInfluxDbOptions influxDbOptions = new VertxInfluxDbOptions()
          .setEnabled(true);
      influxDbOptions = new VertxInfluxDbOptions(
          influxDbOptions.toJson().mergeIn(new JsonObject(influxDbOptionsString), true));
      InfluxMeterRegistry influxMeterRegistry = new InfluxMeterRegistry(
          influxDbOptions.toMicrometerConfig(), Clock.SYSTEM);
      influxMeterRegistry.config().commonTags(TAG_HOST, HOST_ID);
      registry.add(influxMeterRegistry);
      logger.info("Added {} for {}", INFLUX_OPTS, HOST_ID);
    }

    String prometheusOptionsString = System.getProperty(PROMETHEUS_OPTS);
    if (prometheusOptionsString != null) {
      VertxPrometheusOptions prometheusOptions = new VertxPrometheusOptions()
          .setEnabled(true)
          .setStartEmbeddedServer(true)
          .setEmbeddedServerOptions(new HttpServerOptions().setPort(9930));
      prometheusOptions = new VertxPrometheusOptions(
          prometheusOptions.toJson().mergeIn(new JsonObject(prometheusOptionsString), true));
      PrometheusBackendRegistry prometheusBackendRegistry = new PrometheusBackendRegistry(
          prometheusOptions);
      prometheusBackendRegistry.init();
      registry.add(prometheusBackendRegistry.getMeterRegistry());
      logger.info("Added {} for {}", PROMETHEUS_OPTS, HOST_ID);
    }

    String jmxMetricsOptionsString = System.getProperty(JMX_OPTS);
    if (jmxMetricsOptionsString != null) {
      VertxJmxMetricsOptions jmxMetricsOptions = new VertxJmxMetricsOptions()
          .setEnabled(true)
          .setDomain(METRICS_PREFIX);
      jmxMetricsOptions = new VertxJmxMetricsOptions(
          jmxMetricsOptions.toJson().mergeIn(new JsonObject(jmxMetricsOptionsString), true));
      registry.add(new JmxBackendRegistry(jmxMetricsOptions).getMeterRegistry());
      logger.info("Added {} for {}", JMX_OPTS, HOST_ID);
    }

    if (registry.getRegistries().isEmpty()) {
      logger.error("Cannot enable metrics. Check command line for backend config");
      enabled = false;
      return;
    }

    String metricsPrefixFilter = System.getProperty(METRICS_FILTER);
    if (metricsPrefixFilter != null) {
      logger.info("Adding metrics prefix filters");
      String[] strs = metricsPrefixFilter.split(",");
      for (int i = 0, n = strs.length; i < n; i++) {
        String prefix = strs[i].trim();
        logger.info("Allowing metrics with prefix {}", prefix);
        registry.config().meterFilter(MeterFilter.acceptNameStartsWith(prefix));
      }
      logger.info("Denying metrics that have no prefix of {}", metricsPrefixFilter);
      registry.config().meterFilter(MeterFilter.deny());
    }

    new ClassLoaderMetrics().bindTo(registry);
    new JvmMemoryMetrics().bindTo(registry);
    jvmGcMetrics = new JvmGcMetrics();
    jvmGcMetrics.bindTo(registry);
    new ProcessorMetrics().bindTo(registry);
    new JvmThreadMetrics().bindTo(registry);

    vertxBuilder
        .with(new VertxOptions()
            .setMetricsOptions(new MicrometerMetricsOptions().setEnabled(true)))
        .withMetrics(new MicrometerMetricsFactory(registry));
    enabled = true;
    logger.info("Metrics enabled for " + HOST_ID);
  }

  /**
   * Stop metrics utility and release resources.
   */
  public static void stop() {
    if (!enabled && registry.getRegistries().isEmpty()) {
      return;
    }
    logger.debug("Stopping metrics for " + HOST_ID);
    enabled = false;
    if (jvmGcMetrics != null) {
      jvmGcMetrics.close();
      jvmGcMetrics = null;
    }
    List<MeterRegistry> list = new ArrayList<>();
    registry.getRegistries().forEach(r -> {
      list.add(r);
      r.close();
    });
    list.forEach(r -> registry.remove(r));
    logger.debug("Metrics stopped for " + HOST_ID);
  }

  /**
   * Return a {@link Sample} to help start timing a {@link Timer}.
   *
   * @return {@link Sample} or null if metrics is not enabled
   */
  public static Sample getTimerSample() {
    return enabled ? Timer.start(registry) : null;
  }

  /**
   * Record a {@link Counter} meter.
   *
   * @param meterName - name of the {@link Counter} meter
   * @param tags      - tags associated with the meter
   *
   * @return {@link Counter} or null if metrics is not enabled
   */
  public static Counter recordCounter(String meterName, Iterable<Tag> tags) {
    if (!enabled) {
      return null;
    }
    logger.trace("Record counter for {} with tags {}", meterName, tags.toString());
    Counter counter = Counter.builder(meterName).tags(tags).register(registry);
    counter.increment();
    return counter;
  }

  /**
   * Record a {@link Timer} meter.
   *
   * @param sample    - a {@link Sample} that tells the starting time of the Timer
   * @param meterName - name of the {@link Counter} meter
   * @param tags      - tags associated with the meter
   *
   * @return @return {@link Timer} or null if metrics is not enabled
   */
  public static Timer recordTimer(Sample sample, String meterName, Iterable<Tag> tags) {
    if (!enabled) {
      return null;
    }
    logger.trace("Record Timer for {} with tags {}", meterName, tags.toString());
    Timer timer = Timer.builder(meterName).tags(tags).register(registry);
    sample.stop(timer);
    return timer;
  }

}
