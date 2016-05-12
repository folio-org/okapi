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
            + "  \"srvcId\" : \"auth\"," + LS
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
            .then()
            .statusCode(500);

    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    final String docAuthDeployment = "{" + LS
            + "  \"srvcId\" : \"auth\"," + LS
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
            .then()
            .statusCode(201)
            .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    locationAuthDeployment = r.getHeader("Location");
    c = api.createRestAssured();
    String docAuthDiscovery = c.given().get(locationAuthDeployment)
            .then().statusCode(200).extract().body().asString(); 
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docAuthDiscovery).post("/_/discovery/modules")
            .then()
            //.log().ifError()
            .statusCode(201).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    String locationAuthDiscovery = r.getHeader("Location");
    Assert.assertEquals("bad location from discovery",
            "/_/discovery/modules/auth/localhost-9131",
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
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    final String docSampleDeployment = "{" + LS
            + "  \"srvcId\" : \"sample-module\"," + LS
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
            .then()
            .statusCode(201)
            .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    locationSampleDeployment = r.getHeader("Location");

    c = api.createRestAssured();
    String docSampleDiscovery = c.given().get(locationSampleDeployment)
            .then().statusCode(200).extract().body().asString();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSampleDiscovery).post("/_/discovery/modules")
            .then()
            //.log().ifError()
            .statusCode(201).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    final String locationSampleDiscovery = r.getHeader("Location");
    Assert.assertEquals("bad location from discovery",
            "/_/discovery/modules/sample-module/localhost-9132",
            locationSampleDiscovery);
    
    final String docSampleModuleBadRequire = "{" + LS
            + "  \"srvcId\" : \"sample-module\"," + LS
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
            + "  \"srvcId\" : \"sample-module\"," + LS
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
            .then()
            //.log().all()
            .statusCode(201)
            .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    final String locationSampleModule = r.getHeader("Location");

    c = api.createRestAssured();
    c.given().get("/_/proxy/modules/").then().statusCode(404);

    c = api.createRestAssured();
    c.given().get("/_/proxy/modules").then().statusCode(200);
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given()
            .get(locationSampleModule).then().statusCode(200).body(equalTo(docSampleModule));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

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
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    final String locationTenantRoskilde = r.getHeader("Location");
    
    // Try to enable sample without the auth that it requires
    final String docEnableWithoutDep = "{" + LS
            + "  \"id\" : \"sample-module\"" + LS
            + "}";
    c.given()
            .header("Content-Type", "application/json")
            .body(docEnableWithoutDep).post("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(400);

    final String docEnableAuth = "{" + LS
            + "  \"id\" : \"auth\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(docEnableAuth).post("/_/proxy/tenants/" + okapiTenant + "/modules/")
            .then().statusCode(404);  // trailing slash is no good

    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(docEnableAuth).post("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(docEnableAuth));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules/")
            .then().statusCode(404);

    c = api.createRestAssured();
    final String exp1 = "[ {" + LS
            + "  \"id\" : \"auth\"" + LS
            + "} ]";
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200).body(equalTo(exp1));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    final String expAuthEnabled = "{" + LS
            + "  \"id\" : \"auth\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules/auth")
            .then().statusCode(200).body(equalTo(expAuthEnabled));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    final String docEnableSample = "{" + LS
            + "  \"id\" : \"sample-module\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(docEnableSample).post("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(docEnableSample));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules/")
            .then().statusCode(404); // trailing slash

    c = api.createRestAssured();
    final String expEnabledBoth = "[ {" + LS
            + "  \"id\" : \"auth\"" + LS
            + "}, {" + LS
            + "  \"id\" : \"sample-module\"" + LS
            + "} ]";
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200).body(equalTo(expEnabledBoth));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    String docTenant = "{" + LS
            + "  \"id\" : \"" + okapiTenant + "\"," + LS
            + "  \"name\" : \"Roskilde-library\"," + LS
            + "  \"description\" : \"Roskilde bibliotek\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(docTenant).put("/_/proxy/tenants/" + okapiTenant)
            .then().statusCode(200)
            .body(equalTo(docTenant));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200).body(equalTo(expEnabledBoth));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    given().get("/sample")
            .then().statusCode(403);

    given().header("X-Okapi-Tenant", okapiTenant).get("/q")
            .then().statusCode(404);

    given().header("X-Okapi-Tenant", okapiTenant).get("/sample")
            .then().statusCode(401);

    final String docWrongLogin = "{" + LS
            + "  \"tenant\" : \"t1\"," + LS
            + "  \"username\" : \"peter\"," + LS
            + "  \"password\" : \"peter37\"" + LS
            + "}";

    given().header("Content-Type", "application/json").body(docWrongLogin)
            .header("X-Okapi-Tenant", okapiTenant).post("/login")
            .then().statusCode(401);

    final String docLogin = "{" + LS
            + "  \"tenant\" : \"t1\"," + LS
            + "  \"username\" : \"peter\"," + LS
            + "  \"password\" : \"peter-password\"" + LS
            + "}";
    okapiToken = given().header("Content-Type", "application/json").body(docLogin)
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
            + "  \"instId\" : \"sample2-inst\"," + LS
            + "  \"srvcId\" : \"sample-module2\"," + LS
            + "  \"name\" : \"second sample module\"," + LS
            + "  \"nodeId\" : \"localhost\"," + LS
            + "  \"url\" : \"http://localhost:9132\"" + LS
            + "}";
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSample2Deployment).post("/_/discovery/modules")
            .then()
            .statusCode(201).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
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
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    final String locationSample2Module = r.getHeader("Location");

    final String docEnableSample2 = "{" + LS
            + "  \"id\" : \"sample-module2\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(docEnableSample2).post("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(docEnableSample2));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    // 3rd sample module.. We only create it in discovery and give it same URL as
    // for sample-module (first one)
    c = api.createRestAssured();
    final String docSample3Deployment = "{" + LS
            + "  \"instId\" : \"sample3-instance\"," + LS
            + "  \"srvcId\" : \"sample-module3\"," + LS
            + "  \"name\" : \"second sample module\"," + LS
            + "  \"url\" : \"http://localhost:9132\"" + LS
            + "}";
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSample3Deployment).post("/_/discovery/modules")
            .then()
            .statusCode(201).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

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
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    final String locationSample3Module = r.getHeader("Location");

    final String docEnableSample3 = "{" + LS
            + "  \"id\" : \"sample-module3\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .body(docEnableSample3).post("/_/proxy/tenants/" + okapiTenant + "/modules")
            .then().statusCode(200)
            .body(equalTo(docEnableSample3));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

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
    final String exp4Modules = "[ {" + LS
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
            .body(equalTo(exp4Modules));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().delete(locationTenantRoskilde + "/modules/sample-module3")
            .then().statusCode(204);
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    final String exp3Modules = "[ {" + LS
            + "  \"id\" : \"auth\"" + LS
            + "}, {" + LS
            + "  \"id\" : \"sample-module\"" + LS
            + "}, {" + LS
            + "  \"id\" : \"sample-module2\"" + LS
            + "} ]";
    c.given().get(locationTenantRoskilde + "/modules")
            .then().statusCode(200)
            .body(equalTo(exp3Modules));
    Assert.assertTrue(c.getLastReport().isEmpty());

    // make sample 2 disappear from discovery!
    c = api.createRestAssured();
    c.given().delete(locationSample2Discovery)
            .then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

    given().header("X-Okapi-Tenant", okapiTenant)
            .header("X-Okapi-Token", okapiToken)
            .header("Content-Type", "text/xml")
            .get("/sample")
            .then().statusCode(404); // because sample2 was removed

    c = api.createRestAssured();
    c.given().delete(locationTenantRoskilde)
            .then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());

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
            + "  \"srvcId\" : \"sample-module5\"," + LS
            + "  \"name\" : \"sample module\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"cmdlineStart\" : "
            + "\"java -Dport=%p -jar ../okapi-sample-module/target/okapi-sample-module-fat.jar\"," + LS
            + "    \"cmdlineStop\" : null" + LS
            + "  }" + LS
            + "}";

    given().header("Content-Type", "application/json")
           .body(doc1).post("/_/deployment/modules/")     // extra slash !
           .then().statusCode(404);

    final String doc2 = "{" + LS
            + "  \"instId\" : \"localhost-9131\"," + LS
            + "  \"srvcId\" : \"sample-module5\"," + LS
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

    ValidatableResponse then = given().header("Content-Type", "application/json")
            .body(doc2).post("/_/discovery/modules")
            .then();
    then.statusCode(201);
    then.body(equalTo(doc2));
    final String locationDiscovery1 = then.extract().header("Location");
    given().header("Content-Type", "application/json")
            .body(doc2).post("/_/discovery/modules")
            .then().statusCode(400);

    given().get(locationDiscovery1)
         .then().statusCode(200)
         .body(equalTo(doc2));

    given().get("/_/discovery/modules/sample-module5")
         .then().statusCode(200)
         .body(equalTo("[ " + doc2 + " ]"));

    given().get("/_/discovery/modules")
         .then().statusCode(200)
         .log().ifError()
         .body(equalTo("[ " + doc2 + " ]"));

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
