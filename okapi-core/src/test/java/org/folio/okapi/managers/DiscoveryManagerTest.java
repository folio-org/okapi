package org.folio.okapi.managers;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.folio.okapi.util.TestBase;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class DiscoveryManagerTest extends TestBase {

  @Test
  public void isLeaderWithoutClusterManager(TestContext context) {
    DiscoveryManager discoveryManager = new DiscoveryManager(null);
    discoveryManager.init(Vertx.vertx(), asyncAssertSuccess(context, then -> {
      Assert.assertEquals(true, discoveryManager.isLeader());
    }));
  }
}
