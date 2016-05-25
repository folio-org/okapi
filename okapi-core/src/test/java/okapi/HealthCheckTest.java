/*
 * Copyright (C) 2016 Index Data
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import static com.jayway.restassured.RestAssured.*;
import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured.RestAssuredClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Assert;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class HealthCheckTest {

  Vertx vertx;

  private final int port = Integer.parseInt(System.getProperty("port", "9130"));

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
  public void testHealthCheck() {
    RestAssured.port = port;

    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
            .assumingBaseUri("https://okapi.cloud");

    RestAssuredClient c;

    c = api.createRestAssured();
    c.given().get("/_/proxy/health").then().assertThat().statusCode(200);
    Assert.assertTrue("raml report: " + c.getLastReport().toString(), c.getLastReport().isEmpty());

    given().get("/_/proxy/health2").then().assertThat().statusCode(404);
  }

}
