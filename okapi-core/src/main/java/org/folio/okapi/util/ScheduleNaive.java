package org.folio.okapi.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;

public class ScheduleNaive implements Schedule {
  private final Logger logger = OkapiLogger.get();

  private static final int MINUTE_MIN = 0;
  private static final int MINUTE_MAX = 59;
  private static final int HOUR_MIN = 0;
  private static final int HOUR_MAX = 23;
  private static final int DAY_MIN = 1;
  private static final int DAY_MAX = 31;
  private static final int MONTH_MIN = 1;
  private static final int MONTH_MAX = 12;
  private static final int WEEKDAY_MIN = 1;
  private static final int WEEKDAY_MAX = 7;

  static int parseNumber(String spec, int i, int [] val) {
    if (i == spec.length()) {
      throw new IllegalArgumentException("Expected number, but got end of spec");
    }
    if (!Character.isDigit(spec.charAt(i))) {
      throw new IllegalArgumentException("Expected number here: " + spec.substring(i));
    }
    val[0] = 0;
    while (i < spec.length() && Character.isDigit(spec.charAt(i))) {
      val[0] = val[0] * 10 + (spec.charAt(i) - 48);
      i++;
    }
    return i;
  }

  static void addVal(List<Integer> l, int val, int min, int max) {
    if (val < min) {
      throw new IllegalArgumentException("Cron-spec value "
          + val + " below minimum " + min);
    }
    if (val > max) {
      throw new IllegalArgumentException("Cron-spec value "
          + val + " above maximum " + max);
    }
    l.add(val);
  }

  static void parseComp(List<Integer> l, String spec, int min, int max) {
    parseComp(l, spec, min, max, new String[0]);
  }

  static void parseComp(List<Integer> l, String spec, int min, int max, String [] names) {
    int [] val = new int[1];
    int i = 0;
    while (i < spec.length()) {
      char ch = spec.charAt(i);
      if (ch == '*') {
        i++;
        int step = 1;
        if (i < spec.length() && spec.charAt(i) == '/') {
          i++;
          i = parseNumber(spec, i, val);
          step = val[0];
        }
        for (int j = min; j <= max; j += step) {
          l.add(j);
        }
      } else {
        boolean found = false;
        for (int j = 0; j < names.length; j++) {
          if (spec.startsWith(names[j], i)) {
            l.add(j + min);
            i += names[j].length();
            found = true;
          }
        }
        if (!found) {
          i = parseNumber(spec, i, val);
          addVal(l, val[0], min, max);
        }
      }
      if (i < spec.length() && spec.charAt(i) == ',') {
        i++;
      }
    }
  }

  private String spec;
  private final List<Integer> minute = new LinkedList<>();
  private final List<Integer> hour = new LinkedList<>();
  private final List<Integer> dayOfMonth = new LinkedList<>();
  private final List<Integer> monthOfYear = new LinkedList<>();
  private final List<Integer> dayOfWeek = new LinkedList<>();

  @Override
  public void parseSpec(String spec) {
    this.spec = spec;

    String[] components = spec.split("\\s+");

    if (components.length != 5) {
      throw new IllegalArgumentException("Spec must be exactly 5 components: "
          + "minute hour day month weekday");
    }
    parseComp(minute, components[0], MINUTE_MIN, MINUTE_MAX);
    parseComp(hour, components[1], HOUR_MIN, HOUR_MAX);
    parseComp(dayOfMonth, components[2], DAY_MIN, DAY_MAX);
    parseComp(monthOfYear, components[3], MONTH_MIN, MONTH_MAX);
    parseComp(dayOfWeek, components[4], WEEKDAY_MIN, WEEKDAY_MAX,
        new String [] { "monday", "tuesday", "wednesday", "thursday",
            "friday", "saturday", "sunday"});
  }

  static int getNext(int v, List<Integer> list, int max) {
    int r = Integer.MAX_VALUE;
    if (v > max) {
      return r;
    }
    for (Integer value : list) {
      if (value >= v && value < r && value <= max) {
        r = value;
      }
    }
    return r;
  }

