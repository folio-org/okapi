package org.folio.okapi.service;

import io.vertx.core.Handler;
import org.folio.okapi.common.ExtendedAsyncResult;

/**
 *
 * @author heikki
 */
public interface TimeStampStore {

  void getTimeStamp(String stampId, Handler<ExtendedAsyncResult<Long>> fut);

  void updateTimeStamp(String stampId, long currentStamp, Handler<ExtendedAsyncResult<Long>> fut);

}
