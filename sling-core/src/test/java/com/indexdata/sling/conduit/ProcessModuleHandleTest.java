/*
 * Copyright (c) 2015-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.sling.conduit;

import org.junit.Test;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class ProcessModuleHandleTest {
  private Vertx vertx;
  
  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
  }
  
   @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void test1(TestContext context) {
    final Async async = context.async();
    ProcessDeploymentDescriptor desc = new ProcessDeploymentDescriptor("sleep 10", "");
    ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, desc, 0);
    ModuleHandle mh = pmh;
  
    mh.start(res -> {
      if (!res.succeeded()) {
        System.out.println("CAUSE: " + res.cause());
      }
      context.assertTrue(res.succeeded());
      if (!res.succeeded()) {
        async.complete();
        return;        
      }
      mh.stop(res2 -> {
        context.assertTrue(res2.succeeded());
        async.complete();
      });
    });
  }
  @Test
  public void test2(TestContext context) {
    final Async async = context.async();
    ProcessDeploymentDescriptor desc = new ProcessDeploymentDescriptor("sleepxx 10", "");
    ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, desc, 0);
    ModuleHandle mh = pmh;
  
    mh.start(res -> {
      context.assertFalse(res.succeeded());
      async.complete();
    });
  }
}
