package org.folio.okapi.service.impl;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.logging.Logger;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.bean.Ports;
import org.folio.okapi.common.OkapiLogger;
import org.junit.Assert;
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

    DockerModuleHandle dh = new DockerModuleHandle(vertx, ld,
      "mod-users-5.0.0-SNAPSHOT", ports, 9232, "tcp://localhost:9231");

    dh.start(res -> {
      context.assertTrue(res.failed());
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
      "mod-users-5.0.0-SNAPSHOT", ports, 9232);

    dh.getUrl("/version", res -> {
      if (res.failed()) {
        logger.warn(res.cause().getMessage());
      }
      async.complete();
    });
  }
}
