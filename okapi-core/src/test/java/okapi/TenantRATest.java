/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi;

import com.jayway.restassured.RestAssured;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static com.jayway.restassured.RestAssured.*;
import com.jayway.restassured.response.Response;
import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured.RestAssuredClient;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import static org.hamcrest.Matchers.*;
import org.junit.Assert;
import org.junit.runner.RunWith;



@RunWith(VertxUnitRunner.class)
public class TenantRATest {
  private final Logger logger = LoggerFactory.getLogger("okapi.DeployModuleIntegration");

  Vertx vertx;
  private static final String LS = System.lineSeparator();

  public TenantRATest() {
  }

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();

    DeploymentOptions opt = new DeploymentOptions()
            .setConfig(new JsonObject().put("storage", "inmemory"));
    vertx.deployVerticle(MainVerticle.class.getName(), opt,  context.asyncAssertSuccess());
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
    int port = Integer.parseInt(System.getProperty("port", "9130"));

    RestAssured.port = port;

    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
            .assumingBaseUri("https://okapi.io");

    RestAssuredClient c;

    c = api.createRestAssured();
    c.given().get("/_/tenants").then().statusCode(200).body(equalTo("[ ]"));
    if (!c.getLastReport().isEmpty()) {
      logger.info("0:" + c.getLastReport().toString());
    }
    Assert.assertTrue(c.getLastReport().isEmpty());

    String badId = "{"+LS
            + "  \"id\" : \"Bad Id with Spaces and Specials: ?%!\","+LS
            + "  \"name\" : \"roskilde\","+LS
            + "  \"description\" : \"Roskilde bibliotek\""+LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json").body(badId)
            .post("/_/tenants").then().statusCode(400);
    Assert.assertEquals(
            "RamlReport{requestViolations=[], responseViolations="
            + "[Body given but none defined on action(POST /_/tenants) "
            + "response(400)], validationViolations=[]}",
                        c.getLastReport().toString());

    String doc = "{"+LS
            + "  \"id\" : \"roskilde\","+LS
            + "  \"name\" : \"roskilde\","+LS
            + "  \"description\" : \"Roskilde bibliotek\""+LS
            + "}";

    c = api.createRestAssured();
    Response r = c.given()
            .header("Content-Type", "application/json").body(doc)
            .post("/_/tenants").then().statusCode(201)
            .body(equalTo(doc)).extract().response();
    Assert.assertEquals(
            "RamlReport{requestViolations=[], responseViolations="
            + "[Body given but none defined on action(POST /_/tenants) "
            + "response(201)], validationViolations=[]}",
                        c.getLastReport().toString());

    Assert.assertTrue(c.getLastReport().isEmpty());
    String location = r.getHeader("Location");

    // post again, fail because of duplicate
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json").body(doc)
            .post("/_/tenants").then().statusCode(400);
    if (!c.getLastReport().isEmpty()) {
      logger.info("3:" + c.getLastReport().toString());
    }

    c = api.createRestAssured();
    c.given().get(location).then().statusCode(200).body(equalTo(doc));
    if (!c.getLastReport().isEmpty()) {
      logger.info("4:" + c.getLastReport().toString());
    }
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get(location + "none").then().statusCode(404);
    if (!c.getLastReport().isEmpty()) {
      logger.info("5:" + c.getLastReport().toString());
    }
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/tenants").then().statusCode(200).body(equalTo("[ " + doc + " ]"));
    if (!c.getLastReport().isEmpty()) {
      logger.info("6:" + c.getLastReport().toString());
    }
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().delete(location).then().statusCode(204);
    if (!c.getLastReport().isEmpty()) {
      logger.info("7:" + c.getLastReport().toString());
    }
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/tenants").then().statusCode(200).body(equalTo("[ ]"));
    if (!c.getLastReport().isEmpty()) {
      logger.info("8:" + c.getLastReport().toString());
    }
    Assert.assertTrue(c.getLastReport().isEmpty());

    String doc3 = "{"+LS
            + "  \"id\" : \"roskildedk\","+LS
            + "  \"name\" : \"roskilde\","+LS
            + "  \"description\" : \"Roskilde bibliotek\""+LS
            + "}";

    c = api.createRestAssured();
    Response r3 = c.given()
            .header("Content-Type", "application/json").body(doc3)
            .post("/_/tenants").then().statusCode(201)
            .body(equalTo(doc3)).extract().response();
    if (!c.getLastReport().isEmpty()) {
      logger.info("9:" + c.getLastReport().toString());
    }
    Assert.assertTrue(c.getLastReport().isEmpty());

    String location3 = r3.getHeader("Location");
    logger.debug("location3 = " + location3);

    c = api.createRestAssured();
    c.given().get("/_/tenants").then().statusCode(200).body(equalTo("[ " + doc3 + " ]"));
    if (!c.getLastReport().isEmpty()) {
      logger.info("10:" + c.getLastReport().toString());
    }
    Assert.assertTrue(c.getLastReport().isEmpty());

    String doc4 = "{"+LS
            + "  \"id\" : \"roskildedk\","+LS
            + "  \"name\" : \"Roskildes Real Name\","+LS
            + "  \"description\" : \"Roskilde bibliotek with a better description\""+LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json").body(doc4)
            .put(location).then().statusCode(200).body(equalTo(doc4));
    if (!c.getLastReport().isEmpty()) {
      logger.info("11:" + c.getLastReport().toString());
    }
    Assert.assertTrue(c.getLastReport().isEmpty());

    given().get("/_/test/reloadtenant/roskildedk").then().statusCode(204);

    c = api.createRestAssured();
    c.given().delete(location3).then().statusCode(204);
    if (!c.getLastReport().isEmpty()) {
      logger.info("12:" + c.getLastReport().toString());
    }
    Assert.assertTrue(c.getLastReport().isEmpty());

    String doc5 = "{"+LS
            + "  \"id\" : \"roskildedk\","+LS
            + "  \"name\" : \"Roskildes Real Name\","+LS
            + "  \"description\" : \"Roskilde bibliotek with a better description\""+LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json").body(doc5)
            .put(location).then().statusCode(400);
    if (!c.getLastReport().isEmpty()) {
      logger.info("13:" + c.getLastReport().toString());
    }
  }
}
