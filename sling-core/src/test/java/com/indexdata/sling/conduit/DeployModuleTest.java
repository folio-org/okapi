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
  
  private String locationTenant;
  private String locationSample;
  private String locationSample2;
  private String locationAuth;
  private String slingToken;
  
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
  
  private int port = Integer.parseInt(System.getProperty("port", "9130"));
            
  @Test
  public void test_sample(TestContext context)
  {
     final Async async = context.async();
     deployAuth(context, async);
  }

  public void deployAuth(TestContext context, Async async) {
    System.out.println("deployAuth");
    final String doc = "{\n"
            + "  \"name\" : \"auth\",\n"
            + "  \"descriptor\" : {\n"
            + "    \"cmdlineStart\" : "
            + "\"java -Dport=%p -jar ../auth/target/auth-fat.jar\",\n"
            + "    \"cmdlineStop\" : null\n"
            + "  },\n"
            + "  \"routingEntries\" : [ {\n"
            + "    \"methods\" : [ \"*\" ],\n"
            + "    \"path\" : \"/\",\n"
            + "    \"level\" : \"10\"\n"
            + "  }, {"
            + "    \"methods\" : [ \"POST\" ],\n"
            + "    \"path\" : \"/login\",\n"
            + "    \"level\" : \"20\"\n"
            + "  } ]\n"
            + "}";
    HttpClient c = vertx.createHttpClient();
    c.post(port, "localhost", "/_/modules", response -> {
      context.assertEquals(201, response.statusCode());
      locationAuth = response.getHeader("Location");
      response.endHandler(x -> {
        deploySample(context, async);
      });
    }).end(doc);
  }

  public void deploySample(TestContext context, Async async) {
    System.out.println("deploySample");
    final String doc = "{\n"
            + "  \"name\" : \"sample-module\",\n"
            + "  \"descriptor\" : {\n"
            + "    \"cmdlineStart\" : "
            + "\"java -Dport=%p -jar ../sling-sample-module/target/sling-sample-module-fat.jar\",\n"
            + "    \"cmdlineStop\" : null\n"
            + "  },\n"
            + "  \"routingEntries\" : [ {\n"
            + "    \"methods\" : [ \"GET\", \"POST\" ],\n"
            + "    \"path\" : \"/sample\",\n"
            + "    \"level\" : \"30\"\n"
            + "  } ]\n"
            + "}";
    HttpClient c = vertx.createHttpClient();
    c.post(port, "localhost", "/_/modules", response -> {
      context.assertEquals(201, response.statusCode());
      locationSample =  response.getHeader("Location");
      response.endHandler(x -> {
        getIt(context, async, doc);
      });
    }).end(doc);
  }
  
  public void getIt(TestContext context, Async async, String doc) {
    System.out.println("getIt");
    HttpClient c = vertx.createHttpClient();
    c.get(port, "localhost", locationSample, response -> {
      response.handler(body -> {
        context.assertEquals(doc, body.toString());
      });
      response.endHandler(x -> {
        // Need a small delay before the modules are actually listening.
        // On a workstation 300 seems to be sufficient, but on my laptop
        // I seem to need 1000ms.
        vertx.setTimer(1000, id -> {  
          createTenant(context, async);
        });
      });
    }).end();
  }

  public void createTenant(TestContext context, Async async) {
    final String doc = "{\n"
            + "  \"name\" : \"roskilde\",\n"
            + "  \"description\" : \"Roskilde bibliotek\"\n"
            + "}";
    HttpClient c = vertx.createHttpClient();
    c.post(port, "localhost", "/_/tenants", response -> {
      context.assertEquals(201, response.statusCode());
      locationTenant = response.getHeader("Location");
      response.endHandler(x -> {
        tenantEnableModule(context, async);
      });
    }).end(doc);
  }
 
  public void tenantEnableModule(TestContext context, Async async) {
    final String doc = "{\n"
            + "  \"module\" : \"" + locationAuth + "\"\n"
            + "}";
    HttpClient c = vertx.createHttpClient();
    c.post(port, "localhost", "/_/tenants/roskilde/modules", response -> {
      context.assertEquals(200, response.statusCode());
      response.endHandler(x -> {
        useWithoutLogin(context, async);
      });
    }).end(doc);
  }
  
  public void useWithoutLogin(TestContext context, Async async) {
    System.out.println("useWithoutLogin");
    HttpClient c = vertx.createHttpClient();
    c.get(port, "localhost", "/sample", response -> {
      context.assertEquals(401, response.statusCode());
      String trace = response.getHeader("X-Sling-Trace");
      context.assertTrue(trace.matches(".*GET auth:401.*"));
      response.endHandler(x -> {
         failLogin(context, async);
      });
    }).end();
  }

  public void failLogin(TestContext context, Async async) {
    System.out.println("failLogin");
    HttpClient c = vertx.createHttpClient();
    String doc = "{\n"
            + "  \"tenant\" : \"t1\",\n"
            + "  \"username\" : \"peter\",\n"
            + "  \"password\" : \"peter37\"\n"
            + "}";
    c.post(port, "localhost", "/login", response -> {
      context.assertEquals(401, response.statusCode());
      response.endHandler(x -> {
         doLogin(context, async);
      });
    }).end(doc);
  }

  public void doLogin(TestContext context, Async async) {
    System.out.println("doLogin");
    HttpClient c = vertx.createHttpClient();
    String doc = "{\n"
            + "  \"tenant\" : \"t1\",\n"
            + "  \"username\" : \"peter\",\n"
            + "  \"password\" : \"peter36\"\n"
            + "}";
    c.post(port, "localhost", "/login", response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      //System.out.println("doLogin Headers: '" + headers );
      // There must be an easier way to check two headers! (excl timing)
      context.assertTrue(headers.matches(".*X-Sling-Trace=POST auth:200.*"));
      slingToken = response.getHeader("X-Sling-Token");
      System.out.println("token=" + slingToken);
      response.endHandler(x -> {
         useItWithGet(context, async);
      });
    }).end(doc);
  }

  public void useItWithGet(TestContext context, Async async) {
    System.out.println("useItWithGet");
    HttpClient c = vertx.createHttpClient();
    HttpClientRequest req = c.get(port, "localhost", "/sample", response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      System.out.println("useWithGet headers " + headers);
      // context.assertTrue(headers.matches(".*X-Sling-Trace=GET auth:202.*")); 
      context.assertTrue(headers.matches(".*X-Sling-Trace=GET sample-module:200.*"));
      response.handler(x -> {
        context.assertEquals("It works", x.toString());
      });
      response.endHandler(x -> {
        useItWithPost(context, async);
      });
    });
    req.headers().add("X-Sling-Token", slingToken);
    req.end();
  }

  public void useItWithPost(TestContext context, Async async) {
    System.out.println("useItWithPost");
    HttpClient c = vertx.createHttpClient();
    Buffer body = Buffer.buffer();
    HttpClientRequest req = c.post(port, "localhost", "/sample", response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      System.out.println("useWithPost headers " + headers);
      context.assertTrue(headers.matches(".*X-Sling-Trace=POST sample-module:200.*"));
      
      response.handler(x -> {
        body.appendBuffer(x);
      });
      response.endHandler(x -> {
        context.assertEquals("Hello Sling", body.toString());
        useNoPath(context, async);
      });
    });
    req.headers().add("X-Sling-Token", slingToken);
    req.end("Sling");
  }

  public void useNoPath(TestContext context, Async async) {
    System.out.println("useNoPath");
    HttpClient c = vertx.createHttpClient();
    HttpClientRequest req = c.get(port, "localhost", "/samplE", response -> {
      context.assertEquals(202, response.statusCode());
      response.endHandler(x -> {
        useNoMethod(context, async);
      });
    });
    req.headers().add("X-Sling-Token", slingToken);
    req.end();
  }

  public void useNoMethod(TestContext context, Async async) {
    System.out.println("useNoMethod");
    HttpClient c = vertx.createHttpClient();
    HttpClientRequest req  = c.delete(port, "localhost", "/sample", response -> {
      context.assertEquals(202, response.statusCode());
      response.endHandler(x -> {
        deploySample2(context, async);
      });
    });
    req.headers().add("X-Sling-Token", slingToken);
    req.end();
  }

  public void deploySample2(TestContext context, Async async) {
    System.out.println("deploySample2");
    final String doc = "{\n"
            + "  \"name\" : \"sample-module2\",\n"
            + "  \"descriptor\" : {\n"
            + "    \"cmdlineStart\" : "
            + "\"java -Dport=%p -jar ../sling-sample-module/target/sling-sample-module-fat.jar\",\n"
            + "    \"cmdlineStop\" : null\n"
            + "  },\n"
            + "  \"routingEntries\" : [ {\n"
            + "    \"methods\" : [ \"GET\", \"POST\" ],\n"
            + "    \"path\" : \"/sample\",\n"
            + "    \"level\" : \"31\"\n"
            + "  } ]\n"
            + "}";
    HttpClient c = vertx.createHttpClient();
    c.post(port, "localhost", "/_/modules", response -> {
      context.assertEquals(201, response.statusCode());
      locationSample2 =  response.getHeader("Location");
      response.endHandler(x -> {
        vertx.setTimer(1000, id -> {  
          useItWithGet2(context, async);
        });
      });
    }).end(doc);
  }
  
  public void useItWithGet2(TestContext context, Async async) {
    System.out.println("useItWithGet2");
    HttpClient c = vertx.createHttpClient();
    HttpClientRequest req = c.get(port, "localhost", "/sample", response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      System.out.println("useWithGet headers " + headers);
      // context.assertTrue(headers.matches(".*X-Sling-Trace=GET auth:202.*")); 
      context.assertTrue(headers.matches(".*X-Sling-Trace=GET sample-module2:200.*"));
      response.handler(x -> {
        context.assertEquals("It works", x.toString());
      });
      response.endHandler(x -> {
        useItWithPost2(context, async);
      });
    });
    req.headers().add("X-Sling-Token", slingToken);
    req.end();
  }

  public void useItWithPost2(TestContext context, Async async) {
    System.out.println("useItWithPost2");
    HttpClient c = vertx.createHttpClient();
    Buffer body = Buffer.buffer();
    HttpClientRequest req = c.post(port, "localhost", "/sample", response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      System.out.println("useWithPost headers " + headers);
      context.assertTrue(headers.matches(".*X-Sling-Trace=POST sample-module2:200.*"));
      
      response.handler(x -> {
        body.appendBuffer(x);
      });
      response.endHandler(x -> {
        context.assertEquals("Hello Hello Sling", body.toString());
        deleteSample2(context, async);
      });
    });
    req.headers().add("X-Sling-Token", slingToken);
    req.end("Sling");
  }

  public void deleteSample2(TestContext context, Async async) {
    System.out.println("deleteSample2");
    HttpClient c = vertx.createHttpClient();
    c.delete(port, "localhost", locationSample2, response -> {
      context.assertEquals(204, response.statusCode());
      response.endHandler(x -> {
        deleteSample(context, async);
      });
    }).end();
  }
  
  public void deleteSample(TestContext context, Async async) {
    System.out.println("deleteSample");
    HttpClient c = vertx.createHttpClient();
    c.delete(port, "localhost", locationSample, response -> {
      context.assertEquals(204, response.statusCode());
      response.endHandler(x -> {
        deleteAuth(context, async);
      });
    }).end();
  }
  
  
  public void deleteAuth(TestContext context, Async async) {
    System.out.println("deleteAuth");
    HttpClient c = vertx.createHttpClient();
    c.delete(port, "localhost", locationAuth, response -> {
      context.assertEquals(204, response.statusCode());
      response.endHandler(x -> {
        deleteTenant(context, async);
      });
    }).end();
  }
  
   public void deleteTenant(TestContext context, Async async) {
    HttpClient c = vertx.createHttpClient();
    c.delete(port, "localhost", locationTenant, response -> {
      context.assertEquals(204, response.statusCode());
      response.endHandler(x -> {
        done(context, async);
      });
    }).end();
  }
  
  public void done(TestContext context, Async async) {
    System.out.println("done");
    async.complete();
  }
}
