package org.folio.okapi.util;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.vertx.core.VertxOptions;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxInfluxDbOptions;
import io.vertx.micrometer.backends.BackendRegistries;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.common.OkapiLogger;

/**
 * Metrics handling.
 */
public class MetricsHelper {

  private static final Logger logger = OkapiLogger.get();

  private static final String METRICS_PREFIX = "org.folio.okapi";
  private static final String METRICS_HTTP = METRICS_PREFIX + ".http";
  private static final String METRICS_HTTP_SERVER = METRICS_HTTP + ".server";
  private static final String METRICS_HTTP_CLIENT = METRICS_HTTP + ".client";
  private static final String METRICS_HTTP_SERVER_PROCESSING_TIME = METRICS_HTTP_SERVER
      + ".processingTime";
  private static final String METRICS_HTTP_CLIENT_RESPONSE_TIME = METRICS_HTTP_CLIENT
      + ".responseTime";
  private static final String METRICS_HTTP_CLIENT_ERRORS = METRICS_HTTP_CLIENT
      + ".errors";

  private static final String TAG_HOST = "host";
  private static final String TAG_TENANT = "tenant";
  private static final String TAG_CODE = "code";
  private static final String TAG_METHOD = "method";
  private static final String TAG_MODULE = "module";
  private static final String TAG_URL = "url";
  private static final String TAG_PHASE = "phase";
  private static final String TAG_EMPTY = "null";

  static final String HOST_UNKNOWN = "unknown";

  private static boolean enabled = false;
  private static MeterRegistry registry;

  private MetricsHelper() {
  }

  /**
   * Config metrics options - specifically use InfluxDb micrometer options.
   *
   * @param vertxOptions   - {@link VertxOptions}
   * @param influxUrl      - default to http://localhost:8086
   * @param influxDbName   - default to okapi
   * @param influxUserName - default to null
   * @param influxPassword - default to null
   */
  public static void config(VertxOptions vertxOptions, String influxUrl,
      String influxDbName, String influxUserName, String influxPassword) {
    VertxInfluxDbOptions influxDbOptions = new VertxInfluxDbOptions()
        .setEnabled(true)
        .setUri(Optional.ofNullable(influxUrl).orElse("http://localhost:8086"))
        .setDb(Optional.ofNullable(influxDbName).orElse("okapi"));
    if (influxUserName != null) {
      influxDbOptions.setUserName(influxUserName);
    }
    logger.log(Level.INFO, influxDbOptions.toJson().encodePrettily());
    if (influxPassword != null) {
      influxDbOptions.setPassword(influxPassword);
    }
    vertxOptions.setMetricsOptions(new MicrometerMetricsOptions()
        .setEnabled(true)
        .setInfluxDbOptions(influxDbOptions));
    enabled = true;
  }

  /**
   * Return a {@link Sample} to help start timing a {@link Timer}.
   *
   * @return {@link Sample} or null if metrics is not enabled
   */
  public static Sample getTimerSample() {
    return enabled ? Timer.start(getRegistry()) : null;
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
        .register(getRegistry());
    counter.increment();
    return counter;
  }

  private static Timer recordHttpTime(Sample sample, String tenant, int httpStatusCode,
      String httpMethod, ModuleInstance moduleInstance, boolean server) {
    if (!enabled) {
      return null;
    }
    String name = server ? METRICS_HTTP_SERVER_PROCESSING_TIME : METRICS_HTTP_CLIENT_RESPONSE_TIME;
    Timer timer = Timer.builder(name)
        .tags(createHttpTags(tenant, httpStatusCode, httpMethod, moduleInstance, !server))
        .register(getRegistry());
    sample.stop(timer);
    return timer;
  }

  private static List<Tag> createHttpTags(String tenant, int httpStatusCode, String httpMethod,
      ModuleInstance moduleInstance, boolean createPhaseTag) {
    List<Tag> tags = new ArrayList<>();
    tags.add(Tag.of(TAG_TENANT, Optional.of(tenant).orElse(TAG_EMPTY)));
    tags.add(Tag.of(TAG_CODE, "" + httpStatusCode));
    tags.add(Tag.of(TAG_METHOD, Optional.of(httpMethod).orElse(TAG_EMPTY)));
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
        tags.add(Tag.of(TAG_PHASE, moduleInstance.isHandler() ? "handler" : ""));
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

  private static MeterRegistry getRegistry() {
    if (registry == null) {
      registry = Optional.ofNullable(BackendRegistries.getDefaultNow())
          .orElse(new SimpleMeterRegistry());
      registry.config().commonTags(TAG_HOST, getHost());
    }
    return registry;
  }

  static String getHost() {
    try {
      return InetAddress.getLocalHost().toString();
    } catch (UnknownHostException e) {
      logger.warn(e);
    }
    return HOST_UNKNOWN;
  }

  public static boolean isEnabled() {
    return enabled;
  }

  public static void setEnabled(boolean enabled) {
    MetricsHelper.enabled = enabled;
  }

}