/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import okapi.bean.DeploymentDescriptor;
import okapi.bean.Ports;
import okapi.bean.ProcessDeploymentDescriptor;
import okapi.deployment.DeploymentManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;


@RunWith(VertxUnitRunner.class)
public class DeploymentManagerTest {
  
  Vertx vertx;
  Async async;
  Ports ports;

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    ports = new Ports(9131, 9140);
  }

  @After
  public void tearDown(TestContext context) {
    async = context.async();
    vertx.close(x -> {
      async.complete();
    });
  }

  @Test
  public void test1(TestContext context) {
    async = context.async();
    assertNotNull(vertx);
    DeploymentManager dm = new DeploymentManager(vertx, "myhost.index", ports);
    ProcessDeploymentDescriptor descriptor = new ProcessDeploymentDescriptor();
    descriptor.setCmdlineStart(
            "java -Dport=%p -jar "
                    +"../okapi-sample-module/target/okapi-sample-module-fat.jar");
    DeploymentDescriptor dd = new DeploymentDescriptor("1", "sample", descriptor);
    dm.deploy(dd, res1 -> {
      assertTrue(res1.succeeded());
      if (res1.failed()) {
        async.complete();
      } else {
        assertEquals("http://myhost.index:9131", res1.result().getUrl());
        dm.undeploy(res1.result().getId(), res2 -> {
          assertTrue(res2.succeeded());
          async.complete();
        });
      }
    });
  }

  @Test
  public void test2(TestContext context) {
    async = context.async();
    assertNotNull(vertx);
    DeploymentManager dm = new DeploymentManager(vertx, "myhost.index", ports);
    ProcessDeploymentDescriptor descriptor = new ProcessDeploymentDescriptor();
    descriptor.setCmdlineStart(
            "java -Dport=%p -jar "
                    +"../okapi-sample-module/target/unknown.jar");
    DeploymentDescriptor dd = new DeploymentDescriptor("1", "unknown", descriptor);
    dm.deploy(dd, res1 -> {
      assertFalse(res1.succeeded());
      if (res1.failed()) {
        async.complete();
      } else {
        dm.undeploy(res1.result().getId(), res2 -> {
          async.complete();
        });
      }
    });
  }

}
