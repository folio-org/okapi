package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.folio.okapi.util.ScheduleCronUtils;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Schedule {

  private final org.folio.okapi.util.Schedule scheduleCronUtils = new ScheduleCronUtils();

  public void setCron(String cron) {
    this.scheduleCronUtils.parseSpec(cron);
  }

  public String getCron() {
    return scheduleCronUtils.toString();
  }

  @JsonIgnore
  long getDelayMilliSeconds() {
    // UTC for now, but we can add other timezone property later
    Optional<Duration> duration = scheduleCronUtils.getNextDuration(
        ZonedDateTime.now(Clock.systemUTC())
    );
    if (duration.isEmpty()) {
      return 0;
    }
    long sec = duration.get().toSeconds();
    return (sec + 1) * 1000;
  }
}