  @Override
  public Duration getNextDuration(LocalDateTime localTime) {
    if (spec == null) {
      return null;
    }
    int minuteNext = getNext(localTime.getMinute() + 1, minute, MINUTE_MAX);
    int hourNext;
    if (minuteNext == Integer.MAX_VALUE) {
      minuteNext = getNext(MINUTE_MIN, minute, MINUTE_MAX);
      hourNext = getNext(localTime.getHour() + 1, hour, HOUR_MAX);
    } else {
      hourNext = getNext(localTime.getHour(), hour, HOUR_MAX);
    }
    int yearNext = localTime.getYear();
    int daysOfMonth = LocalDate.of(yearNext, localTime.getMonthValue(), 1).lengthOfMonth();
    int dayOfMonthNext;
    if (hourNext == Integer.MAX_VALUE) {
      minuteNext = getNext(MINUTE_MIN, minute, MINUTE_MAX);
      hourNext = getNext(HOUR_MIN, hour, HOUR_MAX);
      dayOfMonthNext = getNext(localTime.getDayOfMonth() + 1, dayOfMonth, daysOfMonth);
    } else {
      dayOfMonthNext = getNext(localTime.getDayOfMonth(), dayOfMonth, daysOfMonth);
    }
    if (dayOfMonthNext != localTime.getDayOfMonth()) {
      minuteNext = getNext(MINUTE_MIN, minute, MINUTE_MAX);
      hourNext = getNext(HOUR_MIN, hour, HOUR_MAX);
    }
    int monthNext;
    if (dayOfMonthNext == Integer.MAX_VALUE) {
      monthNext = getNext(localTime.getMonthValue() + 1, monthOfYear, MONTH_MAX);
    } else {
      monthNext = getNext(localTime.getMonthValue(), monthOfYear, MONTH_MAX);
    }
    if (monthNext != localTime.getMonthValue()) {
      if (monthNext == Integer.MAX_VALUE) {
        monthNext = getNext(MONTH_MIN, monthOfYear, MONTH_MAX);
        yearNext++;
      }
      minuteNext = getNext(MINUTE_MIN, minute, MINUTE_MAX);
      hourNext = getNext(HOUR_MIN, hour, HOUR_MAX);
      daysOfMonth = LocalDate.of(yearNext, monthNext, 1).lengthOfMonth();
      dayOfMonthNext = getNext(1, dayOfMonth, daysOfMonth);
    }
    LocalDateTime nextTime = LocalDateTime.of(yearNext, monthNext, dayOfMonthNext,
        hourNext, minuteNext);
    int currentDayOfWeek = nextTime.getDayOfWeek().getValue();
    int dayOfWeekNext = getNext(currentDayOfWeek, dayOfWeek, WEEKDAY_MAX);
    int delta = dayOfWeekNext - currentDayOfWeek;
    if (dayOfWeekNext == Integer.MAX_VALUE) {
      dayOfWeekNext = getNext(WEEKDAY_MIN, dayOfWeek, WEEKDAY_MAX);
      delta = WEEKDAY_MAX - currentDayOfWeek + dayOfWeekNext;
    }
    if (delta > 0) {
      minuteNext = getNext(MINUTE_MIN, minute, MINUTE_MAX);
      hourNext = getNext(HOUR_MIN, hour, HOUR_MAX);
      nextTime = LocalDateTime.of(yearNext, monthNext, dayOfMonthNext, hourNext, minuteNext);
    }
    logger.debug("minute {} hour {} day {} month {} year {} delta {}",
        minuteNext, hourNext, dayOfMonthNext, monthNext, yearNext, delta);
    // adding a second here because 0 means "stop timer".
    return Duration.between(localTime, nextTime.plusDays(delta).plusSeconds(1));
  }

  @Override
  public String toString() {
    return spec;
  }
}
