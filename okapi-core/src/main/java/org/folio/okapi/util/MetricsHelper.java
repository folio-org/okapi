package org.folio.okapi.util;

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
import io.micrometer.influx.InfluxMeterRegistry;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
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
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.common.OkapiLogger;

/**
 * Metrics handling.
 */
public class MetricsHelper {

  private static final Logger logger = OkapiLogger.get(MetricsHelper.class);

  static final String METRICS_PREFIX = "org.folio.metrics";

  private static final String METRICS_HTTP = METRICS_PREFIX + ".http";
  private static final String METRICS_HTTP_SERVER = METRICS_HTTP + ".server";
  private static final String METRICS_HTTP_CLIENT = METRICS_HTTP + ".client";
  private static final String METRICS_HTTP_SERVER_PROCESSING_TIME = METRICS_HTTP_SERVER
      + ".processingTime";
  private static final String METRICS_HTTP_CLIENT_RESPONSE_TIME = METRICS_HTTP_CLIENT
      + ".responseTime";
  private static final String METRICS_HTTP_CLIENT_ERRORS = METRICS_HTTP_CLIENT
      + ".errors";
  private static final String METRICS_TOKEN_CACHE = METRICS_PREFIX + ".tokenCache";
  private static final String METRICS_TOKEN_CACHE_HITS = METRICS_TOKEN_CACHE + ".hits";
  private static final String METRICS_TOKEN_CACHE_MISSES = METRICS_TOKEN_CACHE + ".misses";
  private static final String METRICS_TOKEN_CACHE_CACHED = METRICS_TOKEN_CACHE + ".cached";
  private static final String METRICS_TOKEN_CACHE_EXPIRED = METRICS_TOKEN_CACHE + ".expired";
  private static final String METRICS_CODE = METRICS_PREFIX + ".code";
  private static final String METRICS_CODE_EXECUTION_TIME = METRICS_CODE + ".executionTime";

  private static final String TAG_HOST = "host";
  private static final String TAG_TENANT = "tenant";
  private static final String TAG_CODE = "code";
  private static final String TAG_METHOD = "method";
  private static final String TAG_MODULE = "module";
  private static final String TAG_URL = "url";
  private static final String TAG_PHASE = "phase";
  private static final String TAG_USERID = "userId";
  private static final String TAG_EMPTY = "null";

  static final String ENABLE_METRICS = "vertx.metrics.options.enabled";
  static final String INFLUX_OPTS = "influxDbOptions";
  static final String PROMETHEUS_OPTS = "prometheusOptions";
  static final String JMX_OPTS = "jmxMetricsOptions";

  static final String METRICS_FILTER = "metricsPrefixFilter";

  private static final String HOST_ID = ManagementFactory.getRuntimeMXBean().getName();

  private static boolean enabled = false;

  static boolean isEnabled() {
    return enabled;
  }

  static void setEnabled(boolean enabled) {
    MetricsHelper.enabled = enabled;
  }

  private static CompositeMeterRegistry registry = new CompositeMeterRegistry();

  static CompositeMeterRegistry getRegistry() {
    return registry;
  }

  private static JvmGcMetrics jvmGcMetrics;

  private MetricsHelper() {
  }

  /**
   * Initialize metrics helper.
   *
   * @param vertxOptions - {@link VertxOptions}
   */
  public static void init(VertxOptions vertxOptions) {

    if (!"true".equalsIgnoreCase(System.getProperty(ENABLE_METRICS))) {
      logger.info("Metrics is not enabled");
      enabled = false;
      return;
    }

    logger.info("Enabling metrics for " + HOST_ID);

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
      logger.error("No metrics are enabled. Please check command line config for metrics backend");
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

    vertxOptions.setMetricsOptions(new MicrometerMetricsOptions()
        .setEnabled(true)
        .setMicrometerRegistry(registry));
    enabled = true;
  }

