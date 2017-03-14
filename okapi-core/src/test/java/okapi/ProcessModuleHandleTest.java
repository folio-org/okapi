package okapi;

import org.folio.okapi.util.ModuleHandle;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.util.ProcessModuleHandle;
import org.junit.Test;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.okapi.bean.Ports;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class ProcessModuleHandleTest {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private Vertx vertx;
  private Ports ports = new Ports(0, 10);

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void test1(TestContext context) {
    final Async async = context.async();
    LaunchDescriptor desc = new LaunchDescriptor();
    desc.setExec("java -version %p");
    ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, desc, ports, 0);
    ModuleHandle mh = pmh;

    mh.start(res -> {
      if (!res.succeeded()) {
        logger.error("CAUSE: " + res.cause());
      }
      context.assertTrue(res.succeeded());
      if (!res.succeeded()) {
        async.complete();
        return;
      }
      mh.stop(res2 -> {
        context.assertTrue(res2.succeeded());
        async.complete();
      });
    });
  }

  @Test
  public void test2(TestContext context) {
    final Async async = context.async();
    LaunchDescriptor desc = new LaunchDescriptor();
    desc.setExec("sleepxx 10 %p");
    ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, desc, ports, 0);
    ModuleHandle mh = pmh;

    mh.start(res -> {
      context.assertFalse(res.succeeded());
      async.complete();
    });
  }

  @Test
  public void test3(TestContext context) {
    final Async async = context.async();
    LaunchDescriptor desc = new LaunchDescriptor();
    desc.setExec("java -Dport=%p -jar unknown.jar");
    ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, desc, ports, 9131);
    ModuleHandle mh = pmh;

    mh.start(res -> {
      context.assertFalse(res.succeeded());
      async.complete();
    });
  }
}
