package org.folio.okapi.util;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

public interface Schedule {
  Optional<Duration> getNextDuration(ZonedDateTime zonedDateTime);

  void parseSpec(String spec);
}
