/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.jayway.restassured.RestAssured;
import static com.jayway.restassured.RestAssured.*;
import com.jayway.restassured.response.Response;
import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured.RestAssuredClient;

@RunWith(VertxUnitRunner.class)
public class DeployModuleIntegration {
  private final Logger logger = LoggerFactory.getLogger("okapi");

  Vertx vertx;
  Async async;

  private String locationTenant;
  private String locationSample;
  private String locationSample2;
  private String locationSample3;
  private String locationAuth;
  private String okapiToken;
  private final String okapiTenant = "roskilde";
  private HttpClient httpClient;
  private static final String LS = System.lineSeparator();

  public DeployModuleIntegration() {
  }

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    JsonObject conf = new JsonObject()
      .put("storage", "inmemory");

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
      httpClient.delete(port, "localhost", locationAuth, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationAuth = null;
          td(context);
        });
      }).end();
      return;
    }
    if (locationSample != null) {
      httpClient.delete(port, "localhost", locationSample, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationSample = null;
          td(context);
        });
      }).end();
      return;
    }
    if (locationSample2 != null) {
      httpClient.delete(port, "localhost", locationSample2, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationSample2 = null;
          td(context);
        });
      }).end();
      return;
    }
    if (locationSample3 != null) {
      httpClient.delete(port, "localhost", locationSample3, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationSample3 = null;
          td(context);
        });
      }).end();
      return;
    }
    vertx.close(x -> {
      async.complete();
    });
  }

  private int port = Integer.parseInt(System.getProperty("port", "9130"));

  @Test
  public void test_sample(TestContext context) {
    async = context.async();

    RestAssured.port = port;

    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
            .assumingBaseUri("https://okapi.io");

    RestAssuredClient c;

    final String doc1 = "{ }";
    c = api.createRestAssured();
    // not according to RAML spec so that can not be verified
    c.given()
            .header("Content-Type", "application/json")
            .body(doc1).post("/_/xyz").then().statusCode(404);
    if (!c.getLastReport().isEmpty()) {
      logger.info("0:" + c.getLastReport().toString());
    }

    c = api.createRestAssured();
    final String bad_doc = "{"+LS
            + "  \"name\" : \"auth\","+LS  // the comma here makes it bad json!
            + "}";
    c.given()
            .header("Content-Type", "application/json")
            .body(bad_doc).post("/_/modules").then().statusCode(400);
    if (!c.getLastReport().isEmpty()) {
      logger.info("1:" + c.getLastReport().toString());
    }

    final String doc2 = "{"+LS
            + "  \"id\" : \"auth\","+LS
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
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(doc2).post("/_/modules").then().statusCode(500);
    if (!c.getLastReport().isEmpty()) {
      logger.info("2:" + c.getLastReport().toString());
    }
    deployNoId(context);
  }

  public void deployNoId(TestContext context) {
    final String doc = "{"+LS
            + "  \"name\" : \"auth\","+LS
            + "  \"descriptor\" : {"+LS
            + "    \"cmdlineStart\" : \"sleep %p\","+LS
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
      context.assertEquals(400, response.statusCode());
      response.endHandler(x -> {
        deployAuth(context);
      });
    }).end(doc);
  }

  public void deployAuth(TestContext context) {
    final String doc = "{"+LS
            + "  \"id\" : \"auth\","+LS
            + "  \"name\" : \"auth\","+LS
            + "  \"descriptor\" : {"+LS
            + "    \"cmdlineStart\" : "
            + "\"java -Dport=%p -jar ../okapi-auth/target/okapi-auth-fat.jar\","+LS
            + "    \"cmdlineStop\" : null"+LS
            + "  },"+LS
            + "  \"routingEntries\" : [ {"+LS
            + "    \"methods\" : [ \"*\" ],"+LS
            + "    \"path\" : \"/s\","+LS
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
      context.assertNotNull(locationAuth);
      response.endHandler(x -> {
        deploySample(context);
      });
    }).end(doc);
  }

  public void deploySample(TestContext context) {
    final String doc = "{"+LS
            + "  \"id\" : \"sample-module\","+LS
            + "  \"name\" : \"sample module\","+LS
            + "  \"url\" : null,"+LS
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
      response.handler(body -> {
        context.assertEquals(doc, body.toString());
      });
      Assert.assertNotNull(locationSample);
      response.endHandler(x -> {
        listModules(context, doc);
      });
    }).end(doc);
  }

  public void listModules(TestContext context, String doc) {
    final String listdoc = "[ {"+LS
        + "  \"id\" : \"auth\"," +LS
        + "  \"name\" : \"auth\"," +LS
        + "  \"url\" : null" +LS
        + "}, {" + LS
        + "  \"id\" : \"sample-module\"," +LS
        + "  \"name\" : \"sample module\"," +LS
        + "  \"url\" : null" +LS
        + "} ]";
    httpClient.get(port, "localhost", "/_/modules/", response -> {
      response.handler(body -> {
        context.assertEquals(200, response.statusCode());
        //context.assertEquals("[ \"auth\", \"sample-module\" ]", body.toString());
        context.assertEquals(listdoc, body.toString());
      });
      response.endHandler(x -> {
        getIt(context, doc);
      });
    }).end();

  }

  public void getIt(TestContext context, String doc) {
    httpClient.get(port, "localhost", locationSample, response -> {
      response.handler(body -> {
        context.assertEquals(doc, body.toString());
      });
      response.endHandler(x -> {
        createTenant(context);
      });
    }).end();
  }
  
  public void createTenant(TestContext context) {
    final String doc = "{"+LS
            + "  \"id\" : \"" + okapiTenant + "\","+LS
            + "  \"name\" : \"" + okapiTenant + "\","+LS
            + "  \"description\" : \"Roskilde bibliotek\""+LS
            + "}";
    httpClient.post(port, "localhost", "/_/tenants", response -> {
      context.assertEquals(201, response.statusCode());
      locationTenant = response.getHeader("Location");
      response.handler(body -> {
        context.assertEquals(doc, body.toString());
      });
      response.endHandler(x -> {
        tenantEnableModuleAuth(context);
      });
    }).end(doc);
  }

  public void tenantEnableModuleAuth(TestContext context) {
    final String doc = "{"+LS
            + "  \"module\" : \"auth\""+LS
            + "}";
    httpClient.post(port, "localhost", "/_/tenants/" + okapiTenant + "/modules", response -> {
      context.assertEquals(200, response.statusCode());
      response.endHandler(x -> {
        tenantListModules1(context);
      });
    }).end(doc);
  }

  public void tenantListModules1(TestContext context) {
    httpClient.get(port, "localhost", "/_/tenants/" + okapiTenant + "/modules", response -> {
      context.assertEquals(200, response.statusCode());
      response.handler(x -> {
        context.assertEquals("[ \"auth\" ]", x.toString());
      });
      response.endHandler(x -> {
        tenantEnableModuleSample(context);
      });
    }).end();
  }

  public void tenantEnableModuleSample(TestContext context) {
    final String doc = "{"+LS
            + "  \"module\" : \"sample-module\""+LS
            + "}";
    httpClient.post(port, "localhost", "/_/tenants/" + okapiTenant + "/modules", response -> {
      context.assertEquals(200, response.statusCode());
      response.endHandler(x -> {
        tenantListModules2(context);
      });
    }).end(doc);
  }

  public void tenantListModules2(TestContext context) {
    httpClient.get(port, "localhost", "/_/tenants/" + okapiTenant + "/modules", response -> {
      context.assertEquals(200, response.statusCode());
      response.handler(x -> {
        context.assertEquals("[ \"auth\", \"sample-module\" ]", x.toString());
      });
      response.endHandler(x -> {
        reload(context);
      });
    }).end();
  }

  public void reload(TestContext context) {
    httpClient.get(port, "localhost", "/_/test/reloadtenant/" + okapiTenant, response -> {
      context.assertEquals(204, response.statusCode());
      response.endHandler(x -> {
        tenantListModules3(context);
      });
    }).end();
  }



  public void tenantListModules3(TestContext context) {
    httpClient.get(port, "localhost", "/_/tenants/" + okapiTenant + "/modules", response -> {
      context.assertEquals(200, response.statusCode());
      response.handler(x -> {
        context.assertEquals("[ \"auth\", \"sample-module\" ]", x.toString());
      });
      response.endHandler(x -> {
        updateTenant(context);
      });
    }).end();
  }

  public void updateTenant(TestContext context) {
    final String doc = "{"+LS
            + "  \"id\" : \"" + okapiTenant + "\","+LS
            + "  \"name\" : \"Roskilde-library\","+LS
            + "  \"description\" : \"Roskilde bibliotek\""+LS
            + "}";
    httpClient.put(port, "localhost", "/_/tenants/" + okapiTenant, response -> {
      context.assertEquals(200, response.statusCode());
      response.handler(body -> {
        context.assertEquals(doc, body.toString());
      });
      response.endHandler(x -> {
        tenantListModules4(context);
      });
    }).end(doc);
  }
  
  public void tenantListModules4(TestContext context) {
    httpClient.get(port, "localhost", "/_/tenants/" + okapiTenant + "/modules", response -> {
      context.assertEquals(200, response.statusCode());
      response.handler(x -> {
        System.out.println("tenantListModules4: " + x);
        context.assertEquals("[ \"auth\", \"sample-module\" ]", x.toString());
      });
      response.endHandler(x -> {
        useWithoutTenant(context);
      });
    }).end();
  }

  public void useWithoutTenant(TestContext context) {
    HttpClientRequest req = httpClient.get(port, "localhost", "/sample", response -> {
      context.assertEquals(403, response.statusCode());
      String trace = response.getHeader("X-Okapi-Trace");
      context.assertTrue(trace == null);
      response.endHandler(x -> {
        useWithoutMatchingPath(context);
      });
    });
    req.end();
  }

  public void useWithoutMatchingPath(TestContext context) {
    // auth only listens on /s*
    HttpClientRequest req = httpClient.get(port, "localhost", "/q", response -> {
      context.assertEquals(404, response.statusCode());
      response.endHandler(x -> {
        useWithoutLogin(context);
      });
    });
    req.putHeader("X-Okapi-Tenant", okapiTenant);
    req.end();
  }

  public void useWithoutLogin(TestContext context) {
    HttpClientRequest req = httpClient.get(port, "localhost", "/sample", response -> {
      context.assertEquals(401, response.statusCode());
      String trace = response.getHeader("X-Okapi-Trace");
      context.assertTrue(trace != null && trace.matches(".*GET auth:401.*"));
      response.endHandler(x -> {
        failLogin(context);
      });
    });
    req.putHeader("X-Okapi-Tenant", okapiTenant);
    req.end();
  }

  public void failLogin(TestContext context) {
    String doc = "{"+LS
            + "  \"tenant\" : \"t1\","+LS
            + "  \"username\" : \"peter\","+LS
            + "  \"password\" : \"peter37\""+LS
            + "}";
    HttpClientRequest req = httpClient.post(port, "localhost", "/login", response -> {
      context.assertEquals(401, response.statusCode());
      response.endHandler(x -> {
        doLogin(context);
      });
    });
    req.putHeader("X-Okapi-Tenant", okapiTenant);
    req.end(doc);
  }

  public void doLogin(TestContext context) {
    String doc = "{"+LS
            + "  \"tenant\" : \"t1\","+LS
            + "  \"username\" : \"peter\","+LS
            + "  \"password\" : \"peter-password\""+LS
            + "}";
    HttpClientRequest req = httpClient.post(port, "localhost", "/login", response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      context.assertTrue(headers != null && headers.matches(".*X-Okapi-Trace=POST auth:200.*"));
      okapiToken = response.getHeader("X-Okapi-Token");
      response.endHandler(x -> {
        useItWithGet(context);
      });
    });
    req.putHeader("X-Okapi-Tenant", okapiTenant);
    req.end(doc);
  }

  public void useItWithGet(TestContext context) {
    HttpClientRequest req = httpClient.get(port, "localhost", "/sample", response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      context.assertTrue(headers != null && headers.matches(".*X-Okapi-Trace=GET sample-module:200.*"));
      response.handler(x -> {
        context.assertEquals("It works", x.toString());
      });
      response.endHandler(x -> {
        useItWithPost(context);
      });
    });
    req.headers().add("X-Okapi-Token", okapiToken);
    req.putHeader("X-Okapi-Tenant", okapiTenant);
    req.end();
  }

  public void useItWithPost(TestContext context) {
    Buffer body = Buffer.buffer();
    HttpClientRequest req = httpClient.post(port, "localhost", "/sample", response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      context.assertTrue(headers != null && headers.matches(".*X-Okapi-Trace=POST sample-module:200.*"));
      response.handler(x -> {
        body.appendBuffer(x);
      });
      response.endHandler(x -> {
        context.assertEquals("Hello  (XML) Okapi", body.toString());
        useNoPath(context);
      });
    });
    req.headers().add("X-Okapi-Token", okapiToken);
    req.putHeader("X-Okapi-Tenant", okapiTenant);
    req.putHeader("Content-Type", "text/xml");  
    req.end("Okapi");
  }

  public void useNoPath(TestContext context) {
    HttpClientRequest req = httpClient.get(port, "localhost", "/samplE", response -> {
      context.assertEquals(202, response.statusCode());
      response.endHandler(x -> {
        useNoMethod(context);
      });
    });
    req.headers().add("X-Okapi-Token", okapiToken);
    req.putHeader("X-Okapi-Tenant", okapiTenant);
    req.end();
  }

  public void useNoMethod(TestContext context) {
    HttpClientRequest req = httpClient.delete(port, "localhost", "/sample", response -> {
      context.assertEquals(202, response.statusCode());
      response.endHandler(x -> {
        deploySample2(context);
      });
    });
    req.headers().add("X-Okapi-Token", okapiToken);
    req.putHeader("X-Okapi-Tenant", okapiTenant);
    req.end();
  }

  public void deploySample2(TestContext context) {
    final String doc = "{"+LS
            + "  \"id\" : \"sample-module2\","+LS
            + "  \"name\" : \"another-sample-module2\","+LS
            + "  \"url\" : \"http://localhost:9132\","+LS
            + "  \"descriptor\" : null,"+LS
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
        tenantEnableModuleSample2(context);
      });
    }).end(doc);
  }

  public void tenantEnableModuleSample2(TestContext context) {
    final String doc = "{"+LS
            + "  \"module\" : \"sample-module2\""+LS
            + "}";
    httpClient.post(port, "localhost", "/_/tenants/" + okapiTenant + "/modules", response -> {
      context.assertEquals(200, response.statusCode());
      response.endHandler(x -> {
        deploySample3(context);
      });
    }).end(doc);
  }

  public void deploySample3(TestContext context) {
    final String doc = "{"+LS
            + "  \"id\" : \"sample-module3\","+LS
            + "  \"name\" : \"sample-module3\","+LS
            + "  \"url\" : \"http://localhost:9132\","+LS
            + "  \"descriptor\" : {"+LS
            + "    \"cmdlineStart\" : \"sleep 1\","+LS
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
        tenantEnableModuleSample3(context);
      });
    }).end(doc);
  }

  public void tenantEnableModuleSample3(TestContext context) {
    final String doc = "{"+LS
            + "  \"module\" : \"sample-module3\""+LS
            + "}";
    httpClient.post(port, "localhost", "/_/tenants/" + okapiTenant + "/modules", response -> {
      context.assertEquals(200, response.statusCode());
      response.endHandler(x -> {
        useItWithGet2(context);
      });
    }).end(doc);
  }

  public void useItWithGet2(TestContext context) {
    HttpClientRequest req = httpClient.get(port, "localhost", "/sample", response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      context.assertTrue(headers != null
        && headers.matches(".*X-Okapi-Trace=GET sample-module2:200.*"));
      response.handler(x -> {
        context.assertEquals("It works", x.toString());
      });
      response.endHandler(x -> {
        postMsg(context);
      });
    });
    req.headers().add("X-Okapi-Token", okapiToken);
    req.putHeader("X-Okapi-Tenant", okapiTenant);
    req.end();
  }

  public void postMsg(TestContext context) {
    final String msg = "OkapiX";
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
        reloadModules(context);
      });
      response.exceptionHandler(e -> {
        context.fail(e);
      });
    });
    req.headers().add("X-Okapi-Token", okapiToken);
    req.putHeader("X-Okapi-Tenant", okapiTenant);
    req.end(msg);
  }

  public void reloadModules(TestContext context) {
    httpClient.get(port, "localhost", "/_/test/reloadmodules", response -> {
      context.assertEquals(204, response.statusCode());
      response.endHandler(x -> {
        useItWithGet3(context);
      });
    }).end();
  }

  public void useItWithGet3(TestContext context) {
    HttpClientRequest req = httpClient.get(port, "localhost", "/sample", response -> {
      context.assertEquals(200, response.statusCode());
      String headers = response.headers().entries().toString();
      context.assertTrue(headers.matches(".*X-Okapi-Trace=GET sample-module2:200.*"));
      response.handler(x -> {
        context.assertEquals("It works (XML) ", x.toString());
      });
      response.endHandler(x -> {
        listTenantModules1(context);
      });
    });
    req.headers().add("X-Okapi-Token", okapiToken);
    req.putHeader("X-Okapi-Tenant", okapiTenant);
    req.putHeader("Content-Type", "text/xml");  
    req.end();
  }

  public void listTenantModules1(TestContext context) {
    httpClient.get(port, "localhost", locationTenant + "/modules", response -> {
      context.assertEquals(200, response.statusCode());
      response.handler(x -> {
        logger.debug("listTenantModules: " + x.toString());
        //String explist = "[ \"sample-module2\", \"sample-module\", \"auth\", \"sample-module3\" ]";
        String explist = "[ \"auth\", \"sample-module\", \"sample-module2\", \"sample-module3\" ]";
        context.assertEquals(explist, x.toString());
      });
      response.endHandler(x -> {
        disableTenantModule(context);
      });
    }).end();
  }


  public void disableTenantModule(TestContext context) {
    httpClient.delete(port, "localhost", locationTenant + "/modules/sample-module3", response -> {
      logger.debug("disableTenantModule: " + response.statusCode());
      context.assertEquals(204, response.statusCode());
      response.endHandler(x -> {
        listTenantModules2(context);
      });
    }).end();
  }

  public void listTenantModules2(TestContext context) {
    httpClient.get(port, "localhost", locationTenant + "/modules", response -> {
      context.assertEquals(200, response.statusCode());
      response.handler(x -> {
        String explist = "[ \"auth\", \"sample-module\", \"sample-module2\" ]";
        logger.debug("listTenantModules: " + x.toString());
        context.assertEquals(explist, x.toString());
      });
      response.endHandler(x -> {
        deleteTenant(context);
      });
    }).end();
  }


  public void deleteTenant(TestContext context) {
    httpClient.delete(port, "localhost", locationTenant, response -> {
      context.assertEquals(204, response.statusCode());
      response.endHandler(x -> {
        done(context);
      });
    }).end();
  }

  public void done(TestContext context) {
    async.complete();
  }
}
