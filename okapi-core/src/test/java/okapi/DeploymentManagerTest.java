package okapi;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.bean.Ports;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.deployment.DeploymentManager;
import org.folio.okapi.discovery.DiscoveryManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class DeploymentManagerTest {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  Vertx vertx;
  Async async;
  Ports ports;
  DiscoveryManager dis;
  DeploymentManager dm;

  @Before
  public void setUp(TestContext context) {
    async = context.async();
    vertx = Vertx.vertx();
    ports = new Ports(9131, 9140);
    dis = new DiscoveryManager();
    dis.init(vertx, res -> {
      dm = new DeploymentManager(vertx, dis, "myhost.index", ports, 9130);
      async.complete();
    });
  }

  @After
  public void tearDown(TestContext context) {
    async = context.async();
    vertx.close(x -> {
      async.complete();
    });
  }

  @Test
  public void test(TestContext context) {
    async = context.async();
    assertNotNull(vertx);
    test1(context);
  }

  private void test1(TestContext context) {
    LaunchDescriptor descriptor = new LaunchDescriptor();
    descriptor.setExec(
            "java -Dport=%p -jar "
            + "../okapi-test-module/target/okapi-test-module-fat.jar");
    DeploymentDescriptor dd = new DeploymentDescriptor("1", "sid", descriptor);
    dm.deploy(dd, res1 -> {
      assertTrue(res1.succeeded());
      if (res1.failed()) {
        test2(context);
      } else {
        assertEquals("http://myhost.index:9131", res1.result().getUrl());
        dm.undeploy(res1.result().getInstId(), res2 -> {
          assertTrue(res2.succeeded());
          test2(context);
        });
      }
    });
  }

  private void test2(TestContext context) {
    LaunchDescriptor descriptor = new LaunchDescriptor();
    descriptor.setExec(
            "java -Dport=%p -jar "
            + "../okapi-test-module/target/unknown.jar");
    DeploymentDescriptor dd = new DeploymentDescriptor("2", "sid", descriptor);
    dm.deploy(dd, res1 -> {
      assertFalse(res1.succeeded());
      if (res1.failed()) {
        logger.info(res1.cause().getMessage());
        async.complete();
      } else {
        dm.undeploy(res1.result().getInstId(), res2 -> {
          async.complete();
        });
      }
    });
  }
}
