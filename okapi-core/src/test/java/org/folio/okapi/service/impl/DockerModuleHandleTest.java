package org.folio.okapi.service.impl;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
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

  @Test
  public void testNoDockerAtPort(TestContext context) {
    Vertx vertx = Vertx.vertx();

    LaunchDescriptor ld = new LaunchDescriptor();
    ld.setDockerImage("folioci/mod-users:5.0.0-SNAPSHOT");
    ld.setWaitIterations(3);
    Ports ports = new Ports(9232, 9233);
    JsonObject conf = new JsonObject().put("dockerUrl", "tcp://localhost:9231");

    DockerModuleHandle dh = new DockerModuleHandle(vertx, ld,
        "mod-users-5.0.0-SNAPSHOT", ports, "localhost", 9232, conf);

    dh.start().onComplete(context.asyncAssertFailure(cause ->
          context.assertTrue(cause.getMessage().contains("Connection refused"),
              cause.getMessage())));
    dh.stop().onComplete(context.asyncAssertFailure(cause ->
        context.assertTrue(cause.getMessage().contains("Connection refused"),
            cause.getMessage())));

    dh.startContainer().onComplete(context.asyncAssertFailure(cause ->
        context.assertTrue(cause.getMessage().contains("Connection refused"),
            cause.getMessage())));
    dh.stopContainer().onComplete(context.asyncAssertFailure(cause ->
        context.assertTrue(cause.getMessage().contains("Connection refused"),
            cause.getMessage())));
    dh.deleteContainer().onComplete(context.asyncAssertFailure(cause ->
        context.assertTrue(cause.getMessage().contains("Connection refused"),
            cause.getMessage())));
    }

  @Test
  public void testHostNoExposedPorts(TestContext context) {
    Vertx vertx = Vertx.vertx();

    LaunchDescriptor ld = new LaunchDescriptor();
    ld.setDockerImage("folioci/mod-users:5.0.0-SNAPSHOT");
    ld.setDockerPull(false);
    Ports ports = new Ports(9232, 9233);
    JsonObject conf = new JsonObject().put("dockerUrl", "tcp://localhost:9231");

    DockerModuleHandle dh = new DockerModuleHandle(vertx, ld,
        "mod-users-5.0.0-SNAPSHOT", ports, "localhost",
        0 /* no exposed port */, conf);
    dh.start().onComplete(context.asyncAssertFailure(cause ->
        context.assertEquals("No exposedPorts in image", cause.getMessage())));
  }

  private int dockerMockStatus = 200;
  private int dockerEmptyStatus = 200;
  private JsonObject dockerMockJson = null;
  private String dockerMockText = null;

  private void dockerMockHandle(RoutingContext ctx) {
    if (ctx.request().method().equals(HttpMethod.GET) || ctx.request().path().endsWith("/create")) {
      ctx.response().setStatusCode(dockerMockStatus);
      if (dockerMockJson != null) {
        ctx.response().putHeader("Context-Type", "application/json");
        ctx.response().end(Json.encodePrettily(dockerMockJson));
      } else if (dockerMockText != null) {
        ctx.response().end(dockerMockText);
      } else {
        ctx.response().end();
      }
    } else {
      ctx.response().setStatusCode(dockerEmptyStatus);
      ctx.response().end();
    }
  }

  @Test
  public void testDockerMock(TestContext context) {
    Vertx vertx = Vertx.vertx();
    int dockerPort = 9231;

    Router router = Router.router(vertx);
    router.routeWithRegex("/.*").handler(this::dockerMockHandle);
    HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
    HttpServer listen = vertx.createHttpServer(so)
        .requestHandler(router)
        .listen(dockerPort, context.asyncAssertSuccess());

    LaunchDescriptor ld = new LaunchDescriptor();
    ld.setWaitIterations(2);
    ld.setDockerImage("folioci/mod-x");
    ld.setDockerPull(false);
    EnvEntry[] env = new EnvEntry[1];
    env[0] = new EnvEntry();
    env[0].setName("varName");
    env[0].setValue("varValue");
    ld.setEnv(env);

    String []cmd = {"command"};
    ld.setDockerCmd(cmd);
    Ports ports = new Ports(9232, 9233);
    JsonObject conf = new JsonObject().put("dockerUrl", "tcp://localhost:" + dockerPort);

    DockerModuleHandle dh = new DockerModuleHandle(vertx, ld,
        "mod-users-5.0.0-SNAPSHOT", ports, "localhost",
        9231, conf);

    {
      Async async = context.async();
      dockerMockStatus = 200;
      dockerMockText = "OK";
      dh.start().onComplete(context.asyncAssertFailure(cause -> {
        context.assertTrue(cause.getMessage().contains("Failed to decode"),
            cause.getMessage());
        async.complete();
      }));
      async.await();
    }

    {
      Async async = context.async();
      dockerMockStatus = 102;
      dockerMockText = "Switch";
      dh.start().onComplete(context.asyncAssertFailure(cause -> {
        context.assertEquals("/images/folioci/mod-x/json HTTP error 102\n",
            cause.getMessage());
        async.complete();
      }));
      async.await();
    }

    {
      Async async = context.async();
      dockerMockStatus = 404;
      dockerMockText = "NotHere";
      dh.start().onComplete(context.asyncAssertFailure(cause -> {
        context.assertEquals("/images/folioci/mod-x/json HTTP error 404\nNotHere",
            cause.getMessage());
        async.complete();
      }));
      async.await();
    }

    {
      Async async = context.async();
      dockerMockStatus = 200;
      dockerMockJson = new JsonObject();

      dh.start().onComplete(context.asyncAssertFailure(cause -> {
        context.assertEquals("Missing Config in image", cause.getMessage());
        async.complete();
      }));
      async.await();
    }

    {
      Async async = context.async();
      dockerMockStatus = 200;
      dockerMockJson = new JsonObject();
      dockerMockJson.put("Config", 1);

      dh.start().onComplete(context.asyncAssertFailure(cause -> {
        assertThat(cause.getMessage().contains("class java.lang.Integer cannot be cast to class io.vertx.core.json.JsonObject"));
        async.complete();
      }));
      async.await();
    }

    {
      Async async = context.async();
      dockerMockStatus = 200;
      dockerMockJson = new JsonObject();
      dockerMockJson.put("Config", new JsonObject().put("foo", 1));

      dh.start().onComplete(context.asyncAssertFailure(cause -> {
        context.assertEquals("Missing EXPOSE in image", cause.getMessage());
        async.complete();
      }));
      async.await();
    }

    {
      Async async = context.async();
      dockerMockStatus = 200;
      dockerMockJson = new JsonObject();
      dockerMockJson.put("Config", new JsonObject().put("ExposedPorts",
          new JsonObject().put("notInteger", "a")));

      dh.start().onComplete(context.asyncAssertFailure(cause -> {
        context.assertEquals("For input string: \"notInteger\"", cause.getMessage());
        async.complete();
      }));
      async.await();
    }

    {
      Async async = context.async();
      dockerMockStatus = 200;
      dockerMockJson = new JsonObject();
      dockerMockJson.put("Config", new JsonObject().put("ExposedPorts",
          new JsonObject().put("1", "a").put("2", "b")));

      dh.start().onComplete(context.asyncAssertFailure(cause -> {
        context.assertTrue(cause.getMessage().contains("startContainer HTTP error 200"),
            cause.getMessage());
        async.complete();
      }));
      async.await();
    }

    {
      Async async = context.async();
      dockerMockStatus = 400;
      dockerMockJson = null;
      dockerMockText = "User Error";

      dh.createContainer(9232).onComplete(context.asyncAssertFailure(cause -> {
        context.assertTrue(cause.getMessage().contains("createContainer HTTP error 400"),
            cause.getMessage());
        async.complete();
      }));
      async.await();
    }

    {
      Async async = context.async();
      dockerEmptyStatus = 204;
      dockerMockStatus = 200;
      dockerMockJson = new JsonObject();
      dockerMockJson.put("Config", new JsonObject().put("ExposedPorts",
          new JsonObject().put("8000", "a")));

      dh.start().onComplete(context.asyncAssertSuccess(res1 ->
        dh.stop().onComplete(context.asyncAssertSuccess(res2 -> async.complete())
      )));
      async.await();
    }

    {
      Async async = context.async();
      dockerEmptyStatus = 204;
      dockerMockStatus = 400;

      dh.getContainerLog().onComplete(context.asyncAssertFailure(cause -> {
        context.assertEquals("getContainerLog HTTP error 400", cause.getMessage());
        async.complete();
      }));
      async.await();
    }

    listen.close(context.asyncAssertSuccess());
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
    dh.getUrl("/version").onComplete(context.asyncAssertSuccess(res -> {
      versionRes.put("result", res);
      async.complete();
    }));
    async.await();
    Assume.assumeTrue(versionRes.containsKey("result"));
    context.assertTrue(versionRes.getJsonObject("result").containsKey("Version"));

    // provoke 404 not found
    dh.deleteUrl("/version", "msg").onComplete(context.asyncAssertFailure(cause -> {
      context.assertTrue(cause.getMessage().startsWith("msg HTTP error 404"),
          cause.getMessage());
      // provoke 404 not found
      dh.postUrlJson("/version", "msg", "{}").onComplete(context.asyncAssertFailure(cause2 -> {
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
  }
}
