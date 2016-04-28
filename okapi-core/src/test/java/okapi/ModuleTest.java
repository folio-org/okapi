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
import com.jayway.restassured.response.ValidatableResponse;
import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured.RestAssuredClient;

@RunWith(VertxUnitRunner.class)
public class ModuleTest {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  Vertx vertx;
  Async async;

  private String locationSampleDeployment;
  private String locationSample5Deployment;
  private String locationAuthDeployment = null;
  private String okapiToken;
  private final String okapiTenant = "roskilde";
  private HttpClient httpClient;
  private static final String LS = System.lineSeparator();
  private int port = Integer.parseInt(System.getProperty("port", "9130"));


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
    RestAssured.port = port;
  }

  @After
  public void tearDown(TestContext context) {
    async = context.async();
    td(context);
  }

  public void td(TestContext context) {
    if (locationAuthDeployment != null) {
      httpClient.delete(port, "localhost", locationAuthDeployment, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationAuthDeployment = null;
          td(context);
        });
      }).end();
      return;
    }
    if (locationSampleDeployment != null) {
      httpClient.delete(port, "localhost", locationSampleDeployment, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationSampleDeployment = null;
          td(context);
        });
      }).end();
      return;
    }
    if (locationSample5Deployment != null) {
      httpClient.delete(port, "localhost", locationSample5Deployment, response -> {
        context.assertEquals(204, response.statusCode());
        response.endHandler(x -> {
          locationSample5Deployment = null;
          td(context);
        });
      }).end();
      return;
    }

    vertx.close(x -> {
      async.complete();
    });
  }

  @Test
  public void testProxy(TestContext context) {
    async = context.async();

    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
            .assumingBaseUri("https://okapi.cloud");

    RestAssuredClient c;
    Response r;

    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body("{ }").post("/_/xyz").then().statusCode(404);
    Assert.assertEquals("RamlReport{requestViolations=[Resource '/_/xyz' is not defined], "
            + "responseViolations=[], validationViolations=[]}",
            c.getLastReport().toString());

    final String badDoc = "{" + LS
            + "  \"name\" : \"BAD\"," + LS // the comma here makes it bad json!
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(badDoc).post("/_/deployment/modules")
            .then().statusCode(400);

    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(badDoc).post("/_/discovery/modules")
            .then().statusCode(400);

    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(badDoc).post("/_/proxy/modules")
            .then().statusCode(400);

    final String docUnknownJar = "{" + LS
            + "  \"id\" : \"auth\"," + LS
            + "  \"name\" : \"auth\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"cmdlineStart\" : "
            + "\"java -Dport=%p -jar ../okapi-auth/target/okapi-unknown.jar\"," + LS
            // + "\"sleep %p\","+LS
            + "    \"cmdlineStop\" : null" + LS
            + "  }" + LS
            + "}";

    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(docUnknownJar).post("/_/deployment/modules")
            .then().log().all().statusCode(500);

    /*
    Assert.assertEquals("RamlReport{requestViolations=[], "
            + "responseViolations=[Body given but none defined on action(POST /_/proxy/modules) "
            + "response(500)], validationViolations=[]}",
            c.getLastReport().toString());
*/
    // post a module with missing "id":
    final String docMissingId = "{" + LS
            + "  \"name\" : \"auth\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"cmdlineStart\" : \"sleep %p\"," + LS
            + "    \"cmdlineStop\" : null" + LS
            + "  }" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(docMissingId).post("/_/deployment/modules")
            .then().log().all().statusCode(400);
    // Will not be according to RAML.. So no Assert on it.

    final String docAuthDeployment = "{" + LS
            + "  \"id\" : \"auth\"," + LS
            + "  \"name\" : \"auth\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"cmdlineStart\" : "
            + "\"java -Dport=%p -jar ../okapi-auth/target/okapi-auth-fat.jar\"," + LS
            + "    \"cmdlineStop\" : null" + LS
            + "  }" + LS
            + "}";

    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docAuthDeployment).post("/_/deployment/modules")
            .then().log().ifError().statusCode(201)
            .extract().response();
    // Assert.assertTrue(c.getLastReport().isEmpty());
    locationAuthDeployment = r.getHeader("Location");
    c = api.createRestAssured();
    String docAuthDiscovery = c.given().get(locationAuthDeployment)
            .then().statusCode(200).extract().body().asString(); 
    // Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docAuthDiscovery).post("/_/discovery/modules")
            .then().log().ifError().statusCode(201).extract().response();
    // Assert.assertTrue(c.getLastReport().isEmpty());
    String locationAuthDiscovery = r.getHeader("Location");
    Assert.assertEquals("bad location from discovery",
            "/_/discovery/modules/auth/localhost",
            locationAuthDiscovery);

    final String docAuthModule = "{" + LS
            + "  \"id\" : \"auth\"," + LS
            + "  \"name\" : \"auth\"," + LS
            + "  \"provides\" : [ {" + LS
            + "    \"id\" : \"auth\"," + LS
            + "    \"version\" : \"1.2.3\"" + LS
            + "  } ]," + LS
            + "  \"requires\" : null," + LS
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
            .body(docAuthModule).post("/_/proxy/modules").then().statusCode(201)
            .extract().response();

    final String docSampleDeployment = "{" + LS
            + "  \"id\" : \"sample-module\"," + LS
            + "  \"name\" : \"sample module\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"cmdlineStart\" : "
            + "\"java -Dport=%p -jar ../okapi-sample-module/target/okapi-sample-module-fat.jar\"," + LS
            + "    \"cmdlineStop\" : null" + LS
            + "  }" + LS
            + "}";
    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSampleDeployment).post("/_/deployment/modules")
            .then().log().ifError().statusCode(201)
            .extract().response();
    // Assert.assertTrue(c.getLastReport().isEmpty());
    locationSampleDeployment = r.getHeader("Location");

    c = api.createRestAssured();
    String docSampleDiscovery = c.given().get(locationSampleDeployment)
            .then().statusCode(200).extract().body().asString();
    // Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSampleDiscovery).post("/_/discovery/modules")
            .then().log().ifError().statusCode(201).extract().response();
    // Assert.assertTrue(c.getLastReport().isEmpty());
    final String locationSampleDiscovery = r.getHeader("Location");
    Assert.assertEquals("bad location from discovery",
            "/_/discovery/modules/sample-module/localhost",
            locationSampleDiscovery);
    
    final String docSampleModuleBadRequire = "{" + LS
            + "  \"id\" : \"sample-module\"," + LS
            + "  \"name\" : \"sample module\"," + LS
            + "  \"requires\" : [ {" + LS
            + "    \"id\" : \"SOMETHINGWEDONOTHAVE\"," + LS
            + "    \"version\" : \"1.2.3\"" + LS
            + "  } ]," + LS
            + "  \"routingEntries\" : [ ] " + LS
            + "}";

    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSampleModuleBadRequire).post("/_/proxy/modules").then().statusCode(400)
            .extract().response();

    final String docSampleModuleBadVersion = "{" + LS
            + "  \"id\" : \"sample-module\"," + LS
            + "  \"name\" : \"sample module\"," + LS
            + "  \"provides\" : [ {" + LS
            + "    \"id\" : \"sample\"," + LS
            + "    \"version\" : \"1.0.0\"" + LS
            + "  } ]," + LS
            + "  \"requires\" : [ {" + LS
            + "    \"id\" : \"auth\"," + LS
            + "    \"version\" : \"9.9.3\"" + LS  // We only have 1.2.3
            + "  } ]," + LS
            + "  \"routingEntries\" : [ ] " + LS
            + "}";

    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSampleModuleBadVersion).post("/_/proxy/modules").then().statusCode(400)
            .extract().response();

    final String docSampleModule = "{" + LS
            + "  \"id\" : \"sample-module\"," + LS
            + "  \"name\" : \"sample module\"," + LS
            + "  \"provides\" : [ {" + LS
            + "    \"id\" : \"sample\"," + LS
            + "    \"version\" : \"1.0.0\"" + LS
            + "  } ]," + LS
            + "  \"requires\" : [ {" + LS
            + "    \"id\" : \"auth\"," + LS
            + "    \"version\" : \"1.2.3\"" + LS
            + "  } ]," + LS
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
            .body(docSampleModule).post("/_/proxy/modules")
            .then().log().all().statusCode(201)
            .extract().response();
    Assert.assertTrue(c.getLastReport().isEmpty());
    final String locationSampleModule = r.getHeader("Location");

    c = api.createRestAssured();
    c.given().get("/_/proxy/modules/").then().statusCode(404);
    Assert.assertEquals("RamlReport{requestViolations=[Resource '/_/proxy/modules/' is not defined], "
            + "responseViolations=[], validationViolations=[]}",
            c.getLastReport().toString());

    c = api.createRestAssured();
    c.given().get("/_/proxy/modules").then().statusCode(200);
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given()
            .get(locationSampleModule).then().statusCode(200).body(equalTo(docSampleModule));
    Assert.assertTrue(c.getLastReport().isEmpty());

    final String docTenantRoskilde = "{" + LS
            + "  \"id\" : \"" + okapiTenant + "\"," + LS
            + "  \"name\" : \"" + okapiTenant + "\"," + LS
            + "  \"description\" : \"Roskilde bibliotek\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(docTenantRoskilde).post("/_/proxy/tenants/")
            .then().statusCode(404);
    Assert.assertEquals("RamlReport{requestViolations=[Resource '/_/proxy/tenants/' is not defined], "
            + "responseViolations=[], validationViolations=[]}",
            c.getLastReport().toString());

    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docTenantRoskilde).post("/_/proxy/tenants")
            .then().statusCode(201)
            .body(equalTo(docTenantRoskilde))
            .extract().response();
    Assert.assertTrue(c.getLastReport().isEmpty());
    final String locationTenantRoskilde = r.getHeader("Location");

    final String doc7 = "{" + LS
            + "  \"id\" : \"auth\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(doc7).post("/_/proxy/tenants/" + okapiTenant + "/modules/")
            .then().statusCode(404);

    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(doc7).post("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(doc7));
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules/")
            .then().statusCode(404);

    c = api.createRestAssured();
    final String exp1 = "[ {" + LS
            + "  \"id\" : \"auth\"" + LS
            + "} ]";
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200).body(equalTo(exp1));
    Assert.assertTrue(c.getLastReport().isEmpty());

    final String exp2 = "{" + LS
            + "  \"id\" : \"auth\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules/auth")
            .then().statusCode(200).body(equalTo(exp2));
    Assert.assertTrue(c.getLastReport().isEmpty());

    final String doc8 = "{" + LS
            + "  \"id\" : \"sample-module\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(doc8).post("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(doc8));
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules/")
            .then().statusCode(404);

    c = api.createRestAssured();
    final String exp3 = "[ {" + LS
            + "  \"id\" : \"auth\"" + LS
            + "}, {" + LS
            + "  \"id\" : \"sample-module\"" + LS
            + "} ]";
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200).body(equalTo(exp3));
    Assert.assertTrue(c.getLastReport().isEmpty());

    given().get("/_/test/reloadtenant/" + okapiTenant)
            .then().statusCode(204);

    c = api.createRestAssured();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules")
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
            .body(doc9).put("/_/proxy/tenants/" + okapiTenant)
            .then().statusCode(200)
            .body(equalTo(doc9));
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules")
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

    // 2nd sample module.. We only create it in discovery and give it same URL as
    // for sample-module (first one)
    c = api.createRestAssured();
    final String docSample2Deployment = "{" + LS
            + "  \"id\" : \"sample-module2\"," + LS
            + "  \"name\" : \"second sample module\"," + LS
            + "  \"nodeId\" : \"localhost\"," + LS
            + "  \"url\" : \"http://localhost:9132\"" + LS
            + "}";
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSample2Deployment).post("/_/discovery/modules")
            .then().log().ifError().statusCode(201).extract().response();
    final String locationSample2Discovery = r.header("Location");

    final String docSample2Module = "{" + LS
            + "  \"id\" : \"sample-module2\"," + LS
            + "  \"name\" : \"another-sample-module2\"," + LS
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
            .body(docSample2Module).post("/_/proxy/modules").then().statusCode(201)
            .extract().response();
    Assert.assertTrue(c.getLastReport().isEmpty());
    final String locationSample2Module = r.getHeader("Location");

    final String doc13 = "{" + LS
            + "  \"id\" : \"sample-module2\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(doc13).post("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(doc13));
    Assert.assertTrue(c.getLastReport().isEmpty());

    // 3rd sample module.. We only create it in discovery and give it same URL as
    // for sample-module (first one)
    c = api.createRestAssured();
    final String docSample3Deployment = "{" + LS
            + "  \"id\" : \"sample-module3\"," + LS
            + "  \"name\" : \"second sample module\"," + LS
            + "  \"url\" : \"http://localhost:9132\"" + LS
            + "}";
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSample3Deployment).post("/_/discovery/modules")
            .then().log().ifError().statusCode(201).extract().response();

    final String docSample3Module = "{" + LS
            + "  \"id\" : \"sample-module3\"," + LS
            + "  \"name\" : \"sample-module3\"," + LS
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
            .body(docSample3Module).post("/_/proxy/modules").then().statusCode(201)
            .extract().response();
    Assert.assertTrue(c.getLastReport().isEmpty());
    final String locationSample3Module = r.getHeader("Location");

    final String doc15 = "{" + LS
            + "  \"id\" : \"sample-module3\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(doc15).post("/_/proxy/tenants/" + okapiTenant + "/modules")
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
    c.given().get(locationTenantRoskilde + "/modules")
            .then().statusCode(200)
            .body(equalTo(exp4));
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().delete(locationTenantRoskilde + "/modules/sample-module3")
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
    c.given().get(locationTenantRoskilde + "/modules")
            .then().statusCode(200)
            .body(equalTo(exp5));
    Assert.assertTrue(c.getLastReport().isEmpty());

    // make sample 2 disappear from discovery!
    System.out.println("About to delete " + locationSample2Discovery);
    c = api.createRestAssured();
    c.given().delete(locationSample2Discovery)
            .then().statusCode(204);

    given().header("X-Okapi-Tenant", okapiTenant)
            .header("X-Okapi-Token", okapiToken)
            .header("Content-Type", "text/xml")
            .get("/sample")
            .then().statusCode(404); // because sample2 was removed

    c = api.createRestAssured();
    c.given().delete(locationTenantRoskilde)
            .then().statusCode(204);
    Assert.assertTrue(c.getLastReport().isEmpty());

    async.complete();
  }

  @Test
  public void testDeployment(TestContext context) {
    async = context.async();

    Response r;

    given().get("/_/deployment/modules")
           .then().statusCode(200)
           .body(equalTo("[ ]"));

    given().get("/_/deployment/modules/not_found")
           .then().statusCode(404);

    final String doc1 = "{" + LS
            + "  \"id\" : \"sample-module5\"," + LS
            + "  \"name\" : \"sample module\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"cmdlineStart\" : "
            + "\"java -Dport=%p -jar ../okapi-sample-module/target/okapi-sample-module-fat.jar\"," + LS
            + "    \"cmdlineStop\" : null" + LS
            + "  }" + LS
            + "}";
    // extra slash !
    given().header("Content-Type", "application/json")
           .body(doc1).post("/_/deployment/modules/")
           .then().statusCode(404);

    final String doc2 = "{" + LS
            + "  \"id\" : \"sample-module5\"," + LS
            + "  \"name\" : \"sample module\"," + LS
            + "  \"nodeId\" : \"localhost\"," + LS
            + "  \"url\" : \"http://localhost:9131\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"cmdlineStart\" : "
            + "\"java -Dport=%p -jar ../okapi-sample-module/target/okapi-sample-module-fat.jar\"," + LS
            + "    \"cmdlineStop\" : null" + LS
            + "  }" + LS
            + "}";

    r = given().header("Content-Type", "application/json")
           .body(doc1).post("/_/deployment/modules")
           .then().statusCode(201)
           .body(equalTo(doc2))
           .extract().response();
    locationSample5Deployment = r.getHeader("Location");

    given().get(locationSample5Deployment)
         .then().statusCode(200)
         .body(equalTo(doc2));

    given().get("/_/deployment/modules")
         .then().statusCode(200)
         .body(equalTo("[ " + doc2 + " ]"));

    final String doc3 = "{" + LS
          + "  \"id\" : \"sample-module5\"," + LS
          + "  \"name\" : \"sample module3\"," + LS
          + "  \"descriptor\" : {" + LS
          + "    \"cmdlineStart\" : "
          + "\"java -Dport=%p -jar ../okapi-sample-module/target/okapi-sample-module-fat.jar\"," + LS
          + "    \"cmdlineStop\" : null" + LS
          + "  }" + LS
          + "}";

    final String doc4 = "{" + LS
            + "  \"id\" : \"sample-module5\"," + LS
            + "  \"name\" : \"sample module3\"," + LS
            + "  \"nodeId\" : \"localhost\"," + LS
            + "  \"url\" : \"http://localhost:9132\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"cmdlineStart\" : "
            + "\"java -Dport=%p -jar ../okapi-sample-module/target/okapi-sample-module-fat.jar\"," + LS
            + "    \"cmdlineStop\" : null" + LS
            + "  }" + LS
            + "}";

    given().header("Content-Type", "application/json")
           .body(doc3).put(locationSample5Deployment)
           .then().statusCode(200)
           .body(equalTo(doc4));

    given().get(locationSample5Deployment)
         .then().statusCode(200)
         .body(equalTo(doc4));

    given().get("/_/deployment/modules")
         .then().statusCode(200)
         .body(equalTo("[ " + doc4 + " ]"));

    ValidatableResponse then = given().header("Content-Type", "application/json")
            .body(doc4).post("/_/discovery/modules")
            .then();
    then.statusCode(201);
    then.body(equalTo(doc4));
    final String locationDiscovery1 = then.extract().header("Location");
    given().header("Content-Type", "application/json")
            .body(doc4).post("/_/discovery/modules")
            .then().statusCode(400);

    given().get(locationDiscovery1)
         .then().statusCode(200)
         .body(equalTo(doc4));

    given().get("/_/discovery/modules/sample-module5")
         .then().statusCode(200)
         .body(equalTo("[ " + doc4 + " ]"));

    given().get("/_/discovery/modules")
         .then().statusCode(500);
    // TODO - Can not list them yet

    given().delete(locationDiscovery1)
      .then().statusCode(204);

    given().delete(locationDiscovery1)
      .then().statusCode(404);

    given().get("/_/discovery/modules/sample-module5")
         .then().statusCode(404);

    given().delete(locationSample5Deployment).then().statusCode(204);
    locationSample5Deployment = null;

    given().get("/_/deployment/modules")
           .then().statusCode(200)
           .body(equalTo("[ ]"));

    async.complete();
  }
}
