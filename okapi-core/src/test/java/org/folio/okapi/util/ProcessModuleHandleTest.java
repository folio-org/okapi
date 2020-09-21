package org.folio.okapi.util;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.bean.Ports;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.service.ModuleHandle;
import org.folio.okapi.service.impl.ProcessModuleHandle;
import org.junit.Assume;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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
    desc.setWaitIterations(15);
    ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, desc, "test", ports, port);
    return pmh;
  }

  @Test
  public void testProgramNoPort(TestContext context) {
    LaunchDescriptor desc = new LaunchDescriptor();
    // program starts OK, we don't check port
    desc.setExec("java -version %p");
    ModuleHandle mh = createModuleHandle(desc, 0);

    mh.start(context.asyncAssertSuccess(res1 ->
      mh.stop(context.asyncAssertSuccess())
    ));
  }

  @Test
  public void testProgramNoListener(TestContext context) {
    LaunchDescriptor desc = new LaunchDescriptor();
    // program starts OK, but do not listen to port..
    desc.setExec("java -version %p");
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start(context.asyncAssertFailure(cause ->
      context.assertTrue(cause.getMessage().startsWith("Deployment failed. Could not connect to port 9231"))
    ));
  }

  @Test
  public void testProgramDoesNotExist(TestContext context) {
    LaunchDescriptor desc = new LaunchDescriptor();
    // program does not exist (we hope)
    desc.setExec("gyf 10 %p"); // bad program
    ModuleHandle mh = createModuleHandle(desc, 0);

    mh.start(context.asyncAssertFailure(cause ->
        context.assertEquals("Could not execute gyf 10 0", cause.getMessage())
    ));
  }

  @Test
  public void testProgramReturnsError(TestContext context) {
    LaunchDescriptor desc = new LaunchDescriptor();
    // program returns immediately with exit code
    desc.setExec("java -Dport=%p -jar unknown.jar");
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start(context.asyncAssertFailure());
  }

  @Test
  public void testNoExecNoCmdLineStart(TestContext context) {
    LaunchDescriptor desc = new LaunchDescriptor();
    // no cmdlineStart, no exec
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start(context.asyncAssertFailure(cause ->
      context.assertEquals("Can not deploy: No exec, no CmdlineStart in LaunchDescriptor", cause.getMessage())
    ));
  }

  @Test
  public void testMissingPercentPexec(TestContext context) {
    LaunchDescriptor desc = new LaunchDescriptor();
    desc.setExec("java -Dport=9000 -jar unknown.jar");
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start(context.asyncAssertFailure(cause ->
      context.assertEquals("Can not deploy: No %p in the exec line", cause.getMessage())
    ));
  }

  @Test
  public void testMissingPercentPcmdlineStart(TestContext context) {
    LaunchDescriptor desc = new LaunchDescriptor();
    desc.setCmdlineStart("java -Dport=9000 -jar unknown.jar");
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start(context.asyncAssertFailure(cause ->
      context.assertEquals("Can not deploy: No %p in the cmdlineStart", cause.getMessage())
    ));
  }

  @Test
  public void testExecOk(TestContext context) {
    LaunchDescriptor desc = new LaunchDescriptor();
    // program should operate OK
    desc.setExec("java " + testModuleArgs);
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start(context.asyncAssertSuccess(res ->
      mh.stop(context.asyncAssertSuccess())
    ));
  }

  @Test
  public void testPortAlreadyInUse(TestContext context) {
    final NetServer ns = vertx.createNetServer().connectHandler( res -> { res.close(); });
    ns.listen(9231, context.asyncAssertSuccess(res -> {
      LaunchDescriptor desc = new LaunchDescriptor();
      desc.setExec("java " + testModuleArgs);
      ModuleHandle mh = createModuleHandle(desc, 9231);
      mh.start(context.asyncAssertFailure(cause -> {
        context.assertEquals("port 9231 already in use", cause.getMessage());
        ns.close();
        // stop is not necessary, but check that we can call it anyway
        mh.stop(context.asyncAssertSuccess());
      }));
    }));
  }

  @Test
  public void testCmdlineOk(TestContext context) {
    // Cannot rely on sh and kill on Windows
    String os = System.getProperty("os.name").toLowerCase();
    Assume.assumeFalse(os.contains("win"));

    LaunchDescriptor desc = new LaunchDescriptor();
    // program should operate OK
    desc.setCmdlineStart("java -DpidFile=test-module.pid " + testModuleArgs + " 2>&1 >/dev/null &");
    desc.setCmdlineStop("kill `cat test-module.pid`; rm -f test-module.pid");
    ModuleHandle mh = createModuleHandle(desc, 9231);

    mh.start(context.asyncAssertSuccess(res -> mh.stop(context.asyncAssertSuccess())));
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

    mh.start(context.asyncAssertSuccess(res ->
        mh.stop(context.asyncAssertFailure(cause ->
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

    mh.start(context.asyncAssertSuccess(res ->
        mh.stop(context.asyncAssertFailure(cause ->
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

    mh.start(context.asyncAssertFailure());
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

    mh.start(context.asyncAssertFailure(cause ->
        context.assertEquals("Could not execute gyf 0", cause.getMessage())
    ));
  }

  @Test
  public void testExecMultiple(TestContext context) {
    LaunchDescriptor desc = new LaunchDescriptor();
    // program should operate OK
    desc.setExec("java " + testModuleArgs);
    int no = 9; // number of processes to spawn
    ModuleHandle[] mhs = new ModuleHandle[no];
    int i;
    for (i = 0; i < no; i++) {
      mhs[i] = createModuleHandle(desc, 9231+i);
    }
    logger.debug("Start");
    List<Future> futures = new LinkedList<>();
    for (ModuleHandle mh : mhs) {
      Promise<Void> promise = Promise.promise();
      mh.start(promise::handle);
      futures.add(promise.future());
    }
    Async async1 = context.async();
    CompositeFuture.all(futures).onComplete(context.asyncAssertSuccess(res -> async1.complete()));
    async1.await();

    logger.debug("Wait");
    Async async = context.async();
    vertx.setTimer(4000, x -> async.complete());
    async.await();
    logger.debug("Stop");
    futures = new LinkedList<>();
    for (ModuleHandle mh : mhs) {
      Promise<Void> promise = Promise.promise();
      mh.stop(promise::handle);
      futures.add(promise.future());
    }
    Async async2 = context.async();
    CompositeFuture.all(futures).onComplete(context.asyncAssertSuccess(res -> async2.complete()));
    async2.await();
  }
}
