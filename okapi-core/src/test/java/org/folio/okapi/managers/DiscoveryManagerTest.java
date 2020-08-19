package org.folio.okapi.managers;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.okapi.bean.DeploymentDescriptor;
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

  @Test
  public void healthUnknown(TestContext context) {
    Async async = context.async();
    DiscoveryManager discoveryManager = new DiscoveryManager(null);
    discoveryManager.init(Vertx.vertx(), asyncAssertSuccess(context, res -> {
      DeploymentDescriptor dd = new DeploymentDescriptor();
      discoveryManager.health(dd, res1 -> {
        context.assertTrue(res1.succeeded());
        context.assertFalse(res1.result().isHealthStatus());
        context.assertEquals("Unknown", res1.result().getHealthMessage());
        async.complete();
      });
    }));
    async.await();
  }

  @Test
  public void healthUnknown2(TestContext context) {
    Async async = context.async();
    DiscoveryManager discoveryManager = new DiscoveryManager(null);
    discoveryManager.init(Vertx.vertx(), asyncAssertSuccess(context, res -> {
      DeploymentDescriptor dd = new DeploymentDescriptor();
      dd.setUrl("");
      discoveryManager.health(dd, res1 -> {
        context.assertTrue(res1.succeeded());
        context.assertFalse(res1.result().isHealthStatus());
        context.assertEquals("Unknown", res1.result().getHealthMessage());
        async.complete();
      });
    }));
    async.await();
  }

  @Test
  public void healthFails(TestContext context) {
    Async async = context.async();
    DiscoveryManager discoveryManager = new DiscoveryManager(null);
    discoveryManager.init(Vertx.vertx(), asyncAssertSuccess(context, res -> {
      DeploymentDescriptor dd = new DeploymentDescriptor();
      dd.setUrl("http://localhost:9230");
      discoveryManager.health(dd, res1 -> {
        context.assertTrue(res1.succeeded());
        context.assertFalse(res1.result().isHealthStatus());
        context.assertTrue(res1.result().getHealthMessage().startsWith("Fail"));
        async.complete();
      });
    }));
    async.await();
  }

}
