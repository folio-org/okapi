package org.folio.okapi.managers;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.service.DeploymentStore;
import org.folio.okapi.service.EnvStore;
import org.folio.okapi.service.impl.EnvStoreNull;
import org.folio.okapi.service.impl.DeploymentStoreNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class DeploymentManagerTest {

  private final Logger logger = OkapiLogger.get();

  private Vertx vertx;
  private DeploymentManager dm;

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  private void createDeploymentManager(TestContext context, EnvStore envStore, JsonObject config) {
    Async async = context.async();
    EnvManager em = new EnvManager(envStore);
    DeploymentStore ds = new DeploymentStoreNull();
    DiscoveryManager dis = new DiscoveryManager(ds);
    em.init(vertx)
        .compose(x -> dis.init(vertx))
        .onComplete(context.asyncAssertSuccess(x -> {
          dm = new DeploymentManager(vertx, dis, em, "myhost.index", 9230, "", config);
          async.complete();
        }));
    async.await();
}

  private void createDeploymentManager(TestContext context) {
    DeploymentManagerTest.this.createDeploymentManager(context, new EnvStoreNull(), new JsonObject());
  }

  @Test
  public void testDeployProcess(TestContext context) {
    createDeploymentManager(context);
    Async async = context.async();
    LaunchDescriptor descriptor = new LaunchDescriptor();
    descriptor.setExec(
      "java -Dport=%p -jar "
      + "../okapi-test-module/target/okapi-test-module-fat.jar");
    DeploymentDescriptor dd = new DeploymentDescriptor("1", "sid", descriptor);
    dm.deploy(dd).onComplete(res1 -> {
      context.assertTrue(res1.succeeded());
      dm.undeploy(res1.result().getInstId()).onComplete(res2 -> {
        // after undeploy so we have no stale process
        context.assertEquals("http://myhost.index:9231", res1.result().getUrl());
        context.assertTrue(res2.succeeded());
        dm.shutdown().onComplete(res3 -> {
          context.assertTrue(res3.succeeded());
          async.complete();
        });
      });
    });
    async.await();
  }

  @Test
  public void testProcessNotFound(TestContext context) {
    createDeploymentManager(context);
    Async async = context.async();
    LaunchDescriptor descriptor = new LaunchDescriptor();
    descriptor.setExec(
            "java -Dport=%p -jar "
            + "../okapi-test-module/target/unknown.jar");
    DeploymentDescriptor dd = new DeploymentDescriptor("2", "sid", descriptor);
    dm.deploy(dd).onComplete(res -> {
      context.assertFalse(res.succeeded());
      context.assertEquals("Service sid returned with exit code 1", res.cause().getMessage());
      async.complete();
    });
    async.await();
  }

  @Test
  public void testNoPortsAvailable(TestContext context) {
    JsonObject conf = new JsonObject();
    conf.put("port_start", "9231");
    conf.put("port_end", "9231");
    DeploymentManagerTest.this.createDeploymentManager(context, new EnvStoreNull(), conf);
    Async async = context.async();
    LaunchDescriptor descriptor = new LaunchDescriptor();
    descriptor.setExec(
            "java -Dport=%p -jar "
            + "../okapi-test-module/target/unknown.jar");
    DeploymentDescriptor dd = new DeploymentDescriptor("2", "sid", descriptor);
    dm.deploy(dd).onComplete(res -> {
      context.assertFalse(res.succeeded());
      context.assertEquals("all ports in use", res.cause().getMessage());
      async.complete();
    });
    async.await();
  }

  @Test
  public void testUndeployNotFound(TestContext context) {
    createDeploymentManager(context);
    Async async = context.async();
    LaunchDescriptor descriptor = new LaunchDescriptor();
    dm.undeploy("1234").onComplete(res -> {
      context.assertFalse(res.succeeded());
      context.assertEquals("not found: 1234", res.cause().getMessage());
      async.complete();
    });
    async.await();
  }


}
