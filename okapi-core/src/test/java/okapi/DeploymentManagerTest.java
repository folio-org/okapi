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
import org.folio.okapi.env.EnvManager;
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
  EnvManager em;

  @Before
  public void setUp(TestContext context) {
    async = context.async();
    vertx = Vertx.vertx();
    ports = new Ports(9131, 9140);
    em = new EnvManager();
    em.init(vertx, res1 -> {
      dis = new DiscoveryManager();
      dis.init(vertx, res2 -> {
        dm = new DeploymentManager(vertx, dis, em, "myhost.index", ports, 9130);
        async.complete();
      });
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
  public void test1(TestContext context) {
    async = context.async();
    LaunchDescriptor descriptor = new LaunchDescriptor();
    descriptor.setExec(
            "java -Dport=%p -jar "
            + "../okapi-test-module/target/okapi-test-module-fat.jar");
    DeploymentDescriptor dd = new DeploymentDescriptor("1", "sid", descriptor);
    dm.deploy(dd, res1 -> {
      assertTrue(res1.succeeded());
      if (res1.failed()) {
        async.complete();
      } else {
        assertEquals("http://myhost.index:9131", res1.result().getUrl());
        dm.undeploy(res1.result().getInstId(), res2 -> {
          assertTrue(res2.succeeded());
          async.complete();
        });
      }
    });
  }

  @Test
  public void test2(TestContext context) {
    async = context.async();
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
