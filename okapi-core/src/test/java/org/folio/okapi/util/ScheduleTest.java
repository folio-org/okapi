package org.folio.okapi.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;

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
    ScheduleNaive.parseComp(l, "*/15", 4, 63);
    assertThat(l).containsExactlyInAnyOrder(4, 19, 34, 49);
  }

  @Test
  void testParseComp5() {
    List<Integer> l = new LinkedList<>();
    ScheduleNaive.parseComp(l, "tuesday,friday", 2, 8,
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
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      schedule.parseSpec("*/15 * * *");
    });
  }

  @Test
  void testScheduleNextMinute() {
    Schedule schedule = new ScheduleNaive();
    schedule.parseSpec("*/15 * * * *");
    assertThat(schedule.toString()).isEqualTo("*/15 * * * *");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextDuration(localDateTime)).isEqualTo(Duration.parse("PT1M1S"));

    localDateTime = LocalDateTime.of(2020, 12, 31, 23, 45);
    assertThat(schedule.getNextDuration(localDateTime)).isEqualTo(Duration.parse("PT15M1S"));

    localDateTime = LocalDateTime.of(2020, 12, 31, 23, 45, 36);
    assertThat(schedule.getNextDuration(localDateTime)).isEqualTo(Duration.parse("PT14M25S"));

    localDateTime = LocalDateTime.of(2020, 12, 31, 23, 46);
    assertThat(schedule.getNextDuration(localDateTime)).isEqualTo(Duration.parse("PT14M1S"));
  }

  @Test
  void testScheduleNextHour() {
    Schedule schedule = new ScheduleNaive();
    schedule.parseSpec("3 1,22 * * *");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextDuration(localDateTime)).isEqualTo(Duration.parse("PT1H19M1S"));
  }

  @Test
  void testScheduleNextDay() {
    Schedule schedule = new ScheduleNaive();
    schedule.parseSpec("3 1,22 1 * *");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextDuration(localDateTime)).isEqualTo(Duration.parse("PT1H19M1S"));
  }

  @Test
  void testScheduleNextDay2() {
    Schedule schedule = new ScheduleNaive();
    schedule.parseSpec("3 1,22 5 * *");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextDuration(localDateTime)).isEqualTo(Duration.parse("PT97H19M1S"));
  }

  @Test
  void testScheduleNextMonth1() {
    Schedule schedule = new ScheduleNaive();
    schedule.parseSpec("3 1,22 5 5 *");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextDuration(localDateTime)).isEqualTo(Duration.parse("PT2977H19M1S"));
  }

  @Test
  void testScheduleWeekDay1() {
    Schedule schedule = new ScheduleNaive();
    schedule.parseSpec("3 1,22 * * friday");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextDuration(localDateTime)).isEqualTo(Duration.parse("PT1H19M1S"));
  }

  @Test
  void testScheduleWeekDay2() {
    Schedule schedule = new ScheduleNaive();
    schedule.parseSpec("3 1,22 * * saturday");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextDuration(localDateTime)).isEqualTo(Duration.parse("PT25H19M1S"));
  }

  @Test
  void testScheduleWeekDay3() {
    Schedule schedule = new ScheduleNaive();
    schedule.parseSpec("*/15 * * * monday,friday");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextDuration(localDateTime)).isEqualTo(Duration.parse("PT16M1S"));
  }

  @Test
  void testScheduleWeekDay4() {
    Schedule schedule = new ScheduleNaive();
    schedule.parseSpec("*/15 * * * monday");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextDuration(localDateTime)).isEqualTo(Duration.parse("PT72H16M1S"));
  }

  @Test
  void testScheduleWeekDay5() {
    Schedule schedule = new ScheduleNaive();
    schedule.parseSpec("*/15 * 5 * monday");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextDuration(localDateTime)).isEqualTo(Duration.parse("PT240H16M1S"));
  }

  @Test
  void testScheduleDayOfMonth() {
    Schedule schedule = new ScheduleNaive();
    schedule.parseSpec("*/15 * 4 * *");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextDuration(localDateTime)).isEqualTo(Duration.parse("PT72H16M1S"));
  }

  @Test
  void testScheduleMonth1() {
    Schedule schedule = new ScheduleNaive();
    schedule.parseSpec("*/15 * * 2 *");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextDuration(localDateTime)).isEqualTo(Duration.parse("PT744H16M1S"));
  }

  @Test
  void testScheduleMonth2() {
    Schedule schedule = new ScheduleNaive();
    schedule.parseSpec("*/15 * * 2 *");
    LocalDateTime localDateTime = LocalDateTime.of(2021, 1, 31, 23, 44);
    assertThat(schedule.getNextDuration(localDateTime)).isEqualTo(Duration.parse("PT16M1S"));
  }

  @Test
  void testScheduleMonthLeapYear() {
    Schedule schedule = new ScheduleNaive();
    schedule.parseSpec("*/15 * 29 * *");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 2, 26, 23, 44);
    assertThat(schedule.getNextDuration(localDateTime)).isEqualTo(Duration.parse("PT48H16M1S"));
  }

  @Test
  void testScheduleMonthNoLeapYear() {
    Schedule schedule = new ScheduleNaive();
    schedule.parseSpec("*/15 * 29 * *");
    LocalDateTime localDateTime = LocalDateTime.of(2021, 2, 26, 23, 44);
    assertThat(schedule.getNextDuration(localDateTime)).isEqualTo(Duration.parse("PT720H16M1S"));
  }

}
