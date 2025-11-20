package org.folio.okapi.common.logging;

import io.vertx.core.internal.VertxBootstrap;
import io.vertx.core.spi.VertxServiceProvider;
import io.vertx.core.spi.context.storage.ContextLocal;

public class FolioLocal implements VertxServiceProvider {
  static boolean initialized;  // used for unit test only
  public static final ContextLocal<String> TENANT_ID = ContextLocal.registerLocal(String.class);
  public static final ContextLocal<String> REQUEST_ID = ContextLocal.registerLocal(String.class);
  public static final ContextLocal<String> MODULE_ID = ContextLocal.registerLocal(String.class);
  public static final ContextLocal<String> USER_ID = ContextLocal.registerLocal(String.class);

  /**
   * See the {@link ContextLocal} javadoc why we need this init method and
   * {@code src/main/resources/META-INF/services/io.vertx.core.spi.VertxServiceProvider}
   * to prevent a timing (race condition) issue:
   * <a href="https://folio-org.atlassian.net/browse/OKAPI-1228">OKAPI-1228</a>.
   * <p>
   * If some other dependency also ships with io.vertx.core.spi.VertxServiceProvider
   * add the {@code ServicesResourceTransformer} transformer to {@code maven-shade-plugin}
   * so that both files get concatenated, see
   * <pre><a href="https://maven.apache.org/plugins/maven-shade-plugin/examples/resource-transformers.html
     #ServicesResourceTransformer">manual</a></pre>
   * and <a href="https://github.com/folio-org/mod-copycat/pull/123/files">example</a>.
   */
  @Override
  @SuppressWarnings("java:S2696")  // Suppress "Make method static", cannot change supertype
  public void init(VertxBootstrap builder) {
    initialized = true;
  }
}