  /**
   * Stop metrics helper and release resources.
   */
  public static void stop() {
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
   * Record HTTP server processing time if metrics is enabled.
   *
   * @param sample         - {@link Sample} that tells the starting time
   * @param tenant         - FOLIO tenant id
   * @param httpStatusCode - HTTP response code to record
   * @param httpMethod     - HTTP method to record
   * @param moduleInstance - {@link ModuleInstance} provides some tag info
   *
   * @return {@link Timer} or null if metrics is not enabled
   */
  public static Timer recordHttpServerProcessingTime(Sample sample, String tenant,
      int httpStatusCode, String httpMethod, ModuleInstance moduleInstance) {
    return recordHttpTime(sample, tenant, httpStatusCode, httpMethod, moduleInstance, true);
  }

  /**
   * Record HTTP client response time if metrics is enabled.
   *
   * @param sample         - {@link Sample} that tells the starting time
   * @param tenant         - FOLIO tenant id
   * @param httpStatusCode - HTTP response code to record
   * @param httpMethod     - HTTP method to record
   * @param moduleInstance - {@link ModuleInstance} provides some tag info
   *
   * @return {@link Timer} or null if metrics is not enabled
   */
  public static Timer recordHttpClientResponse(Sample sample, String tenant, int httpStatusCode,
      String httpMethod, ModuleInstance moduleInstance) {
    return recordHttpTime(sample, tenant, httpStatusCode, httpMethod, moduleInstance, false);
  }

  /**
   * Record HTTP client error if metrics is enabled.
   *
   * @param tenant     - FOLIO tenant id
   * @param httpMethod - HTTP method
   * @param urlPath    - HTTP URL path
   *
   * @return {@link Counter} or null if metrics is not enabled
   */
  public static Counter recordHttpClientError(String tenant, String httpMethod, String urlPath) {
    if (!enabled) {
      return null;
    }
    Counter counter = Counter.builder(METRICS_HTTP_CLIENT_ERRORS)
        .tag(TAG_TENANT, tenant)
        .tag(TAG_METHOD, httpMethod)
        .tag(TAG_URL, urlPath)
        .register(registry);
    counter.increment();
    return counter;
  }

  /**
   * Record code execution time.
   *
   * @param sample - {@link Sample} that tells the starting time
   * @param name   - name of the code block for tagging purpose
   *
   * @return {@link Timer} or null if metrics is not enabled
   */
  public static Timer recordCodeExecutionTime(Sample sample, String name) {
    if (!enabled) {
      return null;
    }
    Timer timer = Timer.builder(METRICS_CODE_EXECUTION_TIME)
        .tag("name", name)
        .register(registry);
    sample.stop(timer);
    return timer;
  }

  private static Timer recordHttpTime(Sample sample, String tenant, int httpStatusCode,
      String httpMethod, ModuleInstance moduleInstance, boolean server) {
    if (!enabled) {
      return null;
    }
    String name = server ? METRICS_HTTP_SERVER_PROCESSING_TIME : METRICS_HTTP_CLIENT_RESPONSE_TIME;
    Timer timer = Timer.builder(name)
        .tags(createHttpTags(tenant, httpStatusCode, httpMethod, moduleInstance, !server))
        .register(registry);
    sample.stop(timer);
    return timer;
  }

  public static Counter recordTokenCacheMiss(String tenant, String httpMethod, String urlPath,
      String userId) {
    return recordTokenCacheEvent(METRICS_TOKEN_CACHE_MISSES, tenant, httpMethod, urlPath, userId);
  }

  public static Counter recordTokenCacheHit(String tenant, String httpMethod, String urlPath,
      String userId) {
    return recordTokenCacheEvent(METRICS_TOKEN_CACHE_HITS, tenant, httpMethod, urlPath, userId);
  }

  public static Counter recordTokenCacheCached(String tenant, String httpMethod, String urlPath,
      String userId) {
    return recordTokenCacheEvent(METRICS_TOKEN_CACHE_CACHED, tenant, httpMethod, urlPath, userId);
  }

  public static Counter recordTokenCacheExpired(String tenant, String httpMethod, String urlPath,
      String userId) {
    return recordTokenCacheEvent(METRICS_TOKEN_CACHE_EXPIRED, tenant, httpMethod, urlPath, userId);
  }

  private static Counter recordTokenCacheEvent(String event, String tenant, String httpMethod,
      String urlPath, String userId) {
    if (!enabled) {
      return null;
    }
    Counter counter = Counter.builder(event).tag(TAG_TENANT, tenant).tag(TAG_METHOD, httpMethod)
        .tag(TAG_URL, urlPath).tag(TAG_USERID, userId == null ? "null" : userId)
        .register(registry);
    counter.increment();
    return counter;
  }

  private static List<Tag> createHttpTags(String tenant, int httpStatusCode, String httpMethod,
      ModuleInstance moduleInstance, boolean createPhaseTag) {
    List<Tag> tags = new ArrayList<>();
    tags.add(Tag.of(TAG_TENANT, tenant == null ? TAG_EMPTY : tenant));
    tags.add(Tag.of(TAG_CODE, "" + httpStatusCode));
    tags.add(Tag.of(TAG_METHOD, httpMethod == null ? TAG_EMPTY : httpMethod));
    if (moduleInstance != null) {
      tags.add(Tag.of(TAG_MODULE, moduleInstance.getModuleDescriptor().getId()));
      // legacy case where module instance has no routing entry
      if (moduleInstance.getRoutingEntry() != null) {
        tags.add(Tag.of(TAG_URL, moduleInstance.getRoutingEntry().getStaticPath()));
        if (createPhaseTag) {
          tags.add(Tag.of(TAG_PHASE, moduleInstance.isHandler() ? "handler"
              : moduleInstance.getRoutingEntry().getPhase()));
        }
      } else {
        tags.add(Tag.of(TAG_URL, moduleInstance.getPath()));
        tags.add(Tag.of(TAG_PHASE, moduleInstance.isHandler() ? "handler" : TAG_EMPTY));
      }
    } else {
      tags.add(Tag.of(TAG_MODULE, TAG_EMPTY));
      tags.add(Tag.of(TAG_URL, TAG_EMPTY));
      if (createPhaseTag) {
        tags.add(Tag.of(TAG_PHASE, TAG_EMPTY));
      }
    }
    return tags;
  }

}
