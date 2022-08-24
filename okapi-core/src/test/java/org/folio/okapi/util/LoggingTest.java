package org.folio.okapi.util;

import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;
import org.junit.Test;

public class LoggingTest {
  private final Logger logger = OkapiLogger.get();

  @Test
  public void testWithMapFilled() {
    OkapiMapMessage msg = new OkapiMapMessage("req1", "tenant1", "userid1", "module1", "msg-map-filled");
    logger.info(msg);
  }

  @Test
  public void testWithoutMap() {
    logger.info("msg-without-map");
  }

  @Test
  public void testWithMapEmpty() {
    OkapiMapMessage msg = new OkapiMapMessage(null, null, null, null, "msg-empty-map");
    logger.info(msg);
  }

}
