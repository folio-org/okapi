package org.folio.okapi.util;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

public class ScheduleCronUtils implements Schedule {
  private String spec;  // cron-utils returns spec altered, we want to keep it was it was
  private Cron cron;

  @Override
  public Duration getNextDuration(LocalDateTime localDateTime) {
    if (cron == null) {
      return null;
    }
    ExecutionTime executionTime = ExecutionTime.forCron(cron);

    ZonedDateTime now = ZonedDateTime.of(localDateTime, ZoneId.systemDefault());

    Optional<Duration> timeToNextExecution = executionTime.timeToNextExecution(now);

    if (timeToNextExecution.isEmpty()) {
      return null;
    }
    return timeToNextExecution.get().plusSeconds(1);
  }

  @Override
  public void parseSpec(String spec) {
    this.spec = spec;
    CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);
    CronParser parser = new CronParser(cronDefinition);
    this.cron = parser.parse(spec);
  }

  @Override
  public String toString() {
    return spec;
  }
}
