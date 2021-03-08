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

  private ConfNames() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }
}
