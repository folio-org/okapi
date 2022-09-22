package org.folio.okapi;

import io.restassured.http.ContentType;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured3.RestAssuredClient;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;
import static org.hamcrest.Matchers.*;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

import java.util.List;

@java.lang.SuppressWarnings({"squid:S1192"})
@RunWith(VertxUnitRunner.class)
public class TenantRATest {

  private final Logger logger = OkapiLogger.get();
  private final int port = 9230;

  private Vertx vertx;
  private static final String LS = System.lineSeparator();

  private static RamlDefinition api;

  @BeforeClass
  public static void setUpBeforeClass() {
    api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml");
  }

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();

    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(new JsonObject().put("port", Integer.toString(port)));
    vertx.deployVerticle(MainVerticle.class.getName(), opt, context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void test1() {
    RestAssured.port = port;

    RestAssuredClient c;
    Response r;

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants//modules")
      .then().statusCode(404);

    String superTenantDoc = "{" + LS
      + "  \"id\" : \"supertenant\"," + LS
      + "  \"name\" : \"supertenant\"," + LS
      + "  \"description\" : \"Okapi built-in super tenant\"" + LS
      + "}";
    String superTenantList = "[ " + superTenantDoc + " ]";

    c = api.createRestAssured3();
    c.given()
      .get("/_/proxy/tenants")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(equalTo(superTenantList));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

     // Check that we can not delete the superTenant
    c = api.createRestAssured3();
    c.given().delete("/_/proxy/tenants/supertenant")
        .then().statusCode(400)
        .contentType(ContentType.TEXT)
        .body(is("Can not delete the superTenant supertenant"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    // omitted identifier
    c = api.createRestAssured3();
    c.given()
        .contentType(ContentType.JSON)
        .body(new JsonObject().encode())
        .post("/_/proxy/tenants").then().statusCode(400)
        .contentType(ContentType.TEXT)
        .body(is("Tenant id required"));

    for (String id : List.of("Bad id", "a_b", "a0123456789012345678901234567890")) {
      c = api.createRestAssured3();
      c.given()
          .contentType(ContentType.JSON)
          .body(new JsonObject().put("id", id).encode())
          .post("/_/proxy/tenants")
          .then().statusCode(400)
          .contentType(ContentType.TEXT)
          .body(is("Tenant id " + id + " invalid. Must match ^[a-z][a-z0-9]{0,30}$"));
      Assert.assertTrue("raml: " + c.getLastReport().toString(),
          c.getLastReport().isEmpty());
    }

    JsonObject tenantObject = new JsonObject()
        .put("id", "roskilde")
        .put("name", "Roskilde")
        .put("description", "Roskilde bibliotek");

    c = api.createRestAssured3();
    r = c.given()
        .contentType(ContentType.JSON)
        .body(tenantObject.encode())
      .post("/_/proxy/tenants").then().statusCode(201)
      .body(equalTo(tenantObject.encodePrettily())).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    String location = r.getHeader("Location");

    // post again, fail because of duplicate
    c = api.createRestAssured3();
    c.given()
        .contentType(ContentType.JSON)
        .body(tenantObject.encode())
        .post("/_/proxy/tenants")
        .then().statusCode(400)
        .contentType(ContentType.TEXT)
        .body(is("Duplicate tenant id roskilde"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
        .header("Content-Type", "application/json")
        .get("/_/proxy/tenants/roskilde/modules/foo").then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
        c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get(location).then().statusCode(200).body(equalTo(tenantObject.encodePrettily()));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get(location + "none").then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    final String tenantList = "[ " + tenantObject.encodePrettily() + ", " + superTenantDoc + " ]";
    c = api.createRestAssured3();
    c.given()
      .get("/_/proxy/tenants")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(equalTo(tenantList));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().delete(location).then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants").then().statusCode(200).body(equalTo(superTenantList));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    String doc3BadJson = "{" + LS
      + "  \"id\" : \"roskildedk\"," + LS;
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json").body(doc3BadJson)
      .post("/_/proxy/tenants").then().statusCode(400);

    String doc3 = "{" + LS
      + "  \"id\" : \"roskildedk\"," + LS
      + "  \"name\" : \"roskilde\"," + LS
      + "  \"description\" : \"Roskilde bibliotek\"" + LS
      + "}";
    c = api.createRestAssured3();
    Response r3 = c.given()
            .header("Content-Type", "application/json").body(doc3)
            .post("/_/proxy/tenants").then().statusCode(201)
            .body(equalTo(doc3)).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());
    String location3 = r3.getHeader("Location");
    logger.debug("location3 = " + location3);

    final String tenantList3 = "[ " + doc3 + ", " + superTenantDoc + " ]";
    c = api.createRestAssured3();
    c.given().get("/_/proxy/tenants").then().statusCode(200).body(equalTo(tenantList3));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    String doc4BadJson = "{" + LS
      + "  \"id\" : \"roskildedk\"," + LS;
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json").body(doc4BadJson)
      .put(location3).then().statusCode(400);

    String doc4badId = "{" + LS
      + "  \"id\" : \"roskildedk2\"," + LS
      + "  \"name\" : \"Roskildes Real Name\"," + LS
      + "  \"description\" : \"Roskilde bibliotek with a better description\"" + LS
      + "}";
    c = api.createRestAssured3();
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
    c = api.createRestAssured3();
    c.given()
            .header("Content-Type", "application/json").body(doc4)
            .put(location3).then().statusCode(200).body(equalTo(doc4));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().delete(location3).then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().delete(location3 + "notThere").then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    String doc5 = "{" + LS
            + "  \"id\" : \"roskildedk\"," + LS
            + "  \"name\" : \"Roskildes Real Name\"," + LS
            + "  \"description\" : \"Roskilde bibliotek with a better description\"" + LS
            + "}";
    c = api.createRestAssured3();
    c.given()
            .header("Content-Type", "application/json").body(doc5)
            .put(location3).then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().delete(location3).then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    String superdoc2 = "{" + LS
      + "  \"id\" : \"supertenant\"," + LS
      + "  \"name\" : \"The Real Super Tenant\"," + LS
      + "  \"description\" : \"With a better description\"" + LS
      + "}";
    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json").body(superdoc2)
      .put("/_/proxy/tenants/supertenant").then().statusCode(200);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    c = api.createRestAssured3();
    c.given()
      .get("/_/proxy/tenants")
      .then().statusCode(200)
      .body(equalTo("[ " + superdoc2 + " ]"));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    // no name
    String doc7 = "{" + LS
      + "  \"id\" : \"ringsted\"" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json").body(doc7)
      .post("/_/proxy/tenants").then().statusCode(201).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    location3 = r.getHeader("Location");

    c = api.createRestAssured3();
    r = c.given()
      .get("/_/proxy/tenants/ringsted").then().statusCode(200).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    Assert.assertEquals(doc7, r.body().asString());

    String doc8 = "{" + LS
      + "  \"id\" : \"ringsted\"," + LS
      + "  \"name\" : \"Ringsted\"" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json").body(doc8)
      .put(location3).then().statusCode(200).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    JsonObject j = new JsonObject(r.body().asString());
    Assert.assertEquals("Ringsted", j.getString("name"));
    Assert.assertEquals("ringsted", j.getString("id"));

    c = api.createRestAssured3();
    c.given().delete(location3).then().statusCode(204);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
  }
}
