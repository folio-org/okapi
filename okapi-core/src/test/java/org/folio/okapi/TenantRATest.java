package org.folio.okapi;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured3.RestAssuredClient;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.managers.TenantManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@java.lang.SuppressWarnings({"squid:S1192"})
@RunWith(VertxUnitRunner.class)
public class TenantRATest {

  private final Logger logger = OkapiLogger.get();
  private static int PORT = 9230;

  private Vertx vertx;
  private TenantManager tenantManager;
  private static final String LS = System.lineSeparator();

  private static RamlDefinition api;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml");
    RestAssured.port = PORT;
  }

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    var mainVerticle = new MainVerticle();

    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(new JsonObject().put("port", Integer.toString(PORT)));
    vertx.deployVerticle(mainVerticle, opt, context.asyncAssertSuccess(x -> {
      tenantManager = mainVerticle.getTenantManager();
    }));
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void test1() {
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
      .then().statusCode(400);
     Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());

    String badId = "{" + LS
      + "  \"id\" : \"Bad Id with Spaces and Specials: ?%!\"," + LS
      + "  \"name\" : \"roskilde\"," + LS
      + "  \"description\" : \"Roskilde bibliotek\"" + LS
      + "}";
    c = api.createRestAssured3();
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

    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json").body(doc)
      .post("/_/proxy/tenants").then().statusCode(201)
      .body(equalTo(doc)).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    String location = r.getHeader("Location");

    // post again, fail because of duplicate
    c = api.createRestAssured3();
    c.given()
            .header("Content-Type", "application/json").body(doc)
            .post("/_/proxy/tenants").then().statusCode(400);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given()
            .header("Content-Type", "application/json")
            .get("/_/proxy/tenants/roskilde/modules/foo").then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get(location).then().statusCode(200).body(equalTo(doc));
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    c = api.createRestAssured3();
    c.given().get(location + "none").then().statusCode(404);
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
             c.getLastReport().isEmpty());

    final String tenantList = "[ " + doc + ", " + superTenantDoc + " ]";
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

    // server-side generated Id
    String doc6 = "{" + LS
      + "  \"name\" : \"Ringsted\"," + LS
      + "  \"description\" : \"Ringsted description\"" + LS
      + "}";
    c = api.createRestAssured3();
    r = c.given()
      .header("Content-Type", "application/json").body(doc6)
      .post("/_/proxy/tenants").then().statusCode(201).extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    location3 = r.getHeader("Location");

    c = api.createRestAssured3();
    c.given().delete(location3).then().statusCode(204);
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

  @Test
  public void rename() {
    var c = api.createRestAssured3();
    c.given()
    .header("Content-Type", "application/json")
    .body("""
          { "id": "foo",
            "name": "The foo lib",
            "description": "foo and further"
          }
          """)
    .post("/_/proxy/tenants")
    .then().statusCode(201);
    assertThat(c.getLastReport().toString(), c.getLastReport().isEmpty(), is(true));

    // can change name and description
    api.createRestAssured3()
    .given()
    .header("Content-Type", "application/json")
    .body("""
        { "id": "foo",
          "name": "foo & bar",
          "description": "foo raising the bar"
        }
        """)
    .put("/_/proxy/tenants/foo")
    .then()
    .statusCode(200)
    .body("name", is("foo & bar"));
    assertThat(c.getLastReport().toString(), c.getLastReport().isEmpty(), is(true));

    var bar = """
        { "id": "bar"
        }
        """;

    // cannot change id from foo to bar
    api.createRestAssured3()
    .given()
    .header("Content-Type", "application/json")
    .body(bar)
    .put("/_/proxy/tenants/foo")
    .then().statusCode(400)
    .body(is("The tenant id must not be changed, old='foo', new='bar'"));
    assertThat(c.getLastReport().toString(), c.getLastReport().isEmpty(), is(true));

    // but can create a separate bar tenant
    api.createRestAssured3()
    .given()
    .header("Content-Type", "application/json")
    .body(bar)
    .post("/_/proxy/tenants")
    .then().statusCode(201);
    assertThat(c.getLastReport().toString(), c.getLastReport().isEmpty(), is(true));
  }

  @Test
  public void legacy() {
    api.createRestAssured3()
    .given()
    .header("Content-Type", "application/json")
    .body("""
          { "id": "lib_bee"
          }
          """)
    .post("/_/proxy/tenants")
    .then().statusCode(400)
    .body(is("Tenant id must not contain underscore: lib_bee"));

    // create a legacy tenant id with underscore
    tenantManager.insert(new Tenant(new TenantDescriptor("lib_bee", "lib_bee")));

    // change name
    api.createRestAssured3()
    .given()
    .header("Content-Type", "application/json")
    .body("""
          { "id": "lib_bee",
            "name": "Bee the honey maker"
          }
          """)
    .put("/_/proxy/tenants/lib_bee")
    .then().statusCode(200);

    api.createRestAssured3()
    .given()
    .delete("/_/proxy/tenants/lib_bee")
    .then().statusCode(204);
  }
}
