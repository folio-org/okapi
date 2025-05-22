package org.folio.okapi.common.logging;

import io.vertx.core.internal.VertxBootstrap;
import io.vertx.core.spi.VertxServiceProvider;
import io.vertx.core.spi.context.storage.ContextLocal;

public class FolioLocal implements VertxServiceProvider {
  public static final ContextLocal<String> TENANT_ID = ContextLocal.registerLocal(String.class);
  public static final ContextLocal<String> REQUEST_ID = ContextLocal.registerLocal(String.class);
  public static final ContextLocal<String> MODULE_ID = ContextLocal.registerLocal(String.class);
  public static final ContextLocal<String> USER_ID = ContextLocal.registerLocal(String.class);

  @Override
  public void init(VertxBootstrap builder) {
    // nothing to do
  }

}
