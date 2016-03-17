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
import com.jayway.restassured.http.ContentType;
import static org.hamcrest.Matchers.*;
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
  private String locationAuth = null;
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
    Response r;

    final String doc1 = "{ }";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(doc1).post("/_/xyz").then().statusCode(404);
    Assert.assertEquals("RamlReport{requestViolations=[Resource '/_/xyz' is not defined], "
            + "responseViolations=[], validationViolations=[]}",
            c.getLastReport().toString());

    c = api.createRestAssured();
    final String bad_doc = "{"+LS
            + "  \"name\" : \"auth\","+LS  // the comma here makes it bad json!
            + "}";
    c.given()
            .header("Content-Type", "application/json")
            .body(bad_doc).post("/_/modules").then().statusCode(400);

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
    Assert.assertEquals("RamlReport{requestViolations=[], "
            + "responseViolations=[Body given but none defined on action(POST /_/modules) "
            + "response(500)], validationViolations=[]}",
            c.getLastReport().toString());
    final String doc3 = "{" + LS
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
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(doc3).post("/_/modules").then().statusCode(400);
    Assert.assertEquals("RamlReport{requestViolations=[], "
            + "responseViolations=[Body given but none defined on action(POST /_/modules) "
            + "response(400)], validationViolations=[]}",
            c.getLastReport().toString());
    final String doc4 = "{" + LS
              + "  \"id\" : \"auth\"," + LS
              + "  \"name\" : \"auth\"," + LS
              + "  \"descriptor\" : {" + LS
              + "    \"cmdlineStart\" : "
              + "\"java -Dport=%p -jar ../okapi-auth/target/okapi-auth-fat.jar\"," + LS
              + "    \"cmdlineStop\" : null" + LS
              + "  }," + LS
              + "  \"routingEntries\" : [ {" + LS
              + "    \"methods\" : [ \"*\" ]," + LS
              + "    \"path\" : \"/s\"," + LS
              + "    \"level\" : \"10\"," + LS
              + "    \"type\" : \"request-response\"" + LS
              + "  }, {"
              + "    \"methods\" : [ \"POST\" ]," + LS
              + "    \"path\" : \"/login\"," + LS
              + "    \"level\" : \"20\"," + LS
              + "    \"type\" : \"request-response\"" + LS
              + "  } ]" + LS
              + "}";
      c = api.createRestAssured();
      r = c.given()
              .header("Content-Type", "application/json")
              .body(doc4).post("/_/modules").then().statusCode(201)
              .extract().response();
      Assert.assertTrue(c.getLastReport().isEmpty());
      locationAuth = r.getHeader("Location");

    final String doc5 = "{" + LS
            + "  \"id\" : \"sample-module\"," + LS
            + "  \"name\" : \"sample module\"," + LS
            + "  \"url\" : null," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"cmdlineStart\" : "
            + "\"java -Dport=%p -jar ../okapi-sample-module/target/okapi-sample-module-fat.jar\"," + LS
            + "    \"cmdlineStop\" : null" + LS
            + "  }," + LS
            + "  \"routingEntries\" : [ {" + LS
            + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
            + "    \"path\" : \"/sample\"," + LS
            + "    \"level\" : \"30\"," + LS
            + "    \"type\" : \"request-response\"" + LS
            + "  } ]" + LS
            + "}";
    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(doc5).post("/_/modules").then().statusCode(201)
            .extract().response();
    Assert.assertTrue(c.getLastReport().isEmpty());
    locationSample = r.getHeader("Location");
    
    c = api.createRestAssured();
    c.given().get("/_/modules").then().statusCode(200);
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given()
            .get(locationSample).then().statusCode(200).body(equalTo(doc5));
    Assert.assertTrue(c.getLastReport().isEmpty());
    
    final String doc6 = "{" + LS
            + "  \"id\" : \"" + okapiTenant + "\"," + LS
            + "  \"name\" : \"" + okapiTenant + "\"," + LS
            + "  \"description\" : \"Roskilde bibliotek\"" + LS
            + "}";
    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(doc6).post("/_/tenants")
            .then().statusCode(201)
            .body(equalTo(doc6))
            .extract().response();
    Assert.assertTrue(c.getLastReport().isEmpty());
    locationTenant = r.getHeader("Location");

    final String doc7 = "{"+LS
            + "  \"module\" : \"auth\""+LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(doc7).post("/_/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(doc7));
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200).body(equalTo("[ \"auth\" ]"));
    Assert.assertTrue(c.getLastReport().isEmpty());

    final String doc8 = "{"+LS
            + "  \"module\" : \"sample-module\""+LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(doc8).post("/_/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(doc8));
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200).body(equalTo("[ \"auth\", \"sample-module\" ]"));
    Assert.assertTrue(c.getLastReport().isEmpty());

    given().get("/_/test/reloadtenant/" + okapiTenant)
            .then().statusCode(204);

    c = api.createRestAssured();
    c.given().get("/_/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200).body(equalTo("[ \"auth\", \"sample-module\" ]"));
    Assert.assertTrue(c.getLastReport().isEmpty());

    String doc9 = "{"+LS
            + "  \"id\" : \"" + okapiTenant + "\","+LS
            + "  \"name\" : \"Roskilde-library\","+LS
            + "  \"description\" : \"Roskilde bibliotek\""+LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(doc9).put("/_/tenants/" + okapiTenant)
            .then().statusCode(200)
            .body(equalTo(doc9));
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200).body(equalTo("[ \"auth\", \"sample-module\" ]"));
    Assert.assertTrue(c.getLastReport().isEmpty());

    given().get("/sample")
            .then().statusCode(403);

    given().header("X-Okapi-Tenant", okapiTenant).get("/q")
            .then().statusCode(404);

    given().header("X-Okapi-Tenant", okapiTenant).get("/sample")
            .then().statusCode(401);

    String doc10 = "{" + LS
            + "  \"tenant\" : \"t1\"," + LS
            + "  \"username\" : \"peter\"," + LS
            + "  \"password\" : \"peter37\"" + LS
            + "}";

    given().header("Content-Type", "application/json").body(doc10)
            .header("X-Okapi-Tenant", okapiTenant).post("/login")
            .then().statusCode(401);

    String doc11 = "{"+LS
            + "  \"tenant\" : \"t1\","+LS
            + "  \"username\" : \"peter\","+LS
            + "  \"password\" : \"peter-password\""+LS
            + "}";
    okapiToken = given().header("Content-Type", "application/json").body(doc11)
            .header("X-Okapi-Tenant", okapiTenant).post("/login")
            .then().statusCode(200).extract().header("X-Okapi-Token");

    given().header("X-Okapi-Tenant", okapiTenant)
           .header("X-Okapi-Token", okapiToken)
           .get("/sample")
            .then().statusCode(200).body(equalTo("It works"));

    given().header("X-Okapi-Tenant", okapiTenant)
            .header("X-Okapi-Token", okapiToken)
            .header("Content-Type", "text/xml")
            .body("Okapi").post("/sample")
            .then().statusCode(200).body(equalTo("Hello  (XML) Okapi"));

    given().header("X-Okapi-Tenant", okapiTenant)
            .header("X-Okapi-Token", okapiToken)
            .get("/samplE")
            .then().statusCode(202);

    given().header("X-Okapi-Tenant", okapiTenant)
            .header("X-Okapi-Token", okapiToken)
            .delete("/sample")
            .then().statusCode(202);

    String doc12 = "{"+LS
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
    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(doc12).post("/_/modules").then().statusCode(201)
            .extract().response();
    Assert.assertTrue(c.getLastReport().isEmpty());
    locationSample2 = r.getHeader("Location");

    String doc13 = "{"+LS
            + "  \"module\" : \"sample-module2\""+LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(doc13).post("/_/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(doc13));
    Assert.assertTrue(c.getLastReport().isEmpty());

    String doc14 = "{"+LS
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
    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(doc14).post("/_/modules").then().statusCode(201)
            .extract().response();
    Assert.assertTrue(c.getLastReport().isEmpty());
    locationSample3 = r.getHeader("Location");

    String doc15 = "{"+LS
            + "  \"module\" : \"sample-module3\""+LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(doc15).post("/_/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(doc15));
    Assert.assertTrue(c.getLastReport().isEmpty());

    given().header("X-Okapi-Tenant", okapiTenant)
            .header("X-Okapi-Token", okapiToken)
            .get("/sample")
            .then().statusCode(200).body(equalTo("It works"));

    postMsg(context);
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
