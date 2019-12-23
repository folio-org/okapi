package org.folio.okapi.service.impl;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.bean.Ports;
import org.folio.okapi.common.OkapiLogger;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class DockerModuleHandleTest {

  private final Logger logger = OkapiLogger.get();

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
    Async async = context.async();
    Vertx vertx = Vertx.vertx();
    LaunchDescriptor ld = new LaunchDescriptor();
    ld.setDockerImage("folioci/mod-users:5.0.0-SNAPSHOT");
    ld.setWaitIterations(3);
    Ports ports = new Ports(9232, 9233);
    JsonObject conf = new JsonObject().put("dockerUrl", "tcp://localhost:9231");

    DockerModuleHandle dh = new DockerModuleHandle(vertx, ld,
      "mod-users-5.0.0-SNAPSHOT", ports, "localhost", 9232, conf);

    dh.start(res -> {
      context.assertTrue(res.failed());
      if (res.failed()) {
        context.assertTrue(res.cause().getMessage().contains("Connection refused"),
          res.cause().getMessage());
      }
      async.complete();
    });
  }

  @Test
  public void testDockerVersionAtLocal(TestContext context) {
    Async async = context.async();

    VertxOptions options = new VertxOptions();
    options.setPreferNativeTransport(true);
    Vertx vertx = Vertx.vertx(options);
    LaunchDescriptor ld = new LaunchDescriptor();
    Ports ports = new Ports(9232, 9233);

    DockerModuleHandle dh = new DockerModuleHandle(vertx, ld,
      "mod-users-5.0.0-SNAPSHOT", ports, "localhost", 9232, new JsonObject());

    dh.getUrl("/version", res -> {
      Assume.assumeTrue(res.succeeded());
      if (res.failed()) {
        logger.warn(res.cause().getMessage());
        async.complete();
        return;
      }
      dh.deleteUrl("/version", res2 -> { // provoke 404 not found
        context.assertTrue(res2.failed());
        if (res2.failed()) {
          context.assertTrue(res2.cause().getMessage().contains("HTTP error 404"),
            res2.cause().getMessage());
        }
        async.complete();
      });

    });
  }
}
