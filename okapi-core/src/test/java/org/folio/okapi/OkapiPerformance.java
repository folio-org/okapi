package org.folio.okapi;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@java.lang.SuppressWarnings({"squid:S1192"})
@RunWith(VertxUnitRunner.class)
public class OkapiPerformance {

  private final Logger logger = OkapiLogger.get();

  private Vertx vertx;
  private Async async;

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
  private static final String LS = System.lineSeparator();
  private int port = 9230;

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    JsonObject conf = new JsonObject()
      .put("port", Integer.toString(port));

    DeploymentOptions opt = new DeploymentOptions()
            .setConfig(conf);
    vertx.deployVerticle(MainVerticle.class.getName(),
            opt, context.asyncAssertSuccess());
    httpClient = vertx.createHttpClient();
  }

  @After
  public void tearDown(TestContext context) {
    async = context.async();
    td(context);
  }

  public void td(TestContext context) {
    if (locationAuth != null) {
      httpClient.delete(port, "localhost", locationAuth, context.asyncAssertSuccess(response -> {
            context.assertEquals(204, response.statusCode());
            response.endHandler(x -> {
              locationAuth = null;
              td(context);
            });
          }));
      return;
    }
    if (locationSample != null) {
      httpClient.delete(port, "localhost", locationSample, context.asyncAssertSuccess(response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationSample = null;
          td(context);
        });
      }));
      return;
    }
    if (locationSample2 != null) {
      httpClient.delete(port, "localhost", locationSample2, context.asyncAssertSuccess(response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationSample2 = null;
          td(context);
        });
      }));
      return;
    }
    if (locationSample3 != null) {
      httpClient.delete(port, "localhost", locationSample3, context.asyncAssertSuccess(response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationSample3 = null;
          td(context);
        });
      }));
      return;
    }
    vertx.close(x -> {
      async.complete();
    });
  }

  @Test(timeout = 600000)
  public void testSample(TestContext context) {
    async = context.async();
    declareAuth(context);
  }

  public void declareAuth(TestContext context) {
    final String doc = "{" + LS
      + "  \"id\" : \"auth-1.0.0\"," + LS
      + "  \"name\" : \"authmodule\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"login\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/authn/login\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"filters\" : [ {" + LS
      + "    \"methods\" : [ \"*\" ]," + LS
      + "    \"path\" : \"/s\"," + LS
      + "    \"level\" : \"10\"," + LS
      + "    \"type\" : \"request-response\"" + LS
      + "  } ]" + LS
      + "}";
    httpClient.post(port, "localhost", "/_/proxy/modules", Buffer.buffer(doc),
        context.asyncAssertSuccess(response -> {
      logger.debug("declareAuth: " + response.statusCode() + " " + response.statusMessage());
      context.assertEquals(201, response.statusCode());
      response.endHandler(x -> {
        deployAuth(context);
      });
    }));
  }

  public void deployAuth(TestContext context) {
    final String doc = "{" + LS
            + "  \"srvcId\" : \"auth-1.0.0\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"exec\" : "
            + "\"java -Dport=%p -jar ../okapi-test-auth-module/target/okapi-test-auth-module-fat.jar\"" + LS
            + "  }" + LS
            + "}";
    httpClient.post(port, "localhost", "/_/deployment/modules", Buffer.buffer(doc),
        context.asyncAssertSuccess(response -> {
      logger.debug("deployAuth: " + response.statusCode() + " " + response.statusMessage());
      context.assertEquals(201, response.statusCode());
      locationAuth = response.getHeader("Location");
      context.assertNotNull(locationAuth);
      response.endHandler(x -> {
        declareSample(context);
      });
    }));
  }

  public void declareSample(TestContext context) {
    final String doc = "{" + LS
      + "  \"id\" : \"sample-module-1.0.0\"," + LS
      + "  \"name\" : \"sample module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"path\" : \"/testb\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  } ]" + LS
      + "}";

    httpClient.post(port, "localhost", "/_/proxy/modules", Buffer.buffer(doc),
        context.asyncAssertSuccess(response -> {
      context.assertEquals(201, response.statusCode());
      response.handler(body -> {
        context.assertEquals(doc, body.toString());
      });
      response.endHandler(x -> {
        deploySample(context);
      });
    }));
  }

  public void deploySample(TestContext context) {
    final String doc = "{" + LS
            + "  \"srvcId\" : \"sample-module-1.0.0\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"exec\" : "
            + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
            + "  }" + LS
            + "}";
    httpClient.post(port, "localhost", "/_/deployment/modules", Buffer.buffer(doc),
        context.asyncAssertSuccess(response -> {
      context.assertEquals(201, response.statusCode());
      locationSample = response.getHeader("Location");
      Assert.assertNotNull(locationSample);
      response.handler(body -> {
      });
      response.endHandler(x -> {
        createTenant(context);
      });
    }));
  }

  public void createTenant(TestContext context) {
    final String doc = "{" + LS
            + "  \"id\" : \"" + okapiTenant + "\"," + LS
            + "  \"name\" : \"" + okapiTenant + "\"," + LS
            + "  \"description\" : \"Roskilde bibliotek\"" + LS
            + "}";
    httpClient.post(port, "localhost", "/_/proxy/tenants", Buffer.buffer(doc),
        context.asyncAssertSuccess(response -> {
      context.assertEquals(201, response.statusCode());
      locationTenant = response.getHeader("Location");
      response.handler(body -> {
        context.assertEquals(doc, body.toString());
      });
      response.endHandler(x -> {
        tenantEnableModuleAuth(context);
      });
    }));
  }

  public void tenantEnableModuleAuth(TestContext context) {
    final String doc = "{" + LS
            + "  \"id\" : \"auth\"" + LS
            + "}";
    httpClient.post(port, "localhost", "/_/proxy/tenants/" + okapiTenant + "/modules",
        MultiMap.caseInsensitiveMultiMap(), Buffer.buffer(doc), context.asyncAssertSuccess(response -> {
      context.assertEquals(201, response.statusCode());
      response.endHandler(x -> tenantEnableModuleSample(context));
    }));
  }

  public void tenantEnableModuleSample(TestContext context) {
    final String doc = "{" + LS
            + "  \"id\" : \"sample-module\"" + LS
            + "}";
    httpClient.post(port, "localhost", "/_/proxy/tenants/" + okapiTenant + "/modules",
        MultiMap.caseInsensitiveMultiMap(), Buffer.buffer(doc), context.asyncAssertSuccess(response -> {
      context.assertEquals(201, response.statusCode());
      response.endHandler(x -> doLogin(context));
    }));
  }

  public void doLogin(TestContext context) {
    String doc = "{" + LS
            + "  \"tenant\" : \"t1\"," + LS
            + "  \"username\" : \"peter\"," + LS
            + "  \"password\" : \"peter-password\"" + LS
            + "}";
    httpClient.post(port, "localhost", "/authn/login",
        MultiMap.caseInsensitiveMultiMap().add("X-Okapi-Tenant", okapiTenant),
        Buffer.buffer(doc), context.asyncAssertSuccess(response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      okapiToken = response.getHeader("X-Okapi-Token");
      response.endHandler(x -> useItWithGet(context));
    }));
  }

  public void useItWithGet(TestContext context) {
    httpClient.get(port, "localhost", "/testb",
        MultiMap.caseInsensitiveMultiMap().add("X-Okapi-Token", okapiToken)
        .add("X-Okapi-Tenant", okapiTenant), context.asyncAssertSuccess(response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      response.handler(x -> {
        context.assertEquals("It works", x.toString());
      });
      response.endHandler(x -> {
        useItWithPost(context);
      });
    }));
  }

  public void useItWithPost(TestContext context) {
    Buffer body = Buffer.buffer();
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    headers.add("X-Okapi-Token", okapiToken);
    headers.add("X-Okapi-Tenant", okapiTenant);
    headers.add("Accept", "text/xml");
    headers.add("Content-Type", "text/plain");
    httpClient.post(port, "localhost", "/testb", headers, Buffer.buffer("Okapi"),
        context.asyncAssertSuccess(response -> {
      context.assertEquals(200, response.statusCode());
      response.handler(body::appendBuffer);
      response.endHandler(x -> {
        context.assertEquals("<test>Hello Okapi</test>", body.toString());
        declareSample2(context);
      });
    }));
  }

  public void declareSample2(TestContext context) {
    final String doc = "{" + LS
            + "  \"id\" : \"sample-module2-1.0.0\"," + LS
            + "  \"name\" : \"sample2\"," + LS
            + "  \"filters\" : [ {" + LS
            + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
            + "    \"path\" : \"/testb\"," + LS
            + "    \"level\" : \"31\"," + LS
            + "    \"type\" : \"request-response\"" + LS
            + "  } ]" + LS
            + "}";
    httpClient.post(port, "localhost", "/_/proxy/modules", Buffer.buffer(doc),
        context.asyncAssertSuccess(response -> {
      context.assertEquals(201, response.statusCode());
      response.endHandler(x -> {
        deploySample2(context);
      });
    }));
  }

  public void deploySample2(TestContext context) {
    final String doc = "{" + LS
        + "  \"instId\" : \"sample2-inst\"," + LS
        + "  \"srvcId\" : \"sample-module2-1.0.0\"," + LS
        + "  \"url\" : \"http://localhost:9232\"" + LS            + "}";
    httpClient.post(port, "localhost", "/_/discovery/modules", Buffer.buffer(doc),
        context.asyncAssertSuccess(response -> {
          context.assertEquals(201, response.statusCode());
          locationSample2 = response.getHeader("Location");
          response.endHandler(x -> {
            tenantEnableModuleSample2(context);
          });
        }));
  }

  public void tenantEnableModuleSample2(TestContext context) {
    final String doc = "{" + LS
        + "  \"id\" : \"sample-module2\"" + LS
        + "}";
    httpClient.post(port, "localhost", "/_/proxy/tenants/" + okapiTenant + "/modules",
        Buffer.buffer(doc), context.asyncAssertSuccess(response -> {
      context.assertEquals(201, response.statusCode());
      response.endHandler(x -> {
        // deleteTenant(context);
        declareSample3(context);
      });
    }));
  }

  public void declareSample3(TestContext context) {
    final String doc = "{" + LS
        + "  \"id\" : \"sample-module3-1.0.0\"," + LS
        + "  \"name\" : \"sample3\"," + LS
        + "  \"filters\" : [ {" + LS
        + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
        + "    \"path\" : \"/sample\"," + LS
        + "    \"level\" : \"05\"," + LS
        + "    \"type\" : \"headers\"" + LS
        + "  }, {" + LS
        + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
        + "    \"path\" : \"/sample\"," + LS
        + "    \"level\" : \"45\"," + LS
        + "    \"type\" : \"headers\"" + LS
        + "  }, {" + LS
        + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
        + "    \"path\" : \"/sample\"," + LS
        + "    \"level\" : \"33\"," + LS
        + "    \"type\" : \"request-only\"" + LS
        + "  } ]" + LS
        + "}";
    httpClient.post(port, "localhost", "/_/proxy/modules", Buffer.buffer(doc),
        context.asyncAssertSuccess(response -> {
          context.assertEquals(201, response.statusCode());
          response.endHandler(x -> {
            deploySample3(context);
          });
        }));
  }

  public void deploySample3(TestContext context) {
    final String doc = "{" + LS
        + "  \"instId\" : \"sample3-inst\"," + LS
        + "  \"srvcId\" : \"sample-module3-1.0.0\"," + LS
        + "  \"url\" : \"http://localhost:9232\"" + LS            + "}";
    httpClient.post(port, "localhost", "/_/discovery/modules", Buffer.buffer(doc),
        context.asyncAssertSuccess(response -> {
          context.assertEquals(201, response.statusCode());
          locationSample3 = response.getHeader("Location");
          response.endHandler(x -> {
            tenantEnableModuleSample3(context);
          });
        }));
  }

  public void tenantEnableModuleSample3(TestContext context) {
    final String doc = "{" + LS
        + "  \"id\" : \"sample-module3-1.0.0\"" + LS
        + "}";
    httpClient.post(port, "localhost", "/_/proxy/tenants/" + okapiTenant + "/modules",
        Buffer.buffer(doc), context.asyncAssertSuccess(response -> {
          context.assertEquals(201, response.statusCode());
          response.endHandler(x -> {
            repeatPostInit(context);
          });
        }));
  }

  public void repeatPostInit(TestContext context) {
    repeatPostRunning = 0;
    // 10 is enough for regular testing, but the performance improves up to 50k
    final int iterations = 10;
    //final int iterations = 50000;
    final int parallels = 10;
    for (int i = 0; i < parallels; i++) {
      repeatPostRun(context, 0, iterations, parallels);
    }
  }

  public void repeatPostRun(TestContext context, int cnt, int max, int parallels) {
    final String msg = "Okapi" + cnt;
    if (cnt == max) {
      if (--repeatPostRunning == 0) {
        long timeDiff = (System.nanoTime() - startTime) / 1000000;
        logger.info("repeatPost " + timeDiff + " elapsed ms. " + 1000 * max * parallels / timeDiff + " req/sec");
        vertx.setTimer(1, x -> deleteTenant(context));
      }
      return;
    } else if (cnt == 0) {
      if (repeatPostRunning == 0) {
        startTime = System.nanoTime();
      }
      repeatPostRunning++;
      logger.debug("repeatPost " + max + " iterations");
    }
    Buffer body = Buffer.buffer();
    httpClient.post(port, "localhost", "/testb",
        MultiMap.caseInsensitiveMultiMap().add("X-Okapi-Token", okapiToken).add("X-Okapi-Tenant", okapiTenant),
        Buffer.buffer(msg), context.asyncAssertSuccess(response -> {
          context.assertEquals(200, response.statusCode());
          String headers = response.headers().entries().toString();
          response.handler(body::appendBuffer);
          response.endHandler(x -> {
            context.assertEquals("Hello Hello " + msg, body.toString());
            repeatPostRun(context, cnt + 1, max, parallels);
          });
          response.exceptionHandler(e -> context.fail(e));
        }));
  }

  public void deleteTenant(TestContext context) {
    httpClient.delete(port, "localhost", locationTenant,
        context.asyncAssertSuccess(response -> {
      context.assertEquals(204, response.statusCode());
      response.endHandler(x -> {
        done(context);
      });
    }));
  }

  public void done(TestContext context) {
    async.complete();
  }
}
