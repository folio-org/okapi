/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi;

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

public class TenantTestRA {

  Vertx vertx;
  private static final String LS = System.lineSeparator();

  public TenantTestRA() {
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

    given().port(port).get("/_/tenants").then().statusCode(200).body(equalTo("[ ]"));
    
    String doc1 = "{"+LS
            + "  \"name\" : \"roskilde\","+LS
            + "  \"description\" : \"Roskilde bibliotek\""+LS
            + "}";
    String doc2 = "{"+LS
            + "  \"id\" : \"roskilde\","+LS
            + "  \"name\" : \"roskilde\","+LS
            + "  \"description\" : \"Roskilde bibliotek\""+LS
            + "}";

    Response r = given().port(port).body(doc1).post("/_/tenants").then().statusCode(201).
              body(equalTo(doc2)).extract().response();
    String location = r.getHeader("Location");

    given().port(port).get(location).then().statusCode(200).body(equalTo(doc2));
    given().port(port).get(location + "none").then().statusCode(404);
    given().port(port).get("/_/tenants").then().statusCode(200).body(equalTo("[ \"roskilde\" ]"));
    given().port(port).delete(location).then().statusCode(204);
  }
}
