/*
 * Copyright (c) 2015-2015, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package com.indexdata.sling.conduit;

import com.indexdata.sling.MainVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import static io.vertx.core.impl.VertxImpl.context;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class DeployModuleTest {
  Vertx vertx;
  
  public DeployModuleTest() {
  }
  
  @BeforeClass
  public static void setUpClass() {
  }
  
  @AfterClass
  public static void tearDownClass() {
  }
  
  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    
    DeploymentOptions opt = new DeploymentOptions();
    vertx.deployVerticle(MainVerticle.class.getName(),
            opt, context.asyncAssertSuccess());
    
  }
  
  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }
  
  private int port = Integer.parseInt(System.getProperty("port", "8080"));
            
  @Test
  public void test1(TestContext context) {
    final String doc = "{\n"
            + "  \"name\" : \"hello\" ,\n"
            + "  \"descriptor\" : {\n"
            + "     \"cmdlineStart\" : \"dd if=/dev/random of=/tmp/sling bs=5 count=%p\"\n"
            + "  }\n"
            + "}";
    final Async async = context.async();
    HttpClient c = vertx.createHttpClient();
    c.post(port, "localhost", "/conduit/enabled_modules", response -> {
      context.assertEquals(201, response.statusCode());
      response.endHandler(x -> {
        deleteIt(context, async, response.getHeader("Location"));
      });
    }).end(doc);
  }

  public void deleteIt(TestContext context, Async async, String location) {
    HttpClient c = vertx.createHttpClient();
    System.out.println("Location=" + location);
    
    // The HttpClient.delete does not take a full URI, so just get the path
    int p = location.indexOf("/conduit");
    context.assertTrue(p > 0);
    String uri = location.substring(p);
    c.delete(port, "localhost", uri, response -> {
      context.assertEquals(204, response.statusCode());
      response.endHandler(x -> {
        async.complete();
      });
    }).end();
  }

}
