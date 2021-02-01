package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.bean.Ports;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.service.ModuleHandle;
import org.junit.Assume;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
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

  @Rule
  public TestRule watcher = new TestWatcher() {
    protected void starting(Description description) {
      System.out.println("Starting test: " + description.getMethodName());
    }
  };

  private ModuleHandle createModuleHandle(LaunchDescriptor desc, int port) {
    return new ProcessModuleHandle(vertx, desc, "test", ports, port);
  }

  @Test
  public void testProgramNoPort(TestContext context) {
    LaunchDescriptor desc = new LaunchDescriptor();
    // program starts OK, we don't check port
    desc.setExec("java -version %p");
    ModuleHandle mh = createModuleHandle(desc, 0);
    mh.start().onComplete(context.asyncAssertSuccess(res1 ->
      mh.stop().onComplete(context.asyncAssertSuccess())
    ));
  }

  @Test
  public void testProgramNoListener(TestContext context) {
    LaunchDescriptor desc = new LaunchDescriptor();
    // program starts OK, but do not listen to port..
    desc.setExec("java -version %p");
    desc.setWaitIterations(3);
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start().onComplete(context.asyncAssertFailure(cause ->
      context.assertTrue(cause.getMessage().startsWith("Deployment failed. Could not connect to port 9231"))
    ));
  }

  @Test
  public void testProgramDoesNotExist(TestContext context) {
    LaunchDescriptor desc = new LaunchDescriptor();
    // program does not exist (we hope)
    desc.setExec("gyf 10 %p"); // bad program
    ModuleHandle mh = createModuleHandle(desc, 0);

    mh.start().onComplete(context.asyncAssertFailure(cause ->
        context.assertEquals("Could not execute gyf 10 0", cause.getMessage())
    ));
  }

  @Test
  public void testProgramReturnsError(TestContext context) {
    LaunchDescriptor desc = new LaunchDescriptor();
    // program returns immediately with exit code
    desc.setExec("java -Dport=%p -jar unknown.jar");
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start().onComplete(context.asyncAssertFailure());
  }

  @Test
  public void testNoExecNoCmdLineStart(TestContext context) {
    LaunchDescriptor desc = new LaunchDescriptor();
    // no cmdlineStart, no exec
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start().onComplete(context.asyncAssertFailure(cause ->
      context.assertEquals("Can not deploy: No exec, no CmdlineStart in LaunchDescriptor", cause.getMessage())
    ));
  }

  @Test
  public void testMissingPercentPexec(TestContext context) {
    LaunchDescriptor desc = new LaunchDescriptor();
    desc.setExec("java -Dport=9000 -jar unknown.jar");
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start().onComplete(context.asyncAssertFailure(cause ->
      context.assertEquals("Can not deploy: No %p in the exec line", cause.getMessage())
    ));
  }

  @Test
  public void testMissingPercentPcmdlineStart(TestContext context) {
    LaunchDescriptor desc = new LaunchDescriptor();
    desc.setCmdlineStart("java -Dport=9000 -jar unknown.jar");
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start().onComplete(context.asyncAssertFailure(cause ->
      context.assertEquals("Can not deploy: No %p in the cmdlineStart", cause.getMessage())
    ));
  }

  @Test
  public void testExecOk(TestContext context) {
    LaunchDescriptor desc = new LaunchDescriptor();
    // program should operate OK
    desc.setExec("java " + testModuleArgs);
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start().onComplete(context.asyncAssertSuccess(res ->
      mh.stop().onComplete(context.asyncAssertSuccess())
    ));
  }

  @Test
  public void testDoubleStartStop(TestContext context) {
    LaunchDescriptor desc = new LaunchDescriptor();
    // program should operate OK
    desc.setExec("java " + testModuleArgs);
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start().onComplete(context.asyncAssertSuccess(res -> {
      mh.start().onComplete(context.asyncAssertFailure(cause ->
        mh.stop().onComplete(context.asyncAssertSuccess(res2 -> {
            context.assertEquals("already started "
                + "java -Dport=9231 -jar ../okapi-test-module/target/okapi-test-module-fat.jar", cause.getMessage());
            mh.stop().onComplete(context.asyncAssertSuccess());
         }))
      ));
    }));
  }

  @Test
  public void testPortAlreadyInUse(TestContext context) {
    final NetServer ns = vertx.createNetServer().connectHandler( res -> { res.close(); });
    ns.listen(9231, context.asyncAssertSuccess(res -> {
      LaunchDescriptor desc = new LaunchDescriptor();
      desc.setExec("java " + testModuleArgs);
      ModuleHandle mh = createModuleHandle(desc, 9231);
      mh.start().onComplete(context.asyncAssertFailure(cause -> {
        ns.close();
        context.assertEquals("port 9231 already in use", cause.getMessage());
        // stop is not necessary, but check that we can call it anyway
        mh.stop().onComplete(context.asyncAssertSuccess());
      }));
    }));
  }

  @Test
  public void testWaitPortClose(TestContext context) {
    final NetServer ns = vertx.createNetServer().connectHandler( res -> { res.close(); });
    ns.listen(9231, context.asyncAssertSuccess(res -> {
      LaunchDescriptor desc = new LaunchDescriptor();
      desc.setExec("java " + testModuleArgs);
      ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, desc, "id", new Ports(9231, 9233), 9231);
      pmh.waitPortToClose(0)
          .onComplete(context.asyncAssertFailure(x -> {
            ns.close();
            context.assertEquals("port 9231 not shut down", x.getMessage());
          }));
    }));
  }

  @Test
  public void testCmdlineOk(TestContext context) {
    // Cannot rely on sh and kill on Windows
    String os = System.getProperty("os.name").toLowerCase();
    Assume.assumeFalse(os.contains("win"));

    LaunchDescriptor desc = new LaunchDescriptor();
    EnvEntry [] envEntries = new EnvEntry[] { new EnvEntry("myenv", "val") };
    desc.setEnv(envEntries);

    // program should operate OK
    desc.setCmdlineStart("test \"$myenv\" = \"val\" && java -DpidFile=test-module.pid " + testModuleArgs + " 2>&1 >/dev/null &");
    desc.setCmdlineStop("kill `cat test-module.pid`; rm -f test-module.pid");
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start().onComplete(context.asyncAssertSuccess(
        res -> mh.stop().onComplete(context.asyncAssertSuccess())));
  }

  @Test
  public void testCmdlineStopNotFound(TestContext context) {
    // Cannot rely on sh and kill on Windows
    String os = System.getProperty("os.name").toLowerCase();
    Assume.assumeFalse(os.contains("win"));
    LaunchDescriptor desc = new LaunchDescriptor();
    // start works (we don't check port) but stop fails
    desc.setCmdlineStart("echo %p; sleep 1 &");
    desc.setCmdlineStop("gyf");
    ModuleHandle mh = createModuleHandle(desc, 0);

    mh.start().onComplete(context.asyncAssertSuccess(res ->
        mh.stop().onComplete(context.asyncAssertFailure(cause ->
            context.assertEquals("Could not execute gyf", cause.getMessage())
        ))
    ));
  }

  @Test
  public void testCmdlineStopBadExit(TestContext context) {
    // Cannot rely on sh and kill on Windows
    String os = System.getProperty("os.name").toLowerCase();
    Assume.assumeFalse(os.contains("win"));
    LaunchDescriptor desc = new LaunchDescriptor();
    // start works (we don't check port) but stop fails
    desc.setCmdlineStart("echo %p; sleep 1 &");
    desc.setCmdlineStop("false");
    ModuleHandle mh = createModuleHandle(desc, 0);

    mh.start().onComplete(context.asyncAssertSuccess(res ->
        mh.stop().onComplete(context.asyncAssertFailure(cause ->
            context.assertEquals("Service returned with exit code 1", cause.getMessage())
        ))
    ));
  }

  @Test
  public void testCmdlineStartNoListener(TestContext context) {
    // Cannot rely on sh and kill on Windows
    String os = System.getProperty("os.name").toLowerCase();
    Assume.assumeFalse(os.contains("win"));
    LaunchDescriptor desc = new LaunchDescriptor();
    // start works , but does not listen on port
    desc.setCmdlineStart("echo %p; sleep 2");
    desc.setCmdlineStop("gyf");
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start().onComplete(context.asyncAssertFailure());
  }

  @Test
  public void testCmdlineStartNotFound(TestContext context) {
    // Cannot rely on sh and kill on Windows
    String os = System.getProperty("os.name").toLowerCase();
    Assume.assumeFalse(os.contains("win"));
    LaunchDescriptor desc = new LaunchDescriptor();
    // start fails (no such file or directory)
    desc.setCmdlineStart("gyf %p");
    desc.setCmdlineStop("gyf");
    ModuleHandle mh = createModuleHandle(desc, 0);

    mh.start().onComplete(context.asyncAssertFailure(cause ->
        context.assertEquals("Could not execute gyf 0", cause.getMessage())
    ));
  }

  @Test
  public void testExecMultiple(TestContext context) {
    LaunchDescriptor desc = new LaunchDescriptor();
    // program should operate OK
    desc.setExec("java " + testModuleArgs);
    int no = 3; // number of processes to spawn
    ModuleHandle[] mhs = new ModuleHandle[no];
    int i;
    for (i = 0; i < no; i++) {
      mhs[i] = createModuleHandle(desc, 9231+i);
    }
    logger.debug("Start");
    List<Future<Void>> futures = new LinkedList<>();
    for (ModuleHandle mh : mhs) {
      futures.add(mh.start());
    }
    Async async1 = context.async();
    GenericCompositeFuture.all(futures).onComplete(context.asyncAssertSuccess(res -> async1.complete()));
    async1.await();

    futures = new LinkedList<>();
    for (ModuleHandle mh : mhs) {
      futures.add(mh.stop());
    }
    Async async2 = context.async();
    GenericCompositeFuture.all(futures).onComplete(context.asyncAssertSuccess(res -> async2.complete()));
    async2.await();
  }
}
