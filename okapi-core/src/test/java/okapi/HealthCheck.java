/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import static com.jayway.restassured.RestAssured.*;

public class HealthCheck {
  Vertx vertx;

  private final int port = Integer.parseInt(System.getProperty("port", "9130"));

  @Before
  public void setUp() {
    vertx = Vertx.vertx();

    DeploymentOptions opt = new DeploymentOptions();
    vertx.deployVerticle(MainVerticle.class.getName(), opt);
  }

  @After
  public void tearDown() {
    vertx.close();
  }

  @Test
  public void testHealthCheck() {
    given().port(port).get("/_/health").then().assertThat().statusCode(200);
    given().port(port).get("/_/health2").then().assertThat().statusCode(404);
  }
}
