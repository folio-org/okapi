package org.folio.okapi.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ScheduleTest {

  @Test
  void testParseComp1() {
    List<Integer> l = new LinkedList<>();
    Schedule.parseComp(l, "32", 0, 59);
    assertThat(l).containsExactlyInAnyOrder(32);
  }

  @Test
  void testParseComp2() {
    List<Integer> l = new LinkedList<>();
    Schedule.parseComp(l, "3,7,59", 0, 59);
    assertThat(l).containsExactlyInAnyOrder(3, 7, 59);
  }

  @Test
  void testParseComp3() {
    List<Integer> l = new LinkedList<>();
    Schedule.parseComp(l, "*", 2, 5);
    assertThat(l).containsExactlyInAnyOrder(2, 3, 4, 5);
  }

  @Test
  void testParseComp4() {
    List<Integer> l = new LinkedList<>();
    Schedule.parseComp(l, "*/15", 4, 63);
    assertThat(l).containsExactlyInAnyOrder(4, 19, 34, 49);
  }

  @Test
  void testParseComp5() {
    List<Integer> l = new LinkedList<>();
    Schedule.parseComp(l, "tuesday,friday", 2, 8,
        new String [] { "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"});
    assertThat(l).containsExactlyInAnyOrder(3, 6);
  }

  @Test
  void testParseCompFail() {
    List<Integer> l = new LinkedList<>();
    Schedule.parseComp(l, "", 4, 63);

    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      Schedule.parseComp(l, "*/", 4, 63);
    });

    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      Schedule.parseComp(l, "*x", 4, 63);
    });

    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      Schedule.parseComp(l, "6x", 4, 63);
    });

    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      Schedule.parseComp(l, "a", 4, 63);
    });

    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      Schedule.parseComp(l, "3", 4, 63);
    });

    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      Schedule.parseComp(l, "64", 4, 63);
    });

    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      Schedule.parseComp(l, "monday,thusday", 0, 6,
          new String [] { "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"});
    });

  }

  @Test
  void testScheduleFailsMissingSection() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      Schedule schedule = new Schedule("*/15 * * *");
    });
  }

  @Test
  void testScheduleNextMinute() {
    Schedule schedule = new Schedule("*/15 * * * *");
    assertThat(schedule.toString()).isEqualTo("*/15 * * * *");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextEventDuration(localDateTime)).isEqualTo(Duration.of(1L, ChronoUnit.MINUTES));
    assertThat(schedule.getNextEventMillis(localDateTime)).isEqualTo(60000L);

    localDateTime = LocalDateTime.of(2020, 12, 31, 23, 45);
    assertThat(schedule.getNextEventDuration(localDateTime).isZero());
    assertThat(schedule.getNextEventMillis(localDateTime)).isEqualTo(1L);

    localDateTime = LocalDateTime.of(2020, 12, 31, 23, 45, 36);
    assertThat(schedule.getNextEventDuration(localDateTime).isZero());
    assertThat(schedule.getNextEventMillis(localDateTime)).isEqualTo(1L);

    localDateTime = LocalDateTime.of(2020, 12, 31, 23, 46);
    assertThat(schedule.getNextEventDuration(localDateTime)).isEqualTo(Duration.of(14, ChronoUnit.MINUTES));
    assertThat(schedule.getNextEventMillis(localDateTime)).isEqualTo(840000L);
  }

  @Test
  void testScheduleNextHour() {
    Schedule schedule = new Schedule("3 1,22 * * *");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextEventDuration(localDateTime)).isEqualTo(Duration.of(79L, ChronoUnit.MINUTES));
  }

  @Test
  void testScheduleNextDay() {
    Schedule schedule = new Schedule("3 1,22 1 * *");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextEventDuration(localDateTime)).isEqualTo(Duration.of(79L, ChronoUnit.MINUTES));
  }

  @Test
  void testScheduleNextDay2() {
    Schedule schedule = new Schedule("3 1,22 5 * *");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextEventDuration(localDateTime)).isEqualTo(Duration.of(5839L, ChronoUnit.MINUTES));
  }

  @Test
  void testScheduleNextMonth1() {
    Schedule schedule = new Schedule("3 1,22 5 5 *");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextEventDuration(localDateTime)).isEqualTo(Duration.of(178639L, ChronoUnit.MINUTES));
  }

  @Test
  void testScheduleWeekDay1() {
    Schedule schedule = new Schedule("3 1,22 * * friday");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextEventDuration(localDateTime)).isEqualTo(Duration.of(79L, ChronoUnit.MINUTES));
  }

  @Test
  void testScheduleWeekDay2() {
    Schedule schedule = new Schedule("3 1,22 * * saturday");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextEventDuration(localDateTime)).isEqualTo(Duration.of(1440L + 79L, ChronoUnit.MINUTES));
  }

  @Test
  void testScheduleWeekDay3() {
    Schedule schedule = new Schedule("*/15 * * * monday,friday");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextEventDuration(localDateTime)).isEqualTo(Duration.of(16L, ChronoUnit.MINUTES));
  }

  @Test
  void testScheduleWeekDay4() {
    Schedule schedule = new Schedule("*/15 * * * monday");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextEventDuration(localDateTime)).isEqualTo(Duration.of(16L + 3*1440L, ChronoUnit.MINUTES));
  }

  @Test
  void testScheduleWeekDay5() {
    Schedule schedule = new Schedule("*/15 * 5 * monday");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextEventDuration(localDateTime)).isEqualTo(Duration.of(16L + 10*1440L, ChronoUnit.MINUTES));
  }

  @Test
  void testScheduleDayOfMonth() {
    Schedule schedule = new Schedule("*/15 * 4 * *");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextEventDuration(localDateTime)).isEqualTo(Duration.of(16L + 3*1440L, ChronoUnit.MINUTES));
  }

  @Test
  void testScheduleMonth1() {
    Schedule schedule = new Schedule("*/15 * * 2 *");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 12, 31, 23, 44);
    assertThat(schedule.getNextEventDuration(localDateTime)).isEqualTo(Duration.of(16L + 31*1440L, ChronoUnit.MINUTES));
  }

  @Test
  void testScheduleMonth2() {
    Schedule schedule = new Schedule("*/15 * * 2 *");
    LocalDateTime localDateTime = LocalDateTime.of(2021, 1, 31, 23, 44);
    assertThat(schedule.getNextEventDuration(localDateTime)).isEqualTo(Duration.of(16L, ChronoUnit.MINUTES));
  }

  @Test
  void testScheduleMonthLeapYear() {
    Schedule schedule = new Schedule("*/15 * 29 * *");
    LocalDateTime localDateTime = LocalDateTime.of(2020, 2, 26, 23, 44);
    assertThat(schedule.getNextEventDuration(localDateTime)).isEqualTo(Duration.of(16L + 2*1440L, ChronoUnit.MINUTES));
  }

  @Test
  void testScheduleMonthNoLeapYear() {
    Schedule schedule = new Schedule("*/15 * 29 * *");
    LocalDateTime localDateTime = LocalDateTime.of(2021, 2, 26, 23, 44);
    assertThat(schedule.getNextEventDuration(localDateTime)).isEqualTo(Duration.of(16L + 30*1440L, ChronoUnit.MINUTES));
  }

}
