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
import io.vertx.core.json.JsonObject;
import static org.hamcrest.Matchers.*;

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

    given().get("/_/tenants").then().statusCode(200).body(equalTo("[ ]"));

    String doc1 = "{"+LS
            + "  \"name\" : \"roskilde\","+LS
            + "  \"description\" : \"Roskilde bibliotek\""+LS
            + "}";
    String doc2 = "{"+LS
            + "  \"id\" : \"roskilde\","+LS
            + "  \"name\" : \"roskilde\","+LS
            + "  \"description\" : \"Roskilde bibliotek\""+LS
            + "}";

    Response r = given().body(doc1).post("/_/tenants").then().statusCode(201).
              body(equalTo(doc2)).extract().response();
    String location = r.getHeader("Location");

    given().get(location).then().statusCode(200).body(equalTo(doc2));
    given().get(location + "none").then().statusCode(404);
    given().get("/_/tenants").then().statusCode(200).body(equalTo("[ " + doc2 + " ]"));
    given().delete(location).then().statusCode(204);
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
