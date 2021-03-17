package org.folio.okapi.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
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

  static void addVal(List<Integer> l, int val, int min, int max, int zeroMap) {
    if (val == 0) {
      val = zeroMap; // if zeroMap is already zero, no harm done
    }
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
    parseComp(l, spec, min, max, names, 0);
  }

  static void parseComp(List<Integer> l, String spec, int min, int max, String [] names,
                        int zeroMap) {
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
          addVal(l, j, min, max, zeroMap);
        }
      } else {
        int i0 = i;
        while (i < spec.length() && Character.isAlphabetic(spec.charAt(i))) {
          i++;
        }
        if (i != i0) {
          parseName(l, spec, min, max, names, i, i0);
        } else {
          i = parseNumber(spec, i, val);
          addVal(l, val[0], min, max, zeroMap);
        }
      }
      if (i < spec.length() && spec.charAt(i) == ',') {
        i++;
      }
    }
  }

  private static void parseName(List<Integer> l, String spec, int min, int max, String[] names,
                                int i, int i0) {
    String name = spec.substring(i0, i);
    for (int j = 0; j < names.length; j++) {
      if (name.equalsIgnoreCase(names[j])) {
        addVal(l, j + min, min, max, 0);
        return;
      }
    }
    throw new IllegalArgumentException("Unrecognized name: " + name);
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
    parseComp(monthOfYear, components[3], MONTH_MIN, MONTH_MAX,
        new String [] {
            "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"
        });
    parseComp(dayOfWeek, components[4], WEEKDAY_MIN, WEEKDAY_MAX,
        new String [] {
            "mon", "tue", "wed", "thu", "fri", "sat", "sun"
        }, WEEKDAY_MAX); // map 0 to 7=WEEKDAY_MAX
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
  public Duration getNextDuration(ZonedDateTime zonedDateTime) {
    if (spec == null) {
      return null;
    }
    int minuteNext = getNext(zonedDateTime.getMinute() + 1, minute, MINUTE_MAX);
    int hourNext;
    if (minuteNext == Integer.MAX_VALUE) {
      minuteNext = getNext(MINUTE_MIN, minute, MINUTE_MAX);
      hourNext = getNext(zonedDateTime.getHour() + 1, hour, HOUR_MAX);
    } else {
      hourNext = getNext(zonedDateTime.getHour(), hour, HOUR_MAX);
    }
    int yearNext = zonedDateTime.getYear();
    int daysOfMonth = LocalDate.of(yearNext, zonedDateTime.getMonthValue(), 1).lengthOfMonth();
    int dayOfMonthNext;
    if (hourNext == Integer.MAX_VALUE) {
      minuteNext = getNext(MINUTE_MIN, minute, MINUTE_MAX);
      hourNext = getNext(HOUR_MIN, hour, HOUR_MAX);
      dayOfMonthNext = getNext(zonedDateTime.getDayOfMonth() + 1, dayOfMonth, daysOfMonth);
    } else {
      dayOfMonthNext = getNext(zonedDateTime.getDayOfMonth(), dayOfMonth, daysOfMonth);
    }
    if (dayOfMonthNext != zonedDateTime.getDayOfMonth()) {
      minuteNext = getNext(MINUTE_MIN, minute, MINUTE_MAX);
      hourNext = getNext(HOUR_MIN, hour, HOUR_MAX);
    }
    int monthNext;
    if (dayOfMonthNext == Integer.MAX_VALUE) {
      monthNext = getNext(zonedDateTime.getMonthValue() + 1, monthOfYear, MONTH_MAX);
    } else {
      monthNext = getNext(zonedDateTime.getMonthValue(), monthOfYear, MONTH_MAX);
    }
    if (monthNext != zonedDateTime.getMonthValue()) {
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
    return Duration.between(zonedDateTime,
        ZonedDateTime.of(nextTime.plusDays(delta), zonedDateTime.getZone()));
  }

  @Override
  public String toString() {
    return spec;
  }
}
