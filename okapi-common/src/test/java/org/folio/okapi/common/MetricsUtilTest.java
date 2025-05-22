package org.folio.okapi.common;

import static org.junit.Assert.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.junit.After;
import org.junit.Test;

public class MetricsUtilTest {

  @After
  public void teardown() {
    MetricsUtil.stop();
  }

  @Test
  public void testEnableMetricsWithoutSwitch() {
    MetricsUtil.init(Vertx.builder());
    verifyNotEnabled();
  }

  @Test
  public void testEnableMetricsWithoutBackendOptions() {
    System.getProperties().setProperty(MetricsUtil.ENABLE_METRICS, "true");
    MetricsUtil.init(Vertx.builder());
    verifyNotEnabled();
    MetricsUtil.stop();
    System.getProperties().remove(MetricsUtil.ENABLE_METRICS);
  }

  private void verifyNotEnabled() {
    assertFalse(MetricsUtil.isEnabled());
    assertTrue(MetricsUtil.getRegistry().getRegistries().isEmpty());
    assertNull(MetricsUtil.getTimerSample());
    assertNull(MetricsUtil.recordCounter("a", Collections.emptyList()));
    assertNull(MetricsUtil.recordTimer(null, "a", Collections.emptyList()));
  }

  @Test
  public void testEnableMetrics() {
    Properties props = System.getProperties();
    props.setProperty(MetricsUtil.ENABLE_METRICS, "true");
    List<String> options = Arrays.asList(
        MetricsUtil.SIMPLE_OPTS,
        MetricsUtil.INFLUX_OPTS,
        MetricsUtil.PROMETHEUS_OPTS,
        MetricsUtil.JMX_OPTS);
    options.forEach(option -> {
      MetricsUtil.stop();
      options.forEach(opt -> props.remove(opt));
      props.setProperty(option, "{}");
      if (props.getProperty(MetricsUtil.METRICS_FILTER) == null) {
        props.setProperty(MetricsUtil.METRICS_FILTER, MetricsUtil.METRICS_PREFIX + ",b,c");
      } else {
        props.remove(MetricsUtil.METRICS_FILTER);
      }
      MetricsUtil.init(Vertx.builder());
      assertTrue(MetricsUtil.isEnabled());
      assertNotNull(MetricsUtil.getTimerSample());
      assertEquals(1, MetricsUtil.getRegistry().getRegistries().size());
    });
    options.forEach(opt -> props.remove(opt));
    props.remove(MetricsUtil.METRICS_FILTER);
    props.remove(MetricsUtil.ENABLE_METRICS);
  }

  @Test
  public void testDisableMetrics() {
    MetricsUtil.setEnabled(true);
    MetricsUtil.stop();
    verifyNotEnabled();
    MetricsUtil.getRegistry().add(new SimpleMeterRegistry());
    MetricsUtil.stop();
    verifyNotEnabled();
  }

  @Test
  public void testRecordTimer() {
    MetricsUtil.setEnabled(true);
    MetricsUtil.getRegistry().add(new SimpleMeterRegistry());
    Sample sample = MetricsUtil.getTimerSample();
    Timer timer = MetricsUtil.recordTimer(sample, MetricsUtil.METRICS_PREFIX + ".a",
        Arrays.asList(Tag.of("k", "v")));
    assertNotNull(timer);
    assertEquals(1, timer.count());
  }

  @Test
  public void testRecordCounter() {
    MetricsUtil.setEnabled(true);
    MetricsUtil.getRegistry().add(new SimpleMeterRegistry());
    Counter counter = MetricsUtil.recordCounter(MetricsUtil.METRICS_PREFIX + ".b",
        Arrays.asList(Tag.of("k", "v")));
    assertNotNull(counter);
    assertEquals(1, counter.count(), 0.1);
  }

}
