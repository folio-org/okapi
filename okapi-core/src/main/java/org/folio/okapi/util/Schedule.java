package org.folio.okapi.util;

import java.time.Duration;
import java.time.ZonedDateTime;

public interface Schedule {
  Duration getNextDuration(ZonedDateTime zonedDateTime);

  void parseSpec(String spec);
}
