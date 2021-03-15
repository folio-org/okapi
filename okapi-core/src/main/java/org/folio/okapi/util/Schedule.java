package org.folio.okapi.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;

public class Schedule {
  private final Logger logger = OkapiLogger.get();

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
        if (names != null) {
          for (int j = 0; j < names.length; j++) {
            if (spec.startsWith(names[j], i)) {
              l.add(j + min);
              i += names[j].length();
              found = true;
            }
          }
        }
        if (!found) {
          i = parseNumber(spec, i, val);
          if (val[0] < min) {
            throw new IllegalArgumentException("Cron-spec value "
                + val[0] + " below minimum " + min);
          }
          if (val[0] > max) {
            throw new IllegalArgumentException("Cron-spec value "
                + val[0] + " above maximum " + min);
          }
          l.add(val[0]);
        }
      }
      if (i < spec.length() && spec.charAt(i) == ',') {
        i++;
      }
    }
  }

  private final String spec;
  private final List<Integer> minute = new LinkedList<>();
  private final List<Integer> hour = new LinkedList<>();
  private final List<Integer> dayOfMonth = new LinkedList<>();
  private final List<Integer> monthOfYear = new LinkedList<>();
  private final List<Integer> dayOfWeek = new LinkedList<>();

  /**
   * Make schedule from Vixie-cron type of spec.
   * @param spec cron specification
   * @throws IllegalArgumentException for bad format
   */
  public Schedule(String spec) {
    this.spec = spec;

    String[] components = spec.split("\\s+");

    if (components.length != 5) {
      throw new IllegalArgumentException("Spec must be exactly 5 components: "
          + "minute hour day month weekday");
    }
    parseComp(minute, components[0], 0, 59, null);
    parseComp(hour, components[1], 0, 23, null);
    parseComp(dayOfMonth, components[2], 1, 31, null);
    parseComp(monthOfYear, components[3], 1, 12, null);
    parseComp(dayOfWeek, components[4], 0, 6,
        new String [] { "monday", "tuesday", "wednesday", "thursday",
            "friday", "saturday", "sunday"});
  }

  public String toString() {
    return this.spec;
  }

  static int getNext(int v, List<Integer> list, int max) {
    int r = Integer.MAX_VALUE;
    if (v > max) {
      return r;
    }
    for (Integer value : list) {
      if (value >= v && r > value) {
        r = value;
      }
    }
    return r;
  }

  /**
   * Return duration until next event in milliseconds.
   * @param localTime time to be used as "current"
   * @return time in milliseconds (always at least 1)
   */
  public long getNextEventMillis(LocalDateTime localTime) {
    Duration duration = getNextEventDuration(localTime);
    if (duration.isNegative()) {
      return 1;
    }
    long milli = duration.getSeconds();
    return milli < 1L ? 1L : milli * 1000L;
  }

  Duration getNextEventDuration(LocalDateTime localTime) {
    int minuteNext = getNext(localTime.getMinute(), minute, 60);
    int hourNext = getNext(localTime.getHour(), hour, 24);
    if (minuteNext == Integer.MAX_VALUE) {
      minuteNext = getNext(0, minute, 60);
      hourNext = getNext(localTime.getHour() + 1, hour, 24);
    }
    int dayOfMonthNext = getNext(localTime.getDayOfMonth(), dayOfMonth, 31);
    if (hourNext == Integer.MAX_VALUE) {
      hourNext = getNext(0, hour, 24);
      dayOfMonthNext = getNext(localTime.getDayOfMonth() + 1, dayOfMonth, 31);
    }
    int monthNext = getNext(localTime.getMonthValue(), monthOfYear, 12);
    if (dayOfMonthNext == Integer.MAX_VALUE) {
      dayOfMonthNext = getNext(1, dayOfMonth, 31);
      monthNext = getNext(localTime.getMonthValue() + 1, monthOfYear, 12);
    }
    int yearNext = localTime.getYear();
    if (monthNext == Integer.MAX_VALUE) {
      monthNext = getNext(1, monthOfYear, 12);
      yearNext++;
    }
    logger.debug("minute {} hour {} day {} month {} year {}",
        minuteNext, hourNext, dayOfMonthNext, monthNext, yearNext);
    LocalDateTime nextTime = LocalDateTime.of(yearNext, monthNext, dayOfMonthNext,
        hourNext, minuteNext);
    return Duration.between(localTime, nextTime);
  }

}
