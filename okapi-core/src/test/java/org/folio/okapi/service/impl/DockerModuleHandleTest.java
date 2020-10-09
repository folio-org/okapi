package org.folio.okapi.service.impl;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.function.Supplier;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.WithAssertions;
import org.folio.okapi.bean.AnyDescriptor;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.bean.Ports;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.AdditionalAnswers;
import static org.mockito.Mockito.*;

@RunWith(VertxUnitRunner.class)
public class DockerModuleHandleTest implements WithAssertions {

  @Test
  public void testDomainSocketAddresses() {
    StringBuilder s = new StringBuilder();
    String u = DockerModuleHandle.setupDockerAddress(s, "unix://socket");
    Assert.assertEquals("http://localhost/" + DockerModuleHandle.DEFAULT_DOCKER_VERSION, u);
    Assert.assertEquals("socket", s.toString());

    s = new StringBuilder();
    u = DockerModuleHandle.setupDockerAddress(s, DockerModuleHandle.DEFAULT_DOCKER_URL);
    Assert.assertEquals("http://localhost/" + DockerModuleHandle.DEFAULT_DOCKER_VERSION, u);
    Assert.assertEquals("/var/run/docker.sock", s.toString());
  }

  @Test
  public void testHostPortAddresses() {
    StringBuilder s = new StringBuilder();
    String u = DockerModuleHandle.setupDockerAddress(s, "tcp://localhost:4243");
    Assert.assertEquals("http://localhost:4243/" + DockerModuleHandle.DEFAULT_DOCKER_VERSION, u);
    Assert.assertEquals("", s.toString());

    s = new StringBuilder();
    u = DockerModuleHandle.setupDockerAddress(s, "https://localhost:4243");
    Assert.assertEquals("https://localhost:4243/" + DockerModuleHandle.DEFAULT_DOCKER_VERSION, u);
    Assert.assertEquals("", s.toString());

    s = new StringBuilder();
    u = DockerModuleHandle.setupDockerAddress(s, "https://localhost:4243/");
    Assert.assertEquals("https://localhost:4243/" + DockerModuleHandle.DEFAULT_DOCKER_VERSION, u);
    Assert.assertEquals("", s.toString());
  }

  public void testNoDockerAtPort(TestContext context) {
    Vertx vertx = Vertx.vertx();

    LaunchDescriptor ld = new LaunchDescriptor();
    ld.setDockerImage("folioci/mod-users:5.0.0-SNAPSHOT");
    ld.setWaitIterations(3);
    Ports ports = new Ports(9232, 9233);
    JsonObject conf = new JsonObject().put("dockerUrl", "tcp://localhost:9231");

    DockerModuleHandle dh = new DockerModuleHandle(vertx, ld,
        "mod-users-5.0.0-SNAPSHOT", ports, "localhost", 9232, conf);

    dh.start(context.asyncAssertFailure(cause ->
          context.assertTrue(cause.getMessage().contains("java.net.ConnectException"),
              cause.getMessage())));
  }

  @Test
  public void testDockerVersionAtLocal(TestContext context) {
    // native transport = call docker via unix domain socket
    Vertx vertx = Vertx.vertx(new VertxOptions().setPreferNativeTransport(true));
    LaunchDescriptor ld = new LaunchDescriptor();
    Ports ports = new Ports(9232, 9233);

    DockerModuleHandle dh = new DockerModuleHandle(vertx, ld,
        "mod-users-5.0.0-SNAPSHOT", ports, "localhost", 9232, new JsonObject());

    JsonObject versionRes = new JsonObject();
    Async async = context.async();
    dh.getUrl("/version", res -> {
      if (res.succeeded()) {
        versionRes.put("result", res.result());
      }
      async.complete();
    });
    async.await(1000);
    Assume.assumeTrue(versionRes.containsKey("result"));
    context.assertTrue(versionRes.getJsonObject("result").containsKey("Version"));

    // provoke 404 not found
    dh.deleteUrl("/version", "msg", context.asyncAssertFailure(cause -> {
      context.assertTrue(cause.getMessage().startsWith("msg HTTP error 404"),
        cause.getMessage());
      // provoke 404 not found
      dh.postUrlJson("/version", "msg", "{}", context.asyncAssertFailure(cause2 -> {
        context.assertTrue(cause2.getMessage().startsWith("msg HTTP error 404"),
          cause2.getMessage());
      }));
    }));
  }

  @Test
  public void testGetCreateContainerDoc() {
    LaunchDescriptor launchDescriptor = new LaunchDescriptor();
    launchDescriptor.setEnv(new EnvEntry []
        { new EnvEntry("username", "foobar"), new EnvEntry("password", "uvwxyz%p%c")});
    launchDescriptor.setDockerArgs(new AnyDescriptor().set("%p", "%p"));
    Logger logger = mock(Logger.class);
    StringBuilder logMessage = new StringBuilder();
    doAnswer(AdditionalAnswers.answerVoid(
        (String msg, Supplier<Object> supplier) -> logMessage.append(msg).append(supplier.get())))
    .when(logger).info(anyString(), any(Supplier.class));
    DockerModuleHandle dockerModuleHandle = new DockerModuleHandle(Vertx.vertx(), launchDescriptor,
        "mod-users-5.0.0-SNAPSHOT", new Ports(9232, 9233), "localhost", 9232, new JsonObject(),
        logger);
    assertThat(dockerModuleHandle.getCreateContainerDoc(8000))
        .contains("8000/tcp")
        .contains("\"%p\" : \"9232\"")  // dockerArgs variable expansion in values, not in keys
        .contains("foobar")
        .contains("uvwxyz%p%c");  // no %p or %c variable expansion in Env values
    assertThat(logMessage.toString())
        .contains("8000/tcp")
        .contains("\"%p\" : \"9232\"")
        .doesNotContain("foobar")  // no env values in the log because they may contain credentials
        .doesNotContain("uvwxyz");
    Assert.assertEquals(launchDescriptor.getDockerArgs().properties().get("%p"), "%p");
  }
}
