package org.folio.okapi.managers;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.Arrays;
import java.util.List;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.service.DeploymentStore;
import org.folio.okapi.service.ModuleStore;
import org.folio.okapi.service.impl.DeploymentStoreNull;
import org.folio.okapi.util.TestBase;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class DiscoveryManagerTest extends TestBase {

  private Vertx vertx;

  @Before
  public void setup(TestContext context) {
    vertx = Vertx.vertx();
  }

  @After
  public void after(TestContext context) {
    vertx.close().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void isLeaderWithoutClusterManager(TestContext context) {
    DiscoveryManager discoveryManager = new DiscoveryManager(null, new JsonObject());

    discoveryManager.init(vertx).onComplete(context.asyncAssertSuccess(then ->
        Assert.assertEquals(true, discoveryManager.isLeader())));
  }

  @Test
  public void healthUnknown(TestContext context) {
    Async async = context.async();
    DiscoveryManager discoveryManager = new DiscoveryManager(null, new JsonObject());
    discoveryManager.init(vertx).onComplete(context.asyncAssertSuccess(res -> {
      DeploymentDescriptor dd = new DeploymentDescriptor();
      discoveryManager.health(dd).onComplete(res1 -> {
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
    DiscoveryManager discoveryManager = new DiscoveryManager(null, new JsonObject());
    discoveryManager.init(vertx).onComplete(context.asyncAssertSuccess(res -> {
      DeploymentDescriptor dd = new DeploymentDescriptor();
      dd.setUrl("");
      discoveryManager.health(dd).onComplete(res1 -> {
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
    DiscoveryManager discoveryManager = new DiscoveryManager(null, new JsonObject());
    discoveryManager.init(vertx).onComplete(context.asyncAssertSuccess(res -> {
      DeploymentDescriptor dd = new DeploymentDescriptor();
      dd.setUrl("http://localhost:9230");
      discoveryManager.health(dd).onComplete(res1 -> {
        context.assertTrue(res1.succeeded());
        context.assertFalse(res1.result().isHealthStatus());
        context.assertTrue(res1.result().getHealthMessage().startsWith("Fail"));
        async.complete();
      });
    }));
    async.await();
  }

  class ModuleManagerFake extends ModuleManager {

    public ModuleManagerFake(ModuleStore moduleStore) {
      super(moduleStore, true);
    }
    @Override
    public Future<ModuleDescriptor> get(String id) {
      return Future.succeededFuture(null); // null ModuleDescriptor
    }
  }

  class DeploymentStoreFake extends DeploymentStoreNull {
    final DeploymentDescriptor deploymentDescriptor;

    DeploymentStoreFake(DeploymentDescriptor deploymentDescriptor) {
      this.deploymentDescriptor = deploymentDescriptor;
    }

    @Override
    public Future<List<DeploymentDescriptor>> getAll() {
      return Future.succeededFuture(Arrays.asList(deploymentDescriptor));
    }
  }
  @Test
  public void restartModules(TestContext context) {
    DeploymentDescriptor deploymentDescriptor = new DeploymentDescriptor();
    deploymentDescriptor.setUrl("http://localhost:9231");
    deploymentDescriptor.setSrvcId("module-1.2.3");
    deploymentDescriptor.setInstId("123");
    DeploymentStore deploymentStore = new DeploymentStoreFake(deploymentDescriptor);

    ModuleManager moduleManager = new ModuleManagerFake(null);
    Future<Void> future = deploymentStore.insert(deploymentDescriptor);
    future = future.compose(x -> {
      DiscoveryManager discoveryManager = new DiscoveryManager(deploymentStore, new JsonObject());
      discoveryManager.setModuleManager(moduleManager);
      return discoveryManager.init(vertx)
          .compose(y -> discoveryManager.restartModules())
          .compose(y -> discoveryManager.restartModules());
    });
    future.onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testAddAndDeployIgnoreError(TestContext context) {
    DiscoveryManager discoveryManager = new DiscoveryManager(null, new JsonObject());

    discoveryManager.init(vertx)
        .onComplete(context.asyncAssertSuccess(then ->
            discoveryManager.addAndDeployIgnoreError(new DeploymentDescriptor())
                .onComplete(context.asyncAssertSuccess())));
  }
}
