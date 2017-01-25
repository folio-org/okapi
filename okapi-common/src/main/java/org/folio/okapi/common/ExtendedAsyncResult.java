package org.folio.okapi.common;

import io.vertx.core.AsyncResult;

/**
 * Like vert.x' AsyncResult, but with our enum ErrorType, to distinguish between
 * internal and user errors, etc.
 *
 * @author heikki
 */
public interface ExtendedAsyncResult<T> extends AsyncResult<T> {

  ErrorType getType();
}
