/*
 * Copyright (C) 2015 Index Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okapi;

import org.folio.okapi.util.ModuleHandle;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.util.ProcessModuleHandle;
import org.junit.Test;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.okapi.bean.Ports;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class ProcessModuleHandleTest {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private Vertx vertx;
  private Ports ports = new Ports(0, 10);

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
    LaunchDescriptor desc = new LaunchDescriptor();
    desc.setExec("sleep 10 #%p");
    ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, desc, ports, 0);
    ModuleHandle mh = pmh;

    mh.start(res -> {
      if (!res.succeeded()) {
        logger.error("CAUSE: " + res.cause());
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
    LaunchDescriptor desc = new LaunchDescriptor();
    desc.setExec("sleepxx 10 %p");
    ProcessModuleHandle pmh = new ProcessModuleHandle(vertx, desc, ports, 0);
    ModuleHandle mh = pmh;

    mh.start(res -> {
      context.assertFalse(res.succeeded());
      async.complete();
    });
  }
}
