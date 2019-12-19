package org.folio.okapi.service.impl;

import org.junit.Assert;
import org.junit.Test;

public class DockerModuleHandleTest {

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

    s = new StringBuilder();
    u = DockerModuleHandle.setupDockerAddress(s, "tcp://localhost:4243/v1.27");
    Assert.assertEquals("http://localhost:4243/v1.27", u);
    Assert.assertEquals("", s.toString());

  }

}
