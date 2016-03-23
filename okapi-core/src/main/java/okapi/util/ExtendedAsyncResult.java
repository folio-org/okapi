/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.util;

import io.vertx.core.AsyncResult;

/**
 * Like vert.x' AsyncResult, but with out enum ErrorType. to distinguish between
 * internal and user errors, etc.
 *
 * @author heikki
 */
public interface ExtendedAsyncResult<T> extends AsyncResult<T> {

  ErrorType getType();
}
