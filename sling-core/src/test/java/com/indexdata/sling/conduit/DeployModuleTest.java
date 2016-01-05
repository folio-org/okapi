/*
 * Copyright (c) 2015-2016, Index Data
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
  private String locationSample3;
  private String locationAuth;
  private String slingToken;
  private final String slingTenant = "roskilde";
  private long startTime;
  private int repeatPostRunning;
  private HttpClient httpClient;
  
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
    httpClient = vertx.createHttpClient();
  }
  
  @After
  public void tearDown(TestContext context) {
    final Async async = context.async();
    td(context, async);
  }

  public void td(TestContext context, Async async) {
    if (locationAuth != null) {
      System.out.println("tearDown " + locationAuth);
      httpClient.delete(port, "localhost", locationAuth, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationAuth = null;
          td(context, async);
        });
      }).end();
      return;
    }
    if (locationSample != null) {
      System.out.println("tearDown " + locationSample);
      httpClient.delete(port, "localhost", locationSample, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationSample = null;
          td(context, async);
        });
      }).end();
      return;
    }
    if (locationSample2 != null) {
      System.out.println("tearDown " + locationSample2);
      httpClient.delete(port, "localhost", locationSample2, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationSample2 = null;
          td(context, async);
        });
      }).end();
      return;
    }
    if (locationSample3 != null) {
      System.out.println("tearDown " + locationSample3);
      httpClient.delete(port, "localhost", locationSample3, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationSample3 = null;
          td(context, async);
        });
      }).end();
      return;
    }
    System.out.println("About to close");
    vertx.close(x -> {
      async.complete();
    });
  }    

  private int port = Integer.parseInt(System.getProperty("port", "9130"));
            
  @Test(timeout = 600000)
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
            + "    \"level\" : \"10\",\n"
            + "    \"type\" : \"request-response\"\n"
            + "  }, {"
            + "    \"methods\" : [ \"POST\" ],\n"
            + "    \"path\" : \"/login\",\n"
            + "    \"level\" : \"20\",\n"
            + "    \"type\" : \"request-response\"\n"
            + "  } ]\n"
            + "}";
    httpClient.post(port, "localhost", "/_/modules", response -> {
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
            + "    \"level\" : \"30\",\n"
            + "    \"type\" : \"request-response\"\n"
            + "  } ]\n"
            + "}";
    httpClient.post(port, "localhost", "/_/modules", response -> {
      context.assertEquals(201, response.statusCode());
      locationSample =  response.getHeader("Location");
      response.endHandler(x -> {
        getIt(context, async, doc);
      });
    }).end(doc);
  }
  
  public void getIt(TestContext context, Async async, String doc) {
    System.out.println("getIt");
    httpClient.get(port, "localhost", locationSample, response -> {
      response.handler(body -> {
        context.assertEquals(doc, body.toString());
      });
      response.endHandler(x -> {
        createTenant(context, async);
      });
    }).end();
  }

  public void createTenant(TestContext context, Async async) {
    final String doc = "{\n"
            + "  \"name\" : \"" + slingTenant + "\",\n"
            + "  \"description\" : \"Roskilde bibliotek\"\n"
            + "}";
    httpClient.post(port, "localhost", "/_/tenants", response -> {
      context.assertEquals(201, response.statusCode());
      locationTenant = response.getHeader("Location");
      response.endHandler(x -> {
        tenantEnableModuleAuth(context, async);
      });
    }).end(doc);
  }
 
  public void tenantEnableModuleAuth(TestContext context, Async async) {
    final String doc = "{\n"
            + "  \"module\" : \"auth\"\n"
            + "}";
    httpClient.post(port, "localhost", "/_/tenants/" + slingTenant + "/modules", response -> {
      context.assertEquals(200, response.statusCode());
      response.endHandler(x -> {
        tenantEnableModuleSample(context, async);
      });
    }).end(doc);
  }

  public void tenantEnableModuleSample(TestContext context, Async async) {
    final String doc = "{\n"
            + "  \"module\" : \"sample-module\"\n"
            + "}";
    httpClient.post(port, "localhost", "/_/tenants/" + slingTenant + "/modules", response -> {
      context.assertEquals(200, response.statusCode());
      response.endHandler(x -> {
        useWithoutTenant(context, async);
      });
    }).end(doc);
  }

  public void useWithoutTenant(TestContext context, Async async) {
    System.out.println("useWithoutTenant");
    HttpClientRequest req = httpClient.get(port, "localhost", "/sample", response -> {
      context.assertEquals(403, response.statusCode());
      String trace = response.getHeader("X-Sling-Trace");
      context.assertTrue(trace == null);
      response.endHandler(x -> {
        useWithoutLogin(context, async);
      });
    });
    req.end();
  }
  
  public void useWithoutLogin(TestContext context, Async async) {
    System.out.println("useWithoutLogin");
    HttpClientRequest req = httpClient.get(port, "localhost", "/sample", response -> {
      context.assertEquals(401, response.statusCode());
      String trace = response.getHeader("X-Sling-Trace");
      context.assertTrue(trace != null && trace.matches(".*GET auth:401.*"));
      response.endHandler(x -> {
        failLogin(context, async);
      });
    });
    req.putHeader("X-Sling-Tenant", slingTenant);
    req.end();
  }

  public void failLogin(TestContext context, Async async) {
    System.out.println("failLogin");
    String doc = "{\n"
            + "  \"tenant\" : \"t1\",\n"
            + "  \"username\" : \"peter\",\n"
            + "  \"password\" : \"peter37\"\n"
            + "}";
    HttpClientRequest req = httpClient.post(port, "localhost", "/login", response -> {
      context.assertEquals(401, response.statusCode());
      response.endHandler(x -> {
         doLogin(context, async);
      });
    });
    req.putHeader("X-Sling-Tenant", slingTenant);
    req.end(doc);
  }

  public void doLogin(TestContext context, Async async) {
    System.out.println("doLogin");
    String doc = "{\n"
            + "  \"tenant\" : \"t1\",\n"
            + "  \"username\" : \"peter\",\n"
            + "  \"password\" : \"peter36\"\n"
            + "}";
    HttpClientRequest req = httpClient.post(port, "localhost", "/login", response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      context.assertTrue(headers != null && headers.matches(".*X-Sling-Trace=POST auth:200.*"));
      slingToken = response.getHeader("X-Sling-Token");
      System.out.println("token=" + slingToken);
      response.endHandler(x -> {
         useItWithGet(context, async);
      });
    });
    req.putHeader("X-Sling-Tenant", slingTenant);
    req.end(doc);
  }

  public void useItWithGet(TestContext context, Async async) {
    System.out.println("useItWithGet");
    HttpClientRequest req = httpClient.get(port, "localhost", "/sample", response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      System.out.println("useWithGet headers " + headers);
      context.assertTrue(headers != null && headers.matches(".*X-Sling-Trace=GET sample-module:200.*"));
      response.handler(x -> {
        context.assertEquals("It works", x.toString());
      });
      response.endHandler(x -> {
        useItWithPost(context, async);
      });
    });
    req.headers().add("X-Sling-Token", slingToken);
    req.putHeader("X-Sling-Tenant", slingTenant);
    req.end();
  }

  public void useItWithPost(TestContext context, Async async) {
    System.out.println("useItWithPost");
    Buffer body = Buffer.buffer();
    HttpClientRequest req = httpClient.post(port, "localhost", "/sample", response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      context.assertTrue(headers != null && headers.matches(".*X-Sling-Trace=POST sample-module:200.*"));
      response.handler(x -> {
        body.appendBuffer(x);
      });
      response.endHandler(x -> {
        context.assertEquals("Hello Sling", body.toString());
        useNoPath(context, async);
      });
    });
    req.headers().add("X-Sling-Token", slingToken);
    req.putHeader("X-Sling-Tenant", slingTenant);
    req.end("Sling");
  }

  public void useNoPath(TestContext context, Async async) {
    System.out.println("useNoPath");
    HttpClientRequest req = httpClient.get(port, "localhost", "/samplE", response -> {
      context.assertEquals(202, response.statusCode());
      response.endHandler(x -> {
        useNoMethod(context, async);
      });
    });
    req.headers().add("X-Sling-Token", slingToken);
    req.putHeader("X-Sling-Tenant", slingTenant);
    req.end();
  }

  public void useNoMethod(TestContext context, Async async) {
    System.out.println("useNoMethod");
    HttpClientRequest req  = httpClient.delete(port, "localhost", "/sample", response -> {
      context.assertEquals(202, response.statusCode());
      response.endHandler(x -> {
        deploySample2(context, async);
      });
    });
    req.headers().add("X-Sling-Token", slingToken);
    req.putHeader("X-Sling-Tenant", slingTenant);
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
            + "    \"level\" : \"31\",\n"
            + "    \"type\" : \"request-response\"\n"
            + "  } ]\n"
            + "}";
    httpClient.post(port, "localhost", "/_/modules", response -> {
      context.assertEquals(201, response.statusCode());
      locationSample2 =  response.getHeader("Location");
      response.endHandler(x -> {
        tenantEnableModuleSample2(context, async);
      });
    }).end(doc);
  }
  
  public void tenantEnableModuleSample2(TestContext context, Async async) {
    final String doc = "{\n"
            + "  \"module\" : \"sample-module2\"\n"
            + "}";
    httpClient.post(port, "localhost", "/_/tenants/" + slingTenant + "/modules", response -> {
      context.assertEquals(200, response.statusCode());
      response.endHandler(x -> {
        deploySample3(context, async);
      });
    }).end(doc);
  }

    public void deploySample3(TestContext context, Async async) {
    System.out.println("deploySample3");
    final String doc = "{\n"
            + "  \"name\" : \"sample-module3\",\n"
            + "  \"descriptor\" : {\n"
            + "    \"cmdlineStart\" : "
            + "\"java -Dport=%p -jar ../sling-sample-module/target/sling-sample-module-fat.jar\",\n"
            + "    \"cmdlineStop\" : null\n"
            + "  },\n"
            + "  \"routingEntries\" : [ {\n"
            + "    \"methods\" : [ \"GET\", \"POST\" ],\n"
            + "    \"path\" : \"/sample\",\n"
            + "    \"level\" : \"33\",\n"
            + "    \"type\" : \"request-only\"\n"
            + "  } ]\n"
            + "}";
    httpClient.post(port, "localhost", "/_/modules", response -> {
      context.assertEquals(201, response.statusCode());
      locationSample3 =  response.getHeader("Location");
      response.endHandler(x -> {
        tenantEnableModuleSample3(context, async);
      });
    }).end(doc);
  }
  
  public void tenantEnableModuleSample3(TestContext context, Async async) {
    final String doc = "{\n"
            + "  \"module\" : \"sample-module3\"\n"
            + "}";
    httpClient.post(port, "localhost", "/_/tenants/" + slingTenant + "/modules", response -> {
      context.assertEquals(200, response.statusCode());
      response.endHandler(x -> {
        useItWithGet2(context, async);
      });
    }).end(doc);
  }


  public void useItWithGet2(TestContext context, Async async) {
    System.out.println("useItWithGet2");
    HttpClientRequest req = httpClient.get(port, "localhost", "/sample", response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      System.out.println("useWithGet2 headers " + headers);
      context.assertTrue(headers != null && headers.matches(".*X-Sling-Trace=GET sample-module2:200.*"));
      response.handler(x -> {
        context.assertEquals("It works", x.toString());
      });
      response.endHandler(x -> {
        repeatPostRunning = 0;
        // 1k is enough for regular testing, but the performance improves up to 50k
        final int iterations = 1000;
        //final int iterations = 50000;
        final int parallels = 10;
        for (int i = 0; i < parallels; i++) {
          repeatPost(context, async, 0, iterations, parallels);
        }
      });
    });
    req.headers().add("X-Sling-Token", slingToken);
    req.putHeader("X-Sling-Tenant", slingTenant);
    req.end();
  }

  public void repeatPost(TestContext context, Async async, 
            int cnt, int max, int parallels) {
    final String msg = "Sling" + cnt;
    if (cnt == max) {
      if (--repeatPostRunning == 0) {
        long timeDiff = (System.nanoTime() - startTime) / 1000000;
        System.out.println("repeatPost " + timeDiff + " elapsed ms. " + 1000 * max * parallels / timeDiff + " req/sec");
        vertx.setTimer(1, x -> useItWithGet3(context, async));
      }
      return;
    } else if (cnt == 0) {
      if (repeatPostRunning == 0) {
        startTime = System.nanoTime();
      }
      repeatPostRunning++;
      System.out.println("repeatPost " + max + " iterations");
    }
    Buffer body = Buffer.buffer();
    HttpClientRequest req = httpClient.post(port, "localhost", "/sample", response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      context.assertTrue(headers.matches(".*X-Sling-Trace=POST sample-module2:200.*"));
      response.handler(x -> {
        body.appendBuffer(x);
      });
      response.endHandler(x -> {
        context.assertEquals("Hello Hello " + msg, body.toString());
        repeatPost(context, async, cnt + 1, max, parallels);
      });
      response.exceptionHandler(e -> { 
        context.fail(e);
      });
    });
    req.headers().add("X-Sling-Token", slingToken);
    req.putHeader("X-Sling-Tenant", slingTenant);
    req.end(msg);
  }

  // Repeat the Get test, to see timing headers of a system that has been warmed up
  public void useItWithGet3(TestContext context, Async async) {
    System.out.println("useItWithGet3");
    HttpClientRequest req = httpClient.get(port, "localhost", "/sample", response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      System.out.println("useWithGet3 headers " + headers);
      // context.assertTrue(headers.matches(".*X-Sling-Trace=GET auth:202.*")); 
      context.assertTrue(headers.matches(".*X-Sling-Trace=GET sample-module2:200.*"));
      response.handler(x -> {
        context.assertEquals("It works", x.toString());
      });
      response.endHandler(x -> {
        deleteTenant(context, async);
      });
    });
    req.headers().add("X-Sling-Token", slingToken);
    req.putHeader("X-Sling-Tenant", slingTenant);
    req.end();
  }

  
  
  public void deleteTenant(TestContext context, Async async) {
    httpClient.delete(port, "localhost", locationTenant, response -> {
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
