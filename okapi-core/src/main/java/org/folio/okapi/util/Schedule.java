package org.folio.okapi.util;

import java.time.Duration;
import java.time.LocalDateTime;

public interface Schedule {
  Duration getNextDuration(LocalDateTime localDateTime);

  void parseSpec(String spec);
}
