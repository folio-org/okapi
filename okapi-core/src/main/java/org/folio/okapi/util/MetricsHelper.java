package org.folio.okapi.util;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.common.MetricsUtil;
import org.folio.okapi.common.OkapiLogger;

/**
 * Metrics handling.
 */
public class MetricsHelper {

  private static final Logger logger = OkapiLogger.get(MetricsHelper.class);

  static final String METRICS_PREFIX = MetricsUtil.METRICS_PREFIX + ".okapi";

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

  private static final String TAG_TENANT = "tenant";
  private static final String TAG_HTTP_CODE = "code";
  private static final String TAG_HTTP_METHOD = "method";
  private static final String TAG_MODULE = "module";
  private static final String TAG_URL = "url";
  private static final String TAG_PHASE = "phase";
  private static final String TAG_USERID = "userId";
  private static final String TAG_EMPTY = "null";

  private static final String TAG_CODE_BLOCK_NAME = "codeBlockName";

  private MetricsHelper() {
  }

  /**
   * Return a {@link Sample} to help start timing a {@link Timer}.
   *
   * @return {@link Sample} or null if metrics is not enabled
   */
  public static Sample getTimerSample() {
    return MetricsUtil.getTimerSample();
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
    if (!MetricsUtil.isEnabled()) {
      return null;
    }
    List<Tag> tags = new ArrayList<>();
    tags.add(Tag.of(TAG_TENANT, "" + tenant));
    tags.add(Tag.of(TAG_HTTP_METHOD, "" + httpMethod));
    tags.add(Tag.of(TAG_URL, "" + urlPath));
    return MetricsUtil.recordCounter(METRICS_HTTP_CLIENT_ERRORS, tags);
  }

  /**
   * Record code execution time.
   *
   * @param sample        - {@link Sample} that tells the starting time
   * @param codeBlockName - name of the code block for tagging purpose
   *
   * @return {@link Timer} or null if metrics is not enabled
   */
  public static Timer recordCodeExecutionTime(Sample sample, String codeBlockName) {
    if (!MetricsUtil.isEnabled()) {
      return null;
    }
    List<Tag> tags = new ArrayList<>();
    tags.add(Tag.of(TAG_CODE_BLOCK_NAME, "" + codeBlockName));
    return MetricsUtil.recordTimer(sample, METRICS_CODE_EXECUTION_TIME, tags);
  }

  private static Timer recordHttpTime(Sample sample, String tenant, int httpStatusCode,
      String httpMethod, ModuleInstance moduleInstance, boolean server) {
    if (!MetricsUtil.isEnabled()) {
      return null;
    }
    String name = server ? METRICS_HTTP_SERVER_PROCESSING_TIME : METRICS_HTTP_CLIENT_RESPONSE_TIME;
    return MetricsUtil.recordTimer(sample, name,
        createHttpTags(tenant, httpStatusCode, httpMethod, moduleInstance, !server));
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
    if (!MetricsUtil.isEnabled()) {
      return null;
    }
    List<Tag> tags = new ArrayList<>();
    tags.add(Tag.of(TAG_TENANT, "" + tenant));
    tags.add(Tag.of(TAG_HTTP_METHOD, "" + httpMethod));
    tags.add(Tag.of(TAG_URL, "" + urlPath));
    tags.add(Tag.of(TAG_USERID, userId == null ? TAG_EMPTY : userId));
    return MetricsUtil.recordCounter(event, tags);
  }

  private static List<Tag> createHttpTags(String tenant, int httpStatusCode, String httpMethod,
      ModuleInstance moduleInstance, boolean createPhaseTag) {
    List<Tag> tags = new ArrayList<>();
    tags.add(Tag.of(TAG_TENANT, tenant == null ? TAG_EMPTY : tenant));
    tags.add(Tag.of(TAG_HTTP_CODE, "" + httpStatusCode));
    tags.add(Tag.of(TAG_HTTP_METHOD, httpMethod == null ? TAG_EMPTY : httpMethod));
    if (moduleInstance != null) {
      tags.add(Tag.of(TAG_MODULE, "" + moduleInstance.getModuleDescriptor().getId()));
      // legacy case where module instance has no routing entry
      if (moduleInstance.getRoutingEntry() != null) {
        tags.add(Tag.of(TAG_URL, moduleInstance.getRoutingEntry().getStaticPath()));
        if (createPhaseTag) {
          tags.add(Tag.of(TAG_PHASE, moduleInstance.isHandler() ? "handler"
              : "" + moduleInstance.getRoutingEntry().getPhase()));
        }
      } else {
        logger.warn("legacy module instance {}", moduleInstance.getPath());
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
