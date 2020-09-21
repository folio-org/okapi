package org.folio.okapi.util;

import org.folio.okapi.service.ModuleHandle;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.service.impl.ProcessModuleHandle;
import org.junit.Assume;
import org.junit.Test;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetServer;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.logging.log4j.Logger;
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

    mh.start(context.asyncAssertFailure());
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
  public void testCmdLineOk(TestContext context) {
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
  public void testBadCmdlineStop(TestContext context) {
    // Cannot rely on sh and kill on Windows
    String os = System.getProperty("os.name").toLowerCase();
    Assume.assumeFalse(os.contains("win"));
    LaunchDescriptor desc = new LaunchDescriptor();
    // start works (we don't check port) but stop fails
    desc.setCmdlineStart("echo %p; sleep 1 &");
    desc.setCmdlineStop("gyf");
    ModuleHandle mh = createModuleHandle(desc, 0);

    mh.start(context.asyncAssertSuccess(res -> mh.stop(context.asyncAssertFailure())));
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
  public void testCmdlineStartFails(TestContext context) {
    // Cannot rely on sh and kill on Windows
    String os = System.getProperty("os.name").toLowerCase();
    Assume.assumeFalse(os.contains("win"));
    LaunchDescriptor desc = new LaunchDescriptor();
    // start fails (no such file or directory)
    desc.setCmdlineStart("gyf %p");
    desc.setCmdlineStop("gyf");
    ModuleHandle mh = createModuleHandle(desc, 0);

    mh.start(context.asyncAssertFailure());
  }

}
