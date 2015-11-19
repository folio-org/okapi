/*
 * Copyright (c) 2015-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.sling.conduit;

import com.indexdata.sling.util.Box;
import org.junit.Test;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Assert;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.runner.RunWith;


/**
 *
 * @author adam
 */
@RunWith(VertxUnitRunner.class)
public class ProcessModuleHandleTest {
  private Vertx vertx;
  
  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
  }
  
  @Test
  public void test1(TestContext context) {
    ProcessDeploymentDescriptor desc = new ProcessDeploymentDescriptor();
    desc.cmdline_start = "sleep 10";
    ProcessModuleHandle pmh = new ProcessModuleHandle(desc);
    ModuleHandle mh = pmh;
  
    mh.init(vertx);
    mh.start(res -> {
      assertTrue(res.succeeded());
      try {
        Thread.sleep(1000);
      } catch (InterruptedException ex) {
        fail(ex.getMessage());
      }
      mh.stop(res2 -> {
        assertTrue(res2.succeeded());
        context.async().complete();
      });
    });
  }
}
