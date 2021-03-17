package org.folio.okapi.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class ScheduleTest {

  @Test
  void testParseComp1() {
    List<Integer> l = new LinkedList<>();
    ScheduleNaive.parseComp(l, "32", 0, 59);
    assertThat(l).containsExactlyInAnyOrder(32);
  }

  @Test
  void testParseComp2() {
    List<Integer> l = new LinkedList<>();
    ScheduleNaive.parseComp(l, "3,7,59", 0, 59);
    assertThat(l).containsExactlyInAnyOrder(3, 7, 59);
  }

  @Test
  void testParseComp3() {
    List<Integer> l = new LinkedList<>();
    ScheduleNaive.parseComp(l, "*", 2, 5);
    assertThat(l).containsExactlyInAnyOrder(2, 3, 4, 5);
  }

  @Test
  void testParseComp4() {
    List<Integer> l = new LinkedList<>();
    ScheduleNaive.parseComp(l, "7,*/15", 4, 63);
    assertThat(l).containsExactlyInAnyOrder(4, 7, 19, 34, 49);
  }

  @Test
  void testParseComp5() {
    List<Integer> l = new LinkedList<>();
    ScheduleNaive.parseComp(l, "tuesday,friDAY", 2, 8,
        new String [] { "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"});
    assertThat(l).containsExactlyInAnyOrder(3, 6);
  }

  @Test
  void testParseCompFail() {
    List<Integer> l = new LinkedList<>();
    ScheduleNaive.parseComp(l, "", 4, 63);

    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      ScheduleNaive.parseComp(l, "*/", 4, 63);
    });

    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      ScheduleNaive.parseComp(l, "*x", 4, 63);
    });

    Exception e = Assertions.assertThrows(IllegalArgumentException.class, () -> {
      ScheduleNaive.parseComp(l, "*/x", 4, 63);
    });
    assertThat(e.getMessage()).isEqualTo("Expected number here: x");

    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      ScheduleNaive.parseComp(l, "6x", 4, 63);
    });

    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      ScheduleNaive.parseComp(l, "a", 4, 63);
    });

    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      ScheduleNaive.parseComp(l, "3", 4, 63);
    });

    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      ScheduleNaive.parseComp(l, "64", 4, 63);
    });

    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      ScheduleNaive.parseComp(l, "monday,thusday", 0, 6,
          new String [] { "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"});
    });

  }

  @Test
  void testScheduleFailsMissingSection() {
    Schedule schedule = new ScheduleNaive();
    Assertions.assertThrows(IllegalArgumentException.class, () ->
      schedule.parseSpec("*/15 * * *")
    );
  }

  static Stream<Arguments> testSchedule() {
    return Stream.of(
        Arguments.of("*/15 * * * *", "2020-12-31T23:44", "PT1M1S", null),
        Arguments.of("*/15 * * * *", "2020-12-31T23:45", "PT15M1S", null),
        Arguments.of("*/15 * * * *", "2020-12-31T23:45:36", "PT14M25S", null),
        Arguments.of("*/15 * * * *", "2020-12-31T23:46", "PT14M1S", null),
        Arguments.of("3 1,22 * * *", "2020-12-31T23:44", "PT1H19M1S", null),
        Arguments.of("3 1,22 1 * *", "2020-12-31T23:44", "PT1H19M1S", null),
        Arguments.of("3 1,22 5 * *", "2020-12-31T23:44", "PT97H19M1S", null),
        Arguments.of("3 1,22 5 3 *", "2020-12-31T23:44", "PT1513H19M1S", null),
        Arguments.of("3 1,22 5 5 *", "2020-12-31T23:44", "PT2977H19M1S", "PT2976H19M1S"), // diff by 1 hour
        Arguments.of("3 1,22 * * fri", "2020-12-31T23:44", "PT1H19M1S", null),
        Arguments.of("3 1,22 * * sat", "2020-12-31T23:44", "PT25H19M1S", null),
        Arguments.of("*/15 * * * mon,fri", "2020-12-31T23:44", "PT16M1S", null),
        Arguments.of("*/15 * * * mon", "2020-12-31T23:44", "PT72H16M1S", null),
        Arguments.of("*/15 * 5 * Mon", "2020-12-31T23:44", "PT240H16M1S", "PT72H16M1S"), // nasty one
        Arguments.of("*/15 * * * Sun", "2020-12-31T23:44", "PT48H16M1S", null),
        Arguments.of("*/15 * * * 0", "2020-12-31T23:44", "PT48H16M1S", null),
        Arguments.of("*/15 * 4 * *", "2020-12-31T23:44", "PT72H16M1S", null),
        Arguments.of("*/15 * * feb *", "2020-12-31T23:44", "PT744H16M1S", null),
        Arguments.of("*/15 * * FEB *", "2021-01-31T23:44", "PT16M1S", null),
        Arguments.of("*/15 * 29 * *", "2020-02-26T23:44", "PT48H16M1S", null),
        Arguments.of("*/15 * 29 * *", "2021-02-26T23:44", "PT720H16M1S", "PT719H16M1S") // diff by 1 hour
        );
  }
  @ParameterizedTest
  @MethodSource
  void testSchedule(String spec, String time, String duration, String cronUtilsDuration) {
    Schedule schedule = new ScheduleNaive();
    schedule.parseSpec(spec);
    assertThat(schedule.toString()).isEqualTo(spec);
    LocalDateTime localDateTime = LocalDateTime.parse(time);
    assertThat(schedule.getNextDuration(localDateTime)).isEqualTo(Duration.parse(duration));

    schedule = new ScheduleCronUtils();
    schedule.parseSpec(spec);
    assertThat(schedule.toString()).isEqualTo(spec);
    localDateTime = LocalDateTime.parse(time);
    assertThat(schedule.getNextDuration(localDateTime)).isEqualTo(Duration.parse(
        cronUtilsDuration != null ? cronUtilsDuration : duration));
  }

}
