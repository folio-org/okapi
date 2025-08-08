package org.folio.okapi;

import org.folio.okapi.common.Config;

/**
 * Configuration variable names used for {@code java -Dname=value okapi.jar}
 * or used as key in okapi-conf.json when calling {@code java okapi.jar -conf okapi-conf.json}.
 *
 * @see Config
 * @see <a href="https://github.com/folio-org/okapi/blob/master/doc/guide.md#okapi-configuration">
 *     guide.md#okapi-configuration</a>
 */
public final class ConfNames {

  public static final String DEPLOY_WAIT_ITERATIONS = "deploy.waitIterations";
  public static final String DOCKER_URL = "dockerUrl";
  public static final String ENABLE_TRACE_HEADERS = "trace_headers";
  public static final String ENABLE_SYSTEM_AUTH = "enable_system_auth";
  public static final String KUBE_CONFIG = "kube_config";
  public static final String KUBE_TOKEN = "kube_token";
  public static final String KUBE_SERVER_URL = "kube_server_url";
  public static final String KUBE_SERVER_PEM = "kube_server_pem";
  public static final String KUBE_NAMESPACE = "kube_namespace";
  public static final String KUBE_REFRESH_INTERVAL = "kube_refresh_interval";
  public static final String LOG_WAIT_MS = "log_wait_ms";
  public static final String HTTP_MAX_SIZE_SYSTEM = "http_max_size_system";
  public static final String HTTP_MAX_SIZE_PROXY = "http_max_size_proxy";
  public static final int HTTP_MAX_SIZE_PROXY_DEFAULT = 1000;

  private ConfNames() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }
}
