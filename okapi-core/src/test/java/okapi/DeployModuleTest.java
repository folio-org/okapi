/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi;

import okapi.MainVerticle;
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
  private String okapiToken;
  private final String okapiTenant = "roskilde";
  private long startTime;
  private int repeatPostRunning;
  private HttpClient httpClient;
  private static String LS = System.lineSeparator();

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
  public void test_sample(TestContext context) {
    final Async async = context.async();
    postUnknownService(context, async);
  }

  public void postUnknownService(TestContext context, Async async) {
    System.out.println("useUnknownService");
    final String doc = "{ }";
    httpClient.post(port, "localhost", "/_/xyz", response -> {
      context.assertEquals(404, response.statusCode());
      response.endHandler(x -> {
        postBadJSON(context, async);
      });
    }).end(doc);
  }

  public void postBadJSON(TestContext context, Async async) {
    System.out.println("deployAuth");
    final String bad_doc = "{"+LS
            + "  \"name\" : \"auth\","+LS
            + "}";
    httpClient.post(port, "localhost", "/_/modules", response -> {
      context.assertEquals(400, response.statusCode());
      response.endHandler(x -> {
        deployBadModule(context, async);
      });
    }).end(bad_doc);
  }

  public void deployBadModule(TestContext context, Async async) {
    System.out.println("deployAuth");
    final String doc = "{"+LS
            + "  \"name\" : \"auth\","+LS
            + "  \"descriptor\" : {"+LS
            + "    \"cmdlineStart\" : "
            + "\"java -Dport=%p -jar ../okapi-auth/target/okapi-unknown.jar\","+LS
            // + "\"sleep %p\","+LS
            + "    \"cmdlineStop\" : null"+LS
            + "  },"+LS
            + "  \"routingEntries\" : [ {"+LS
            + "    \"methods\" : [ \"*\" ],"+LS
            + "    \"path\" : \"/\","+LS
            + "    \"level\" : \"10\","+LS
            + "    \"type\" : \"request-response\""+LS
            + "  } ]"+LS
            + "}";
    httpClient.post(port, "localhost", "/_/modules", response -> {
      context.assertEquals(500, response.statusCode());
      response.endHandler(x -> {
        deployAuth(context, async);
      });
    }).end(doc);
  }

  public void deployAuth(TestContext context, Async async) {
    System.out.println("deployAuth");
    final String doc = "{"+LS
            + "  \"name\" : \"auth\","+LS
            + "  \"descriptor\" : {"+LS
            + "    \"cmdlineStart\" : "
            + "\"java -Dport=%p -jar ../okapi-auth/target/okapi-auth-fat.jar\","+LS
            + "    \"cmdlineStop\" : null"+LS
            + "  },"+LS
            + "  \"routingEntries\" : [ {"+LS
            + "    \"methods\" : [ \"*\" ],"+LS
            + "    \"path\" : \"/\","+LS
            + "    \"level\" : \"10\","+LS
            + "    \"type\" : \"request-response\""+LS
            + "  }, {"
            + "    \"methods\" : [ \"POST\" ],"+LS
            + "    \"path\" : \"/login\","+LS
            + "    \"level\" : \"20\","+LS
            + "    \"type\" : \"request-response\""+LS
            + "  } ]"+LS
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
    final String doc = "{"+LS
            + "  \"name\" : \"sample-module\","+LS
            + "  \"descriptor\" : {"+LS
            + "    \"cmdlineStart\" : "
            + "\"java -Dport=%p -jar ../okapi-sample-module/target/okapi-sample-module-fat.jar\","+LS
            + "    \"cmdlineStop\" : null"+LS
            + "  },"+LS
            + "  \"routingEntries\" : [ {"+LS
            + "    \"methods\" : [ \"GET\", \"POST\" ],"+LS
            + "    \"path\" : \"/sample\","+LS
            + "    \"level\" : \"30\","+LS
            + "    \"type\" : \"request-response\""+LS
            + "  } ]"+LS
            + "}";
    httpClient.post(port, "localhost", "/_/modules", response -> {
      context.assertEquals(201, response.statusCode());
      locationSample = response.getHeader("Location");
      response.endHandler(x -> {
        listModules(context, async, doc);
      });
    }).end(doc);
  }

  public void listModules(TestContext context, Async async, String doc) {
    System.out.println("listModules start");
    httpClient.get(port, "localhost", "/_/modules/", response -> {
      System.out.println("listModules response");
      response.handler(body -> {
        System.out.println("listModules body" + body.toString());
        context.assertEquals(200, response.statusCode());
        context.assertEquals("[ \"auth\", \"sample-module\" ]", body.toString());
      });
      response.endHandler(x -> {
        getIt(context, async, doc);
      });
    }).end();

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
    final String doc = "{"+LS
            + "  \"name\" : \"" + okapiTenant + "\","+LS
            + "  \"description\" : \"Roskilde bibliotek\""+LS
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
    final String doc = "{"+LS
            + "  \"module\" : \"auth\""+LS
            + "}";
    httpClient.post(port, "localhost", "/_/tenants/" + okapiTenant + "/modules", response -> {
      context.assertEquals(200, response.statusCode());
      response.endHandler(x -> {
        tenantListModules1(context, async);
      });
    }).end(doc);
  }

  public void tenantListModules1(TestContext context, Async async) {
    httpClient.get(port, "localhost", "/_/tenants/" + okapiTenant + "/modules", response -> {
      context.assertEquals(200, response.statusCode());
      response.handler(x -> {
        context.assertEquals("[ \"auth\" ]", x.toString());
      });
      response.endHandler(x -> {
        tenantEnableModuleSample(context, async);
      });
    }).end();
  }

  public void tenantEnableModuleSample(TestContext context, Async async) {
    final String doc = "{"+LS
            + "  \"module\" : \"sample-module\""+LS
            + "}";
    httpClient.post(port, "localhost", "/_/tenants/" + okapiTenant + "/modules", response -> {
      context.assertEquals(200, response.statusCode());
      response.endHandler(x -> {
        tenantListModules2(context, async);
      });
    }).end(doc);
  }

  public void tenantListModules2(TestContext context, Async async) {
    httpClient.get(port, "localhost", "/_/tenants/" + okapiTenant + "/modules", response -> {
      context.assertEquals(200, response.statusCode());
      response.handler(x -> {
        context.assertEquals("[ \"auth\", \"sample-module\" ]", x.toString());
      });
      response.endHandler(x -> {
        useWithoutTenant(context, async);
      });
    }).end();
  }


  public void useWithoutTenant(TestContext context, Async async) {
    System.out.println("useWithoutTenant");
    HttpClientRequest req = httpClient.get(port, "localhost", "/sample", response -> {
      context.assertEquals(403, response.statusCode());
      String trace = response.getHeader("X-Okapi-Trace");
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
      String trace = response.getHeader("X-Okapi-Trace");
      context.assertTrue(trace != null && trace.matches(".*GET auth:401.*"));
      response.endHandler(x -> {
        failLogin(context, async);
      });
    });
    req.putHeader("X-Okapi-Tenant", okapiTenant);
    req.end();
  }

  public void failLogin(TestContext context, Async async) {
    System.out.println("failLogin");
    String doc = "{"+LS
            + "  \"tenant\" : \"t1\","+LS
            + "  \"username\" : \"peter\","+LS
            + "  \"password\" : \"peter37\""+LS
            + "}";
    HttpClientRequest req = httpClient.post(port, "localhost", "/login", response -> {
      context.assertEquals(401, response.statusCode());
      response.endHandler(x -> {
        doLogin(context, async);
      });
    });
    req.putHeader("X-Okapi-Tenant", okapiTenant);
    req.end(doc);
  }

  public void doLogin(TestContext context, Async async) {
    System.out.println("doLogin");
    String doc = "{"+LS
            + "  \"tenant\" : \"t1\","+LS
            + "  \"username\" : \"peter\","+LS
            + "  \"password\" : \"peter36\""+LS
            + "}";
    HttpClientRequest req = httpClient.post(port, "localhost", "/login", response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      context.assertTrue(headers != null && headers.matches(".*X-Okapi-Trace=POST auth:200.*"));
      okapiToken = response.getHeader("X-Okapi-Token");
      System.out.println("token=" + okapiToken);
      response.endHandler(x -> {
        useItWithGet(context, async);
      });
    });
    req.putHeader("X-Okapi-Tenant", okapiTenant);
    req.end(doc);
  }

  public void useItWithGet(TestContext context, Async async) {
    System.out.println("useItWithGet");
    HttpClientRequest req = httpClient.get(port, "localhost", "/sample", response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      System.out.println("useWithGet headers " + headers);
      context.assertTrue(headers != null && headers.matches(".*X-Okapi-Trace=GET sample-module:200.*"));
      response.handler(x -> {
        context.assertEquals("It works", x.toString());
      });
      response.endHandler(x -> {
        useItWithPost(context, async);
      });
    });
    req.headers().add("X-Okapi-Token", okapiToken);
    req.putHeader("X-Okapi-Tenant", okapiTenant);
    req.end();
  }

  public void useItWithPost(TestContext context, Async async) {
    System.out.println("useItWithPost");
    Buffer body = Buffer.buffer();
    HttpClientRequest req = httpClient.post(port, "localhost", "/sample", response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      context.assertTrue(headers != null && headers.matches(".*X-Okapi-Trace=POST sample-module:200.*"));
      response.handler(x -> {
        body.appendBuffer(x);
      });
      response.endHandler(x -> {
        context.assertEquals("Hello Okapi", body.toString());
        useNoPath(context, async);
      });
    });
    req.headers().add("X-Okapi-Token", okapiToken);
    req.putHeader("X-Okapi-Tenant", okapiTenant);
    req.end("Okapi");
  }

  public void useNoPath(TestContext context, Async async) {
    System.out.println("useNoPath");
    HttpClientRequest req = httpClient.get(port, "localhost", "/samplE", response -> {
      context.assertEquals(202, response.statusCode());
      response.endHandler(x -> {
        useNoMethod(context, async);
      });
    });
    req.headers().add("X-Okapi-Token", okapiToken);
    req.putHeader("X-Okapi-Tenant", okapiTenant);
    req.end();
  }

  public void useNoMethod(TestContext context, Async async) {
    System.out.println("useNoMethod");
    HttpClientRequest req = httpClient.delete(port, "localhost", "/sample", response -> {
      context.assertEquals(202, response.statusCode());
      response.endHandler(x -> {
        deploySample2(context, async);
      });
    });
    req.headers().add("X-Okapi-Token", okapiToken);
    req.putHeader("X-Okapi-Tenant", okapiTenant);
    req.end();
  }

  public void deploySample2(TestContext context, Async async) {
    System.out.println("deploySample2");
    final String doc = "{"+LS
            + "  \"name\" : \"sample-module2\","+LS
            + "  \"descriptor\" : {"+LS
            + "    \"cmdlineStart\" : "
            + "\"java -Dport=%p -jar ../okapi-sample-module/target/okapi-sample-module-fat.jar\","+LS
            + "    \"cmdlineStop\" : null"+LS
            + "  },"+LS
            + "  \"routingEntries\" : [ {"+LS
            + "    \"methods\" : [ \"GET\", \"POST\" ],"+LS
            + "    \"path\" : \"/sample\","+LS
            + "    \"level\" : \"31\","+LS
            + "    \"type\" : \"request-response\""+LS
            + "  } ]"+LS
            + "}";
    httpClient.post(port, "localhost", "/_/modules", response -> {
      context.assertEquals(201, response.statusCode());
      locationSample2 = response.getHeader("Location");
      response.endHandler(x -> {
        tenantEnableModuleSample2(context, async);
      });
    }).end(doc);
  }

  public void tenantEnableModuleSample2(TestContext context, Async async) {
    final String doc = "{"+LS
            + "  \"module\" : \"sample-module2\""+LS
            + "}";
    httpClient.post(port, "localhost", "/_/tenants/" + okapiTenant + "/modules", response -> {
      context.assertEquals(200, response.statusCode());
      response.endHandler(x -> {
        deploySample3(context, async);
      });
    }).end(doc);
  }

  public void deploySample3(TestContext context, Async async) {
    System.out.println("deploySample3");
    final String doc = "{"+LS
            + "  \"name\" : \"sample-module3\","+LS
            + "  \"descriptor\" : {"+LS
            + "    \"cmdlineStart\" : "
            + "\"java -Dport=%p -jar ../okapi-sample-module/target/okapi-sample-module-fat.jar\","+LS
            + "    \"cmdlineStop\" : null"+LS
            + "  },"+LS
            + "  \"routingEntries\" : [ {"+LS
            + "    \"methods\" : [ \"GET\", \"POST\" ],"+LS
            + "    \"path\" : \"/sample\","+LS
            + "    \"level\" : \"05\","+LS
            + "    \"type\" : \"headers\""+LS
            + "  }, {"+LS
            + "    \"methods\" : [ \"GET\", \"POST\" ],"+LS
            + "    \"path\" : \"/sample\","+LS
            + "    \"level\" : \"45\","+LS
            + "    \"type\" : \"headers\""+LS
            + "  }, {"+LS
            + "    \"methods\" : [ \"GET\", \"POST\" ],"+LS
            + "    \"path\" : \"/sample\","+LS
            + "    \"level\" : \"33\","+LS
            + "    \"type\" : \"request-only\""+LS
            + "  } ]"+LS
            + "}";
    httpClient.post(port, "localhost", "/_/modules", response -> {
      context.assertEquals(201, response.statusCode());
      locationSample3 = response.getHeader("Location");
      response.endHandler(x -> {
        tenantEnableModuleSample3(context, async);
      });
    }).end(doc);
  }

  public void tenantEnableModuleSample3(TestContext context, Async async) {
    final String doc = "{"+LS
            + "  \"module\" : \"sample-module3\""+LS
            + "}";
    httpClient.post(port, "localhost", "/_/tenants/" + okapiTenant + "/modules", response -> {
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
      context.assertTrue(headers != null && headers.matches(".*X-Okapi-Trace=GET sample-module2:200.*"));
      response.handler(x -> {
        context.assertEquals("It works", x.toString());
      });
      response.endHandler(x -> {
        repeatPostInit(context, async);
      });
    });
    req.headers().add("X-Okapi-Token", okapiToken);
    req.putHeader("X-Okapi-Tenant", okapiTenant);
    req.end();
  }

  public void repeatPostInit(TestContext context, Async async) {
    repeatPostRunning = 0;
    // 1k is enough for regular testing, but the performance improves up to 50k
    final int iterations = 1000;
    //final int iterations = 50000;
    final int parallels = 10;
    for (int i = 0; i < parallels; i++) {
      repeatPostRun(context, async, 0, iterations, parallels);
    }
  }

  public void repeatPostRun(TestContext context, Async async,
          int cnt, int max, int parallels) {
    final String msg = "Okapi" + cnt;
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
      context.assertTrue(headers.matches(".*X-Okapi-Trace=POST sample-module2:200.*"));
      response.handler(x -> {
        body.appendBuffer(x);
      });
      response.endHandler(x -> {
        context.assertEquals("Hello Hello " + msg, body.toString());
        repeatPostRun(context, async, cnt + 1, max, parallels);
      });
      response.exceptionHandler(e -> {
        context.fail(e);
      });
    });
    req.headers().add("X-Okapi-Token", okapiToken);
    req.putHeader("X-Okapi-Tenant", okapiTenant);
    req.end(msg);
  }

  // Repeat the Get test, to see timing headers of a system that has been warmed up
  public void useItWithGet3(TestContext context, Async async) {
    System.out.println("useItWithGet3");
    HttpClientRequest req = httpClient.get(port, "localhost", "/sample", response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      System.out.println("useWithGet3 headers " + headers);
      // context.assertTrue(headers.matches(".*X-Okapi-Trace=GET auth:202.*")); 
      context.assertTrue(headers.matches(".*X-Okapi-Trace=GET sample-module2:200.*"));
      response.handler(x -> {
        context.assertEquals("It works", x.toString());
      });
      response.endHandler(x -> {
        deleteTenant(context, async);
      });
    });
    req.headers().add("X-Okapi-Token", okapiToken);
    req.putHeader("X-Okapi-Tenant", okapiTenant);
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
