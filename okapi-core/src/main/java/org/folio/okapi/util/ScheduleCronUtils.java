package org.folio.okapi.util;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

public class ScheduleCronUtils implements Schedule {
  private String spec;  // cron-utils returns spec altered, we want to keep it was
  private ExecutionTime executionTime;

  @Override
  public Optional<Duration> getNextDuration(ZonedDateTime zonedDateTime) {
    if (executionTime == null) {
      return Optional.empty();
    }
    return executionTime.timeToNextExecution(zonedDateTime);
  }

  @Override
  public void parseSpec(String spec) {
    this.spec = spec;
    CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);
    CronParser parser = new CronParser(cronDefinition);
    Cron cron = parser.parse(spec);
    executionTime = ExecutionTime.forCron(cron);
  }

  @Override
  public String toString() {
    return spec;
  }
}
