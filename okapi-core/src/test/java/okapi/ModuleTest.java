/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
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
import static org.hamcrest.Matchers.*;
import com.jayway.restassured.response.Response;
import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured.RestAssuredClient;

@RunWith(VertxUnitRunner.class)
public class ModuleTest {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  Vertx vertx;
  Async async;

  private String locationTenant;
  private String locationSample;
  private String locationSample2;
  private String locationSample3;
  private String locationSample4;
  private String locationAuth = null;
  private String okapiToken;
  private final String okapiTenant = "roskilde";
  private HttpClient httpClient;
  private static final String LS = System.lineSeparator();

  public ModuleTest() {
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
    if (locationSample4 != null) {
      httpClient.delete(port, "localhost", locationSample4, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationSample4 = null;
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
            .assumingBaseUri("https://okapi.cloud");

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
    final String bad_doc = "{" + LS
            + "  \"name\" : \"auth\"," + LS // the comma here makes it bad json!
            + "}";
    c.given()
            .header("Content-Type", "application/json")
            .body(bad_doc).post("/_/modules").then().statusCode(400);

    final String doc2 = "{" + LS
            + "  \"id\" : \"auth\"," + LS
            + "  \"name\" : \"auth\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"cmdlineStart\" : "
            + "\"java -Dport=%p -jar ../okapi-auth/target/okapi-unknown.jar\"," + LS
            // + "\"sleep %p\","+LS
            + "    \"cmdlineStop\" : null" + LS
            + "  }," + LS
            + "  \"routingEntries\" : [ {" + LS
            + "    \"methods\" : [ \"*\" ]," + LS
            + "    \"path\" : \"/\"," + LS
            + "    \"level\" : \"10\"," + LS
            + "    \"type\" : \"request-response\"" + LS
            + "  } ]" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(doc2).post("/_/modules").then().statusCode(500);
    Assert.assertEquals("RamlReport{requestViolations=[], "
            + "responseViolations=[Body given but none defined on action(POST /_/modules) "
            + "response(500)], validationViolations=[]}",
            c.getLastReport().toString());

    // post a module with missing "id":
    final String doc3 = "{" + LS
            + "  \"name\" : \"auth\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"cmdlineStart\" : \"sleep %p\"," + LS
            + "    \"cmdlineStop\" : null" + LS
            + "  }," + LS
            + "  \"routingEntries\" : [ {" + LS
            + "    \"methods\" : [ \"*\" ]," + LS
            + "    \"path\" : \"/\"," + LS
            + "    \"level\" : \"10\"," + LS
            + "    \"type\" : \"request-response\"" + LS
            + "  } ]" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(doc3).post("/_/modules").then().statusCode(400);
    // Will not be according to RAML.. So no Assert on it.
    final String doc4 = "{" + LS
            + "  \"id\" : \"auth\"," + LS
            + "  \"name\" : \"auth\"," + LS
            + "  \"provides\" : [ {" + LS
            + "    \"id\" : \"auth\"," + LS
            + "    \"version\" : \"1.2.3\"" + LS
            + "  } ]," + LS
            + "  \"requires\" : null," + LS
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
    c.given()
            .header("Content-Type", "application/json")
            .body(doc4).post("/_/modules/").then().statusCode(404);
    Assert.assertEquals("RamlReport{requestViolations=[Resource '/_/modules/' is not defined], "
            + "responseViolations=[], validationViolations=[]}",
            c.getLastReport().toString());

    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(doc4).post("/_/modules").then().statusCode(201)
            .extract().response();
    Assert.assertTrue(c.getLastReport().isEmpty());
    locationAuth = r.getHeader("Location");

    c = api.createRestAssured();
    c.given().get(locationAuth)
            .then().statusCode(200);
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/health/modules/auth")
            .then().statusCode(200).body(equalTo(
                    "[ {" + LS
                    + "  \"id\" : \"auth\"," + LS
                    + "  \"status\" : \"OK\"" + LS
                    + "} ]"));
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/health/modules")
            .then().statusCode(200).body(equalTo(
                    "[ {" + LS
                    + "  \"id\" : \"auth\"," + LS
                    + "  \"status\" : \"OK\"" + LS
                    + "} ]"));
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/health/modules/unknown")
            .then().statusCode(200).body(equalTo(
                    "[ {" + LS
                    + "  \"id\" : \"unknown\"," + LS
                    + "  \"status\" : \"Not Found\"" + LS
                    + "} ]"));
    Assert.assertTrue(c.getLastReport().isEmpty());

    final String doc4b = "{" + LS
            + "  \"id\" : \"sample-module\"," + LS
            + "  \"name\" : \"sample module\"," + LS
            + "  \"url\" : null," + LS
            + "  \"provides\" : [ {" + LS
            + "    \"id\" : \"sample\"," + LS
            + "    \"version\" : \"1.0.0\"" + LS
            + "  } ]," + LS
            + "  \"requires\" : [ {" + LS
            + "    \"id\" : \"SOMETHINGWEDONOTHAVE\"," + LS
            + "    \"version\" : \"1.2.3\"" + LS
            + "  } ]," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"cmdlineStart\" : null, " + LS 
            + "    \"cmdlineStop\" : null" + LS
            + "  }," + LS
            + "  \"routingEntries\" : [ ] " + LS
            + "}";

    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(doc4b).post("/_/modules").then().statusCode(400)
            .extract().response();

    final String doc4c = "{" + LS
            + "  \"id\" : \"sample-module\"," + LS
            + "  \"name\" : \"sample module\"," + LS
            + "  \"url\" : null," + LS
            + "  \"provides\" : [ {" + LS
            + "    \"id\" : \"sample\"," + LS
            + "    \"version\" : \"1.0.0\"" + LS
            + "  } ]," + LS
            + "  \"requires\" : [ {" + LS
            + "    \"id\" : \"auth\"," + LS
            + "    \"version\" : \"9.9.3\"" + LS  // We only have 1.2.3
            + "  } ]," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"cmdlineStart\" : null, " + LS
            + "    \"cmdlineStop\" : null" + LS
            + "  }," + LS
            + "  \"routingEntries\" : [ ] " + LS
            + "}";

    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(doc4c).post("/_/modules").then().statusCode(400)
            .extract().response();

    final String doc5 = "{" + LS
            + "  \"id\" : \"sample-module\"," + LS
            + "  \"name\" : \"sample module\"," + LS
            + "  \"url\" : null," + LS
            + "  \"provides\" : [ {" + LS
            + "    \"id\" : \"sample\"," + LS
            + "    \"version\" : \"1.0.0\"" + LS
            + "  } ]," + LS
            + "  \"requires\" : [ {" + LS
            + "    \"id\" : \"auth\"," + LS
            + "    \"version\" : \"1.2.3\"" + LS
            + "  } ]," + LS
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
    c.given().get("/_/modules/").then().statusCode(404);
    Assert.assertEquals("RamlReport{requestViolations=[Resource '/_/modules/' is not defined], "
            + "responseViolations=[], validationViolations=[]}",
            c.getLastReport().toString());

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
    c.given()
            .header("Content-Type", "application/json")
            .body(doc6).post("/_/tenants/")
            .then().statusCode(404);
    Assert.assertEquals("RamlReport{requestViolations=[Resource '/_/tenants/' is not defined], "
            + "responseViolations=[], validationViolations=[]}",
            c.getLastReport().toString());

    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(doc6).post("/_/tenants")
            .then().statusCode(201)
            .body(equalTo(doc6))
            .extract().response();
    Assert.assertTrue(c.getLastReport().isEmpty());
    locationTenant = r.getHeader("Location");

    
    // Try to enable sample without the auth that it requires
    final String doc7a = "{" + LS
            + "  \"id\" : \"sample-module\"" + LS
            + "}";
    c.given()
            .header("Content-Type", "application/json")
            .body(doc7a).post("/_/tenants/" + okapiTenant + "/modules")
            .then().statusCode(400);


    final String doc7 = "{" + LS
            + "  \"id\" : \"auth\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(doc7).post("/_/tenants/" + okapiTenant + "/modules/") // trailing slash not good
            .then().statusCode(404);

    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(doc7).post("/_/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(doc7));
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/tenants/" + okapiTenant + "/modules/")
            .then().statusCode(404);

    c = api.createRestAssured();
    final String exp1 = "[ {" + LS
            + "  \"id\" : \"auth\"" + LS
            + "} ]";
    c.given().get("/_/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200).body(equalTo(exp1));
    Assert.assertTrue(c.getLastReport().isEmpty());

    final String exp2 = "{" + LS
            + "  \"id\" : \"auth\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given().get("/_/tenants/" + okapiTenant + "/modules/auth")
            .then().statusCode(200).body(equalTo(exp2));
    Assert.assertTrue(c.getLastReport().isEmpty());

    final String doc8a = "{" + LS
            + "  \"id\" : \"UNKNOWN-module\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(doc8a).post("/_/tenants/" + okapiTenant + "/modules")
            .then().statusCode(404);

    final String doc8 = "{" + LS
            + "  \"id\" : \"sample-module\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(doc8a).post("/_/tenants/" + okapiTenant + "-UNKNOWN" + "/modules")
            .then().statusCode(404);

    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(doc8).post("/_/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(doc8));
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/tenants/" + okapiTenant + "/modules/")
            .then().statusCode(404);

    c = api.createRestAssured();
    final String exp3 = "[ {" + LS
            + "  \"id\" : \"auth\"" + LS
            + "}, {" + LS
            + "  \"id\" : \"sample-module\"" + LS
            + "} ]";
    c.given().get("/_/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200).body(equalTo(exp3));
    Assert.assertTrue(c.getLastReport().isEmpty());

    given().get("/_/test/reloadtenant/" + okapiTenant)
            .then().statusCode(204);

    c = api.createRestAssured();
    c.given().get("/_/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200).body(equalTo(exp3));
    Assert.assertTrue(c.getLastReport().isEmpty());

    String doc9 = "{" + LS
            + "  \"id\" : \"" + okapiTenant + "\"," + LS
            + "  \"name\" : \"Roskilde-library\"," + LS
            + "  \"description\" : \"Roskilde bibliotek\"" + LS
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
            .then().statusCode(200).body(equalTo(exp3));
    Assert.assertTrue(c.getLastReport().isEmpty());

    given().get("/sample")
            .then().statusCode(403);

    given().header("X-Okapi-Tenant", okapiTenant).get("/q")
            .then().statusCode(404);

    given().header("X-Okapi-Tenant", okapiTenant).get("/sample")
            .then().statusCode(401);

    final String doc10 = "{" + LS
            + "  \"tenant\" : \"t1\"," + LS
            + "  \"username\" : \"peter\"," + LS
            + "  \"password\" : \"peter37\"" + LS
            + "}";

    given().header("Content-Type", "application/json").body(doc10)
            .header("X-Okapi-Tenant", okapiTenant).post("/login")
            .then().statusCode(401);

    final String doc11 = "{" + LS
            + "  \"tenant\" : \"t1\"," + LS
            + "  \"username\" : \"peter\"," + LS
            + "  \"password\" : \"peter-password\"" + LS
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

    final String doc12 = "{" + LS
            + "  \"id\" : \"sample-module2\"," + LS
            + "  \"name\" : \"another-sample-module2\"," + LS
            + "  \"url\" : \"http://localhost:9132\"," + LS
            + "  \"descriptor\" : null," + LS
            + "  \"routingEntries\" : [ {" + LS
            + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
            + "    \"path\" : \"/sample\"," + LS
            + "    \"level\" : \"31\"," + LS
            + "    \"type\" : \"request-response\"" + LS
            + "  } ]" + LS
            + "}";
    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(doc12).post("/_/modules").then().statusCode(201)
            .extract().response();
    Assert.assertTrue(c.getLastReport().isEmpty());
    locationSample2 = r.getHeader("Location");

    final String doc13 = "{" + LS
            + "  \"id\" : \"sample-module2\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(doc13).post("/_/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(doc13));
    Assert.assertTrue(c.getLastReport().isEmpty());

    final String doc14 = "{" + LS
            + "  \"id\" : \"sample-module3\"," + LS
            + "  \"name\" : \"sample-module3\"," + LS
            + "  \"url\" : \"http://localhost:9132\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"cmdlineStart\" : \"sleep 1\"," + LS
            + "    \"cmdlineStop\" : null" + LS
            + "  }," + LS
            + "  \"routingEntries\" : [ {" + LS
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
    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(doc14).post("/_/modules").then().statusCode(201)
            .extract().response();
    Assert.assertTrue(c.getLastReport().isEmpty());
    locationSample3 = r.getHeader("Location");

    final String doc15 = "{" + LS
            + "  \"id\" : \"sample-module3\"" + LS
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

    given().header("X-Okapi-Tenant", okapiTenant)
            .header("X-Okapi-Token", okapiToken)
            .body("OkapiX").post("/sample")
            .then().statusCode(200).body(equalTo("Hello Hello OkapiX"));

    given().get("/_/test/reloadmodules")
            .then().statusCode(204);

    given().header("X-Okapi-Tenant", okapiTenant)
            .header("X-Okapi-Token", okapiToken)
            .header("Content-Type", "text/xml")
            .get("/sample")
            .then().statusCode(200).body(equalTo("It works (XML) "));

    c = api.createRestAssured();
    final String exp4 = "[ {" + LS
            + "  \"id\" : \"auth\"" + LS
            + "}, {" + LS
            + "  \"id\" : \"sample-module\"" + LS
            + "}, {" + LS
            + "  \"id\" : \"sample-module2\"" + LS
            + "}, {" + LS
            + "  \"id\" : \"sample-module3\"" + LS
            + "} ]";
    c.given().get(locationTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(exp4));
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().delete(locationTenant + "/modules/sample-module3")
            .then().statusCode(204);
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    final String exp5 = "[ {" + LS
            + "  \"id\" : \"auth\"" + LS
            + "}, {" + LS
            + "  \"id\" : \"sample-module\"" + LS
            + "}, {" + LS
            + "  \"id\" : \"sample-module2\"" + LS
            + "} ]";
    c.given().get(locationTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(exp5));
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().delete(locationTenant+"/modules/sample-module999") // non-existing
            .then().statusCode(404);

    c = api.createRestAssured();
    c.given().delete(locationTenant+"/modules/auth") // dependency of sample-module
            .then().statusCode(400);
    
    c = api.createRestAssured();
    c.given().delete(locationTenant)
            .then().statusCode(204);
    Assert.assertTrue(c.getLastReport().isEmpty());


    final String doc16 = "{" + LS
            + "  \"id\" : \"sample-module4\"," + LS
            + "  \"name\" : \"sample module\"," + LS
            + "  \"url\" : null" + LS
            + "}";
    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(doc16).post("/_/modules").then().statusCode(201)
            .extract().response();
    locationSample4 = r.getHeader("Location");

    final String doc17 = "{" + LS
            + "  \"id\" : \"sample-module4\"," + LS
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
    c.given()
            .header("Content-Type", "application/json")
            .body(doc17).put(locationSample4).then().statusCode(200);
    async.complete();
  }
}
