/*
 * Copyright (c) 2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.service;

import io.vertx.core.Handler;
import okapi.util.ExtendedAsyncResult;

/**
 *
 * @author heikki
 */
public interface TimeStampStore {

  void getTimeStamp(String stampId, Handler<ExtendedAsyncResult<Long>> fut);

  void updateTimeStamp(String stampId, long currentStamp, Handler<ExtendedAsyncResult<Long>> fut);

}
