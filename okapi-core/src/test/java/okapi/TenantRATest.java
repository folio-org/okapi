/*
 * Copyright (C) 2015 Index Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    int port = Integer.parseInt(System.getProperty("port", "9130"));

    RestAssured.port = port;

    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
            .assumingBaseUri("https://okapi.cloud");

    RestAssuredClient c;

    c = api.createRestAssured();
    c.given().get("/_/proxy/tenants").then().statusCode(200).body(equalTo("[ ]"));
    if (!c.getLastReport().isEmpty()) {
      logger.info("0:" + c.getLastReport().toString());
    }
    Assert.assertTrue(c.getLastReport().isEmpty());

    String badId = "{" + LS
            + "  \"id\" : \"Bad Id with Spaces and Specials: ?%!\"," + LS
            + "  \"name\" : \"roskilde\"," + LS
            + "  \"description\" : \"Roskilde bibliotek\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json").body(badId)
            .post("/_/proxy/tenants").then().statusCode(400);

    Assert.assertTrue(c.getLastReport().isEmpty());

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
    Assert.assertTrue(c.getLastReport().isEmpty());
    String location = r.getHeader("Location");

    // post again, fail because of duplicate
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json").body(doc)
            .post("/_/proxy/tenants").then().statusCode(400);

    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get(location).then().statusCode(200).body(equalTo(doc));
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get(location + "none").then().statusCode(404);
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/proxy/tenants").then().statusCode(200).body(equalTo("[ " + doc + " ]"));
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().delete(location).then().statusCode(204);
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/proxy/tenants").then().statusCode(200).body(equalTo("[ ]"));
    Assert.assertTrue(c.getLastReport().isEmpty());

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
    Assert.assertTrue(c.getLastReport().isEmpty());
    String location3 = r3.getHeader("Location");
    logger.debug("location3 = " + location3);

    c = api.createRestAssured();
    c.given().get("/_/proxy/tenants").then().statusCode(200).body(equalTo("[ " + doc3 + " ]"));
    Assert.assertTrue(c.getLastReport().isEmpty());

    String doc4 = "{" + LS
            + "  \"id\" : \"roskildedk\"," + LS
            + "  \"name\" : \"Roskildes Real Name\"," + LS
            + "  \"description\" : \"Roskilde bibliotek with a better description\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json").body(doc4)
            .put(location).then().statusCode(200).body(equalTo(doc4));
    Assert.assertTrue(c.getLastReport().isEmpty());

    given().get("/_/test/reloadtenant/roskildedk").then().statusCode(204);

    c = api.createRestAssured();
    c.given().delete(location3).then().statusCode(204);
    Assert.assertTrue(c.getLastReport().isEmpty());

    String doc5 = "{" + LS
            + "  \"id\" : \"roskildedk\"," + LS
            + "  \"name\" : \"Roskildes Real Name\"," + LS
            + "  \"description\" : \"Roskilde bibliotek with a better description\"" + LS
            + "}";
    c = api.createRestAssured();
    c.given()
            .header("Content-Type", "application/json").body(doc5)
            .put(location).then().statusCode(400);
    Assert.assertTrue(c.getLastReport().isEmpty());
  }
}
