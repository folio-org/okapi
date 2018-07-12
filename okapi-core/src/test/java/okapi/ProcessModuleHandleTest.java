package okapi;

import org.folio.okapi.util.ModuleHandle;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.util.ProcessModuleHandle;
import org.junit.Test;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.okapi.bean.Ports;
import org.folio.okapi.common.OkapiLogger;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class ProcessModuleHandleTest {

  private final Logger logger = OkapiLogger.get();
  private Vertx vertx;
  private final Ports ports = new Ports(0, 10);
  private final String testModuleArgs
    = "-Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar";

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  private ModuleHandle createModuleHandle(LaunchDescriptor desc, int port) {
    ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, desc, ports, port);
    pmh.setConnectIterMax(5);
    return pmh;
  }

  @Test
  public void test1(TestContext context) {
    final Async async = context.async();
    LaunchDescriptor desc = new LaunchDescriptor();
    // program starts OK, we don't check port
    desc.setExec("java -version %p");
    ModuleHandle mh = createModuleHandle(desc, 0);

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
  public void test1a(TestContext context) {
    final Async async = context.async();
    LaunchDescriptor desc = new LaunchDescriptor();
    // program starts OK, but do not listen to port..
    desc.setExec("java -version %p");
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start(res -> {
      if (res.succeeded()) { // error did not expect to succeed!
        mh.stop(res2 -> {
          context.assertTrue(res2.succeeded());
          context.assertFalse(res.failed());
          async.complete();
        });
      }
      context.assertFalse(res.succeeded());
      String msg = res.cause().getMessage();
      context.assertTrue(msg.startsWith("Deployment failed. Could not connect to port 9231"));
      async.complete();
    });
  }


  @Test
  public void test2(TestContext context) {
    final Async async = context.async();
    LaunchDescriptor desc = new LaunchDescriptor();
    // program does not exist (we hope)
    desc.setExec("gyf 10 %p"); // bad program
    ModuleHandle mh = createModuleHandle(desc, 0);

    mh.start(res -> {
      context.assertFalse(res.succeeded());
      async.complete();
    });
  }

  @Test
  public void test3(TestContext context) {
    final Async async = context.async();
    LaunchDescriptor desc = new LaunchDescriptor();
    // program returns immediately with exit code
    desc.setExec("java -Dport=%p -jar unknown.jar");
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start(res -> {
      context.assertFalse(res.succeeded());
      async.complete();
    });
  }

  @Test
  public void test4(TestContext context) {
    final Async async = context.async();
    LaunchDescriptor desc = new LaunchDescriptor();
    desc.setExec("java -Dport=9000 -jar unknown.jar");
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start(res -> {
      context.assertFalse(res.succeeded());
      context.assertEquals("Can not deploy: No %p in the exec line", res.cause().getMessage());
      async.complete();
    });
  }

  @Test
  public void test5(TestContext context) {
    final Async async = context.async();
    LaunchDescriptor desc = new LaunchDescriptor();
    desc.setCmdlineStart("java -Dport=9000 -jar unknown.jar");
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start(res -> {
      context.assertFalse(res.succeeded());
      context.assertEquals("Can not deploy: No %p in the cmdlineStart", res.cause().getMessage());
      async.complete();
    });
  }

  @Test
  public void test6(TestContext context) {
    final Async async = context.async();
    LaunchDescriptor desc = new LaunchDescriptor();
    // program should operate OK
    desc.setExec("java " + testModuleArgs);
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start(res1 -> {
      mh.stop(res2 -> {
        context.assertTrue(res2.succeeded());
        async.complete();
      });
    });
  }

  @Test
  public void test7(TestContext context) {
    final Async async = context.async();
    // Cannot rely on sh and kill on Windows
    String os = System.getProperty("os.name").toLowerCase();
    if (os.contains("win")) {
      async.complete();
      return;
    }
    LaunchDescriptor desc = new LaunchDescriptor();
    // program should operate OK
    desc.setCmdlineStart("java -DpidFile=test-module.pid " + testModuleArgs + " 2>&1 >/dev/null &");
    desc.setCmdlineStop("kill `cat test-module.pid`; rm -f test-module.pid");
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start(res1 -> {
      context.assertTrue(res1.succeeded());
      mh.stop(res2 -> {
        context.assertTrue(res2.succeeded());
        async.complete();
      });
    });
  }

  @Test
  public void test8(TestContext context) {
    final Async async = context.async();
    // Cannot rely on sh and kill on Windows
    String os = System.getProperty("os.name").toLowerCase();
    if (os.contains("win")) {
      async.complete();
      return;
    }
    LaunchDescriptor desc = new LaunchDescriptor();
    // start works (we don't check port) but stop fails
    desc.setCmdlineStart("echo %p; sleep 1 &");
    desc.setCmdlineStop("gyf");
    ModuleHandle mh = createModuleHandle(desc, 0);

    mh.start(res1 -> {
      context.assertTrue(res1.succeeded());
      mh.stop(res2 -> {
        context.assertTrue(res2.failed());
        async.complete();
      });
    });
  }

  @Test
  public void test9(TestContext context) {
    final Async async = context.async();
    // Cannot rely on sh and kill on Windows
    String os = System.getProperty("os.name").toLowerCase();
    if (os.contains("win")) {
      async.complete();
      return;
    }
    LaunchDescriptor desc = new LaunchDescriptor();
    // start works , but does not listen on port
    desc.setCmdlineStart("echo %p; sleep 2");
    desc.setCmdlineStop("gyf");
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start(res1 -> {
      context.assertTrue(res1.failed());
      async.complete();
    });
  }

  @Test
  public void test10(TestContext context) {
    final Async async = context.async();
    // Cannot rely on sh and kill on Windows
    String os = System.getProperty("os.name").toLowerCase();
    if (os.contains("win")) {
      async.complete();
      return;
    }
    LaunchDescriptor desc = new LaunchDescriptor();
    // start fails (no such file or directory)
    desc.setCmdlineStart("gyf %p");
    desc.setCmdlineStop("gyf");
    ModuleHandle mh = createModuleHandle(desc, 0);

    mh.start(res1 -> {
      context.assertTrue(res1.failed());
      async.complete();
    });

  }

  @Test
  public void test11(TestContext context) {
    final Async async = context.async();
    // Cannot rely on sh and kill on Windows
    String os = System.getProperty("os.name").toLowerCase();
    if (os.contains("win")) {
      async.complete();
      return;
    }
    LaunchDescriptor desc = new LaunchDescriptor();
    // start fails (no such file or directory)
    desc.setCmdlineStart("gyf %p");
    desc.setCmdlineStop("gyf");
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start(res1 -> {
      context.assertTrue(res1.failed());
      async.complete();
    });

  }

}
