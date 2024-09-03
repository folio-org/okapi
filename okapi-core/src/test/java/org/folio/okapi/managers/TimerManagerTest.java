package org.folio.okapi.managers;

import static io.vertx.core.Future.succeededFuture;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.RoutingEntry;
import org.folio.okapi.bean.Schedule;
import org.folio.okapi.bean.TimerDescriptor;
import org.folio.okapi.service.impl.TimerStoreMemory;
import org.folio.okapi.util.TenantProductSeq;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(5000)
@ExtendWith(VertxExtension.class)
class TimerManagerTest {

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
    routingEntry.setSchedule(new Schedule());
    Assertions.assertThat(TimerManager.isPatchReset(routingEntry)).isFalse();
    routingEntry.setUnit("second");
    Assertions.assertThat(TimerManager.isPatchReset(routingEntry)).isFalse();
    routingEntry.setDelay("10");
    Assertions.assertThat(TimerManager.isPatchReset(routingEntry)).isFalse();
  }

  @Test
  void getModuleForTimer() {
    var timerManager = new TimerManager(null, true);
    var x = moduleDescriptor("mod-x-1.0.0", 11);
    var y = moduleDescriptor("mod-y-2.3.4", 11);
    var mds = List.of(x, y);
    Assertions.assertThat(timerManager.getModuleForTimer(mds, "tenant_mod-x_0")).isEqualTo(x);
    Assertions.assertThat(timerManager.getModuleForTimer(mds, "tenant_mod-x_11")).isNull();
    Assertions.assertThat(timerManager.getModuleForTimer(mds, "tenant_mod-y_3")).isEqualTo(y);
    Assertions.assertThat(timerManager.getModuleForTimer(mds, "the_test_tenant_mod-y_3")).isEqualTo(y);
    Assertions.assertThat(timerManager.getModuleForTimer(mds, "invalid")).isNull();
  }

  ModuleDescriptor moduleDescriptor(String moduleId, int timerEntryCount) {
    var routingEntries = new RoutingEntry[timerEntryCount];
    for (int i = 0; i < routingEntries.length; i++) {
      var routingEntry = new RoutingEntry();
      routingEntry.setDelay("1");
      routingEntry.setUnit("second");
      routingEntries[i] = routingEntry;
    }
    var timers = new InterfaceDescriptor("_timer", "1.0");
    timers.setHandlers(routingEntries);
    timers.setInterfaceType("system");
    InterfaceDescriptor [] provides = { timers };
    var moduleDescriptor = new ModuleDescriptor(moduleId);
    moduleDescriptor.setProvides(provides);
    return moduleDescriptor;
  }

  TimerDescriptor timerDescriptor(String id, Integer seconds) {
    var routingEntry = new RoutingEntry();
    if (seconds != null) {
      routingEntry.setDelay("" + seconds);
      routingEntry.setUnit("second");
    }
    var timerDescriptor = new TimerDescriptor();
    timerDescriptor.setId(id);
    timerDescriptor.setRoutingEntry(routingEntry);
    timerDescriptor.setModified(true);
    return timerDescriptor;
  }

  Future<Void> testPatchTimer(TimerManager timerManager, String productSeq, Integer seconds) {
    return timerManager.patchTimer("test_tenant", timerDescriptor(productSeq, seconds))
        .compose(x -> timerManager.getTimer("test_tenant", productSeq))
        .map(timerDescriptor -> {
          var delay = timerDescriptor.getRoutingEntry().getDelay();
          if (seconds == null) {
            Assertions.assertThat(delay).isEqualTo("" + 1);
          } else {
            Assertions.assertThat(delay).isEqualTo("" + seconds);
          }
          return null;
        });
  }

  @Test
  void init(Vertx vertx, VertxTestContext vtc) {
    var timerStore = new TimerStoreMemory(timerDescriptor("mod-y_0", 2));
    var mds = List.of(moduleDescriptor("mod-x-1.0.0", 1), moduleDescriptor("mod-y-2.3.4", 2));
    var tenantManager = mock(TenantManager.class);
    when(tenantManager.allTenants()).thenReturn(succeededFuture(List.of("test_tenant")));
    when(tenantManager.getEnabledModules("test_tenant")).thenReturn(succeededFuture(mds));
    var discoveryManager = mock(DiscoveryManager.class);
    var proxyService = mock(ProxyService.class);
    var timerManager = new TimerManager(timerStore, true);
    timerManager.init(vertx, tenantManager, discoveryManager, proxyService)
    .compose(x -> testPatchTimer(timerManager, "mod-y_0", 0))
    .compose(x -> testPatchTimer(timerManager, "mod-y_0", null))
    .compose(x -> testPatchTimer(timerManager, "mod-y_0", 1))
    .compose(x -> testPatchTimer(timerManager, "mod-y_0", 2))
    .compose(x -> testPatchTimer(timerManager, "mod-y_0", 2))
    .compose(x -> testPatchTimer(timerManager, "mod-x_0", 2))
    .andThen(vtc.succeedingThenComplete());
  }

  @ParameterizedTest
  @CsvSource(textBlock = """
                  , , false
                  foo, foo, false
                  foo_0, foo, false
                  t_foo_0, t, true
                  t_foo, t, false
  """)
  void belongs(String timerId, String tenantId, boolean expected) {
    var timerDescriptor = new TimerDescriptor();
    timerDescriptor.setId(timerId);
    Assertions.assertThat(TimerManager.belongs(timerDescriptor, tenantId)).isEqualTo(expected);
  }
}
