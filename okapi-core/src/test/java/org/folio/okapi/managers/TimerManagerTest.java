package org.folio.okapi.managers;

import org.assertj.core.api.Assertions;
import org.folio.okapi.bean.RoutingEntry;
import org.folio.okapi.bean.Schedule;
import org.folio.okapi.bean.TimerDescriptor;
import org.junit.jupiter.api.Test;

public class TimerManagerTest {

  @Test
  void testIsSimilar() {
    Assertions.assertThat(TimerManager.isSimilar(null, null)).isFalse();
    TimerDescriptor a = new TimerDescriptor();
    TimerDescriptor b = new TimerDescriptor();
    Assertions.assertThat(TimerManager.isSimilar(null, b)).isFalse();
    Assertions.assertThat(TimerManager.isSimilar(a, b)).isTrue();
    RoutingEntry routingEntry = new RoutingEntry();
    routingEntry.setDelay("10");
    a.setRoutingEntry(routingEntry);
    Assertions.assertThat(TimerManager.isSimilar(a, b)).isFalse();
    b.setRoutingEntry(routingEntry);
    Assertions.assertThat(TimerManager.isSimilar(a, b)).isTrue();
  }

  @Test
  void isPatchReset() {
    RoutingEntry routingEntry = new RoutingEntry();
    Assertions.assertThat(TimerManager.isPatchReset(routingEntry)).isTrue();
    routingEntry.setPathPattern("/path");
    Assertions.assertThat(TimerManager.isPatchReset(routingEntry)).isTrue();
    routingEntry.setDelay("10");
    Assertions.assertThat(TimerManager.isPatchReset(routingEntry)).isFalse();
    routingEntry.setUnit("second");
    Assertions.assertThat(TimerManager.isPatchReset(routingEntry)).isFalse();
    routingEntry.setSchedule(new Schedule());
    Assertions.assertThat(TimerManager.isPatchReset(routingEntry)).isFalse();
  }

  }
