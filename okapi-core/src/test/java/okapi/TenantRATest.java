package okapi;

import org.folio.okapi.MainVerticle;
import com.jayway.restassured.RestAssured;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.jayway.restassured.response.Response;
import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured.RestAssuredClient;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.okapi.common.OkapiLogger;
import static org.hamcrest.Matchers.*;
import org.junit.Assert;
import org.junit.runner.RunWith;

@java.lang.SuppressWarnings({"squid:S1192"})
@RunWith(VertxUnitRunner.class)
public class TenantRATest {

  private final Logger logger = OkapiLogger.get();
  private int port = 9230;

  private Vertx vertx;
  private static final String LS = System.lineSeparator();

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();

    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(new JsonObject().put("port", Integer.toString(port)));
    vertx.deployVerticle(MainVerticle.class.getName(), opt, context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
    Async async = context.async();
    vertx.close(x -> {
      async.complete();
    });
  }

  @Test
  public void test1() {
    RestAssured.port = port;

    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
            .assumingBaseUri("https://okapi.cloud");

    RestAssuredClient c;

    c = api.createRestAssured();
    c.given().get("/_/proxy/tenants//modules")
      .then().statusCode(404);

    String superTenantDoc = "{" + LS
      + "  \"id\" : \"supertenant\"," + LS
      + "  \"name\" : \"supertenant\"," + LS
      + "  \"description\" : \"Okapi built-in super tenant\"" + LS
      + "}";
    String superTenantList = "[ " + superTenantDoc + " ]";

    c = api.createRestAssured();
    c.given()
      .get("/_/proxy/tenants")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(equalTo(superTenantList));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

     // Check that we can not delete the superTenant
    c = api.createRestAssured();
    c.given().delete("/_/proxy/tenants/supertenant")
      .then().statusCode(400);
     Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    String badId = "{" + LS
      + "  \"id\" : \"Bad Id with Spaces and Specials: ?%!\"," + LS
      + "  \"name\" : \"roskilde\"," + LS
      + "  \"description\" : \"Roskilde bibliotek\"" + LS
      + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json").body(badId)
            .post("/_/proxy/tenants").then().statusCode(400);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    String doc = "{" + LS
      + "  \"id\" : \"roskilde\"," + LS
      + "  \"name\" : \"roskilde\"," + LS
      + "  \"description\" : \"Roskilde bibliotek\"" + LS
      + "}";

    c = api.createRestAssured();
    Response r = c.given()
            .header("Content-Type", "application/json").body(doc)
            .post("/_/proxy/tenants").then().statusCode(201)
            .body(equalTo(doc)).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());
    String location = r.getHeader("Location");

    // post again, fail because of duplicate
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json").body(doc)
            .post("/_/proxy/tenants").then().statusCode(400);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json")
            .get("/_/proxy/tenants/roskilde/modules/foo").then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get(location).then().statusCode(200).body(equalTo(doc));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get(location + "none").then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    final String tenantList = "[ " + doc + ", " + superTenantDoc + " ]";
    c = api.createRestAssured();
    c.given()
      .get("/_/proxy/tenants")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(equalTo(tenantList));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().delete(location).then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/proxy/tenants").then().statusCode(200).body(equalTo(superTenantList));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    String doc3BadJson = "{" + LS
      + "  \"id\" : \"roskildedk\"," + LS;
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json").body(doc3BadJson)
      .post("/_/proxy/tenants").then().statusCode(400);

    String doc3 = "{" + LS
      + "  \"id\" : \"roskildedk\"," + LS
      + "  \"name\" : \"roskilde\"," + LS
      + "  \"description\" : \"Roskilde bibliotek\"" + LS
      + "}";
    c = api.createRestAssured();
    Response r3 = c.given()
            .header("Content-Type", "application/json").body(doc3)
            .post("/_/proxy/tenants").then().statusCode(201)
            .body(equalTo(doc3)).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());
    String location3 = r3.getHeader("Location");
    logger.debug("location3 = " + location3);

    final String tenantList3 = "[ " + doc3 + ", " + superTenantDoc + " ]";
    c = api.createRestAssured();
    c.given().get("/_/proxy/tenants").then().statusCode(200).body(equalTo(tenantList3));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    String doc4BadJson = "{" + LS
      + "  \"id\" : \"roskildedk\"," + LS;
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json").body(doc4BadJson)
      .put(location3).then().statusCode(400);

    String doc4badId = "{" + LS
      + "  \"id\" : \"roskildedk2\"," + LS
      + "  \"name\" : \"Roskildes Real Name\"," + LS
      + "  \"description\" : \"Roskilde bibliotek with a better description\"" + LS
      + "}";
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json").body(doc4badId)
      .put(location3).then().statusCode(400);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    String doc4 = "{" + LS
            + "  \"id\" : \"roskildedk\"," + LS
            + "  \"name\" : \"Roskildes Real Name\"," + LS
            + "  \"description\" : \"Roskilde bibliotek with a better description\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json").body(doc4)
            .put(location3).then().statusCode(200).body(equalTo(doc4));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().delete(location3).then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().delete(location3 + "notThere").then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    String doc5 = "{" + LS
            + "  \"id\" : \"roskildedk\"," + LS
            + "  \"name\" : \"Roskildes Real Name\"," + LS
            + "  \"description\" : \"Roskilde bibliotek with a better description\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json").body(doc5)
            .put(location3).then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().delete(location3).then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    String superdoc2 = "{" + LS
      + "  \"id\" : \"supertenant\"," + LS
      + "  \"name\" : \"The Real Super Tenant\"," + LS
      + "  \"description\" : \"With a better description\"" + LS
      + "}";
    c = api.createRestAssured();
    c.given()
      .header("Content-Type", "application/json").body(superdoc2)
      .put("/_/proxy/tenants/supertenant").then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    c = api.createRestAssured();
    c.given()
      .get("/_/proxy/tenants")
      .then().statusCode(200)
      .body(equalTo("[ " + superdoc2 + " ]"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // server-side generated Id
    String doc6 = "{" + LS
      + "  \"name\" : \"Ringsted\"," + LS
      + "  \"description\" : \"Ringsted description\"" + LS
      + "}";
    c = api.createRestAssured();
    r = c.given()
      .header("Content-Type", "application/json").body(doc6)
      .post("/_/proxy/tenants").then().statusCode(201).extract().response();
    // Not valid according to RAML at the moment
    location3 = r.getHeader("Location");

    c = api.createRestAssured();
    c.given().delete(location3).then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
  }
}
