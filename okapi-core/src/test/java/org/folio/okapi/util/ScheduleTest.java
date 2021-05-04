package org.folio.okapi.util;

import org.folio.okapi.bean.Schedule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class ScheduleTest {

  @Test
  void testEmpty() {
    ScheduleCronUtils schedule = new ScheduleCronUtils();
    ZonedDateTime parse = ZonedDateTime.parse("2020-12-31T23:44:59+01:00[Europe/Paris]");
    assertThat(schedule.getNextDuration(parse)).isEmpty();
  }

  static Stream<Arguments> testSchedule() {
    return Stream.of(
        Arguments.of("*/15 * * * *", "2020-12-31T23:44:59", "PT1S"),
        Arguments.of("*/15 * * * *", "2020-12-31T23:45", "PT15M"),
        Arguments.of("*/15 * * * *", "2020-12-31T23:45:59", "PT14M1S"),
        Arguments.of("*/15 * * * *", "2020-12-31T23:44", "PT1M"),
        Arguments.of("*/15 * * * *", "2020-12-31T23:45", "PT15M"),
        Arguments.of("*/15 * * * *", "2020-12-31T23:45:36", "PT14M24S"),
        Arguments.of("*/15 * * * *", "2020-12-31T23:46", "PT14M"),
        Arguments.of("3 1,22 * * *", "2020-12-31T23:44", "PT1H19M"),
        Arguments.of("3 1,22 1 * *", "2020-12-31T23:44", "PT1H19M"),
        Arguments.of("3 1,22 5 * *", "2020-12-31T23:44", "PT97H19M"),
        Arguments.of("3 1,22 5 3 *", "2020-12-31T23:44", "PT1513H19M"),
        Arguments.of("3 1,22 5 5 *", "2020-12-31T23:44", "PT2977H19M"),
        Arguments.of("3 1,22 * * fri", "2020-12-31T23:44", "PT1H19M"),
        Arguments.of("3 1,22 * * sat", "2020-12-31T23:44", "PT25H19M"),
        Arguments.of("*/15 * * * mon,fri", "2020-12-31T23:44", "PT16M"),
        Arguments.of("*/15 * * * mon", "2020-12-31T23:44", "PT72H16M"),
        Arguments.of("*/15 * 1 * Mon", "2020-12-31T23:44", "PT16M"),
        Arguments.of("*/15 * 5 * Mon", "2020-12-31T23:44", "PT72H16M"),
        Arguments.of("*/15 * * * Sun", "2020-12-31T23:44", "PT48H16M"),
        Arguments.of("*/15 * * * 0", "2020-12-31T23:44", "PT48H16M"),
        Arguments.of("*/15 * 4 * *", "2020-12-31T23:44", "PT72H16M"),
        Arguments.of("*/15 * * feb *", "2020-12-31T23:44", "PT744H16M"),
        Arguments.of("*/15 * * FEB *", "2021-01-31T23:44", "PT16M"),
        Arguments.of("*/15 * 29 * *", "2020-02-26T23:44", "PT48H16M"),
        Arguments.of("*/15 * 29 * *", "2021-02-26T23:44", "PT720H16M")
    );
  }

  @ParameterizedTest
  @MethodSource
  void testSchedule(String spec, String time, String duration) {
    ZonedDateTime parse = ZonedDateTime.of(LocalDateTime.parse(time), ZoneOffset.UTC);
    ScheduleCronUtils schedule = new ScheduleCronUtils();
    schedule.parseSpec(spec);
    assertThat(schedule.toString()).isEqualTo(spec);
    assertThat(schedule.getNextDuration(parse).get()).isEqualTo(Duration.parse(duration));
  }

  static Stream<Arguments> testZone() {
    return Stream.of(
        Arguments.of("UTC"),
        Arguments.of("GMT-04:00"),
        Arguments.of("CET"),
        Arguments.of("America/New_York"),
        Arguments.of("Europe/Copenhagen"),
        Arguments.of("Atlantic/Faroe"),
        Arguments.of("Asia/Shanghai")
    );
  }

  @ParameterizedTest
  @MethodSource
  void testZone(String s) {
    Schedule schedule = new Schedule();
    schedule.setZone(s);
    assertThat(schedule.getZone()).isEqualTo(s);
    schedule.setCron("0 2 * * *");
    assertThat(schedule.getDelayMilliSeconds()).isGreaterThan(0);
  }

  @Test
  void testNoCron() {
    Schedule schedule = new Schedule();
    assertThat(schedule.getDelayMilliSeconds()).isEqualTo(0);
  }
}
