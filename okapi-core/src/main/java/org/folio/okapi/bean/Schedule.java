package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.folio.okapi.util.ScheduleCronUtils;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Schedule {

  ZoneId zoneId;

  public void setZone(String name) {
    zoneId = ZoneId.of(name);
  }

  public String getZone() {
    return zoneId == null ? null : zoneId.getId();
  }

  private final ScheduleCronUtils scheduleCronUtils = new ScheduleCronUtils();

  public void setCron(String cron) {
    this.scheduleCronUtils.parseSpec(cron);
  }

  public String getCron() {
    return scheduleCronUtils.toString();
  }

  /**
   * Provide nearest time for cron execution.
   * @return 0 if no cron entry is specified; >0 for duration in milliseconds
   */
  @JsonIgnore
  public long getDelayMilliSeconds() {
    ZonedDateTime zonedDateTime;
    if (zoneId == null) {
      zonedDateTime = ZonedDateTime.now(Clock.systemUTC());
    } else {
      zonedDateTime = ZonedDateTime.now(zoneId);
    }
    Optional<Duration> duration = scheduleCronUtils.getNextDuration(zonedDateTime);
    if (duration.isEmpty()) {
      return 0;
    }
    long sec = duration.get().toSeconds();
    return (sec + 1) * 1000;
  }
}
