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
  public static final String HTTP_CLIENT_TRUST_ALL = "http_client_trust_all";
  public static final String KUBE_CONFIG = "kube_config";
  public static final String KUBE_TOKEN = "kube_token";
  public static final String KUBE_SERVER = "kube_server";
  public static final String KUBE_NAMESPACE = "kube_namespace";
  public static final String KUBE_REFRESH_INTERVAL = "kube_refresh_interval";

  private ConfNames() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }
}
