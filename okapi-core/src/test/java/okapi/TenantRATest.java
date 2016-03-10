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
       logger.info(c.getLastReport().toString());
    }

    String badId = "{"+LS
            + "  \"id\" : \"Bad Id with Spaces and Specials: ?%!\","+LS
            + "  \"name\" : \"roskilde\","+LS
            + "  \"description\" : \"Roskilde bibliotek\""+LS
            + "}";
    given().port(port).body(badId).post("/_/tenants").then().statusCode(400);

    String doc = "{"+LS
            + "  \"id\" : \"roskilde\","+LS
            + "  \"name\" : \"roskilde\","+LS
            + "  \"description\" : \"Roskilde bibliotek\""+LS
            + "}";

    Response r = given().port(port).body(doc).post("/_/tenants").then().statusCode(201).
              body(equalTo(doc)).extract().response();
    if (!c.getLastReport().isEmpty()) {
       logger.debug(c.getLastReport().toString());
    }
    Assert.assertTrue(c.getLastReport().isEmpty());
    String location = r.getHeader("Location");

    // post again, fail because of duplicate
    given().port(port).body(doc).post("/_/tenants").then().statusCode(400);



    c = api.createRestAssured();
    c.given().get(location).then().statusCode(200).body(equalTo(doc));
    if (!c.getLastReport().isEmpty()) {
       logger.debug(c.getLastReport().toString());
    }
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get(location + "none").then().statusCode(404);
    if (!c.getLastReport().isEmpty()) {
       logger.debug(c.getLastReport().toString());
    }
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/tenants").then().statusCode(200).body(equalTo("[ " + doc + " ]"));
    if (!c.getLastReport().isEmpty()) {
       logger.debug(c.getLastReport().toString());
    }
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().delete(location).then().statusCode(204);
    if (!c.getLastReport().isEmpty()) {
       logger.debug(c.getLastReport().toString());
    }
    Assert.assertTrue(c.getLastReport().isEmpty());

    given().get("/_/tenants").then().statusCode(200).body(equalTo("[ ]"));

    String doc3 = "{"+LS
            + "  \"id\" : \"roskildedk\","+LS
            + "  \"name\" : \"roskilde\","+LS
            + "  \"description\" : \"Roskilde bibliotek\""+LS
            + "}";

    Response r3 = given().body(doc3).post("/_/tenants").then().statusCode(201).
              body(equalTo(doc3)).extract().response();
    String location3 = r3.getHeader("Location");
    logger.debug("location3 = " + location3);

    given().get("/_/tenants").then().statusCode(200).body(equalTo("[ " + doc3 + " ]"));

    String doc4 = "{"+LS
            + "  \"id\" : \"roskildedk\","+LS
            + "  \"name\" : \"Roskildes Real Name\","+LS
            + "  \"description\" : \"Roskilde bibliotek with a better description\""+LS
            + "}";
    given().body(doc4).put(location).then().statusCode(200).body(equalTo(doc4));


    given().get("/_/test/reloadtenant/roskildedk").then().statusCode(204);

    given().delete(location3).then().statusCode(204);
  }
}
