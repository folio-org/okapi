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
import static com.jayway.restassured.matcher.RestAssuredMatchers.*;
import com.jayway.restassured.response.Response;
import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured.RestAssuredClient;
import io.vertx.core.json.JsonObject;
import static org.hamcrest.Matchers.*;
import org.junit.Assert;

public class TenantRATest {

  Vertx vertx;
  private static final String LS = System.lineSeparator();

  public TenantRATest() {
  }

  @Before
  public void setUp() {
    vertx = Vertx.vertx();

    DeploymentOptions opt = new DeploymentOptions()
            .setConfig(new JsonObject().put("storage", "inmemory"));
    vertx.deployVerticle(MainVerticle.class.getName(), opt);
  }

  @After
  public void tearDown() {
    vertx.close();
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
       System.out.println(c.getLastReport().toString());
    }

    String doc1 = "{"+LS
            + "  \"name\" : \"roskilde\","+LS
            + "  \"description\" : \"Roskilde bibliotek\""+LS
            + "}";
    String doc2 = "{"+LS
            + "  \"id\" : \"roskilde\","+LS
            + "  \"name\" : \"roskilde\","+LS
            + "  \"description\" : \"Roskilde bibliotek\""+LS
            + "}";

    c = api.createRestAssured();
    Response r = c.given().header("Content-Type", "application/json").body(doc1).post("/_/tenants")
            .then().statusCode(201).
             body(equalTo(doc2)).extract().response();
    if (!c.getLastReport().isEmpty()) {
       System.out.println(c.getLastReport().toString());
    }
    Assert.assertTrue(c.getLastReport().isEmpty());
    String location = r.getHeader("Location");

    c = api.createRestAssured();
    c.given().get(location).then().statusCode(200).body(equalTo(doc2));
    if (!c.getLastReport().isEmpty()) {
       System.out.println(c.getLastReport().toString());
    }
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get(location + "none").then().statusCode(404);
    if (!c.getLastReport().isEmpty()) {
       System.out.println(c.getLastReport().toString());
    }
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().get("/_/tenants").then().statusCode(200).body(equalTo("[ " + doc2 + " ]"));
    if (!c.getLastReport().isEmpty()) {
       System.out.println(c.getLastReport().toString());
    }
    Assert.assertTrue(c.getLastReport().isEmpty());

    c = api.createRestAssured();
    c.given().delete(location).then().statusCode(204);
    if (!c.getLastReport().isEmpty()) {
       System.out.println(c.getLastReport().toString());
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
    System.out.println("location3 = " + location3);

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
