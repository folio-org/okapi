package org.folio.okapi;

import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured3.RestAssuredClient;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiLogger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@java.lang.SuppressWarnings({"squid:S1166", "squid:S1192"})
@RunWith(VertxUnitRunner.class)
public class DockerTest {
  VertxOptions options = new VertxOptions()
    .setBlockedThreadCheckInterval(60000) // in ms
    .setWarningExceptionTime(60000) // in ms
    .setPreferNativeTransport(true);

  private Vertx vertx = Vertx.vertx(options);
  private final Logger logger = OkapiLogger.get();
  private final int port = 9230;
  private static final String LS = System.lineSeparator();
  private boolean haveDocker = false;
  private JsonArray dockerImages = new JsonArray();
  private String verticleId;

  @Before
  public void setUp(TestContext context) {
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.port = port;
    Async async = context.async();
    checkDocker().onComplete(res -> {
      haveDocker = res.succeeded();
      if (res.succeeded()) {
        dockerImages = res.result();
        logger.info("Docker found");
      } else {
        logger.warn("No docker: " + res.cause().getMessage());
      }
      DeploymentOptions opt = new DeploymentOptions()
        .setConfig(new JsonObject()
          .put("containerHost", "localhost")
          .put("port", Integer.toString(port))
          .put("port_start", Integer.toString(port + 4))
          .put("port_end", Integer.toString(port + 6)));

      vertx.deployVerticle(MainVerticle.class.getName(), opt)
       .onComplete(context.asyncAssertSuccess(id -> {
          logger.info("Verticle deployed: " + id);
          verticleId = id;
          async.complete();
        }));
    });
    async.await();
  }

  @After
  public void tearDown(TestContext context) {
    logger.info("tearDown");

    HttpClient httpClient = vertx.createHttpClient();
    httpClient.request(HttpMethod.DELETE, port, "localhost", "/_/discovery/modules")
      .compose(request -> request.send().expecting(HttpResponseExpectation.SC_NO_CONTENT))
      .eventually(() -> vertx.undeploy(verticleId))
      .onComplete(context.asyncAssertSuccess());
  }

  private Future<JsonArray> checkDocker() {
    WebClient client = WebClient.create(vertx);
    SocketAddress serverAddress = SocketAddress
      .domainSocketAddress("/var/run/docker.sock");

    return client
      .request(
        HttpMethod.GET,
        serverAddress,
        8080,
        "localhost",
        "/images/json?all=1")
      .send()
      .expecting(HttpResponseExpectation.SC_SUCCESS)
      .expecting(HttpResponseExpectation.JSON)
      .map(res -> {
        System.out.println("Current Docker images" + res);
        return res.bodyAsJsonArray();
      });
  }

  private static boolean checkTestModulePresent(JsonArray ar) {
    if (ar != null) {
      for (int i = 0; i < ar.size(); i++) {
        JsonObject ob = ar.getJsonObject(i);
        JsonArray ar1 = ob.getJsonArray("RepoTags");
        if (ar1 != null) {
          for (int j = 0; j < ar1.size(); j++) {
            String tag = ar1.getString(j);
            if (tag != null && tag.startsWith("okapi-test-module")) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  // deploys okapi-test-module
  // to avoid skip, use:
  // cd okapi-test-module
  // docker build -t okapi-test-module .
  @Test
  public void deploySampleModule(TestContext context) {
    org.junit.Assume.assumeTrue(checkTestModulePresent(dockerImages));
    if (!checkTestModulePresent(dockerImages)) {
      return;
    }
    RestAssuredClient c;
    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
      .assumingBaseUri("https://okapi.cloud");

    final String docSampleDockerModule = "{" + LS
      + "  \"id\" : \"sample-module-1.0.0\"," + LS
      + "  \"name\" : \"sample module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/testb\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"dockerImage\" : \"okapi-test-module\"," + LS
      + "    \"dockerPull\" : false," + LS
      + "    \"dockerCMD\" : [\"-Dfoo=bar\"]," + LS
      + "    \"dockerArgs\" : {" + LS
      + "      \"StopTimeout\" : 12," + LS
      + "      \"HostConfig\": { \"PortBindings\": { \"8080/tcp\": [{ \"HostPort\": \"%p\" }] } }" + LS
      + "    }" + LS
      + "  }" + LS
      + "}";

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docSampleDockerModule).post("/_/proxy/modules")
      .then()
      .statusCode(201);
    context.assertTrue(c.getLastReport().isEmpty(),
      "raml: " + c.getLastReport().toString());

    final String doc1 = "{" + LS
      + "  \"srvcId\" : \"sample-module-1.0.0\"," + LS
      + "  \"nodeId\" : \"localhost\"" + LS
      + "}";

    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
      .body(doc1).post("/_/discovery/modules")
      .then().statusCode(201);
    context.assertTrue(c.getLastReport().isEmpty(),
      "raml: " + c.getLastReport().toString());
  }

  @Test
  public void deployUnknownModule(TestContext context) {
    RestAssuredClient c;
    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
      .assumingBaseUri("https://okapi.cloud");

    final String docSampleDockerModule = "{" + LS
      + "  \"id\" : \"sample-unknown-1\"," + LS
      + "  \"name\" : \"sample unknown\"," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"dockerImage\" : \"okapi-unknown\"," + LS
      + "    \"dockerPull\" : false," + LS
      + "    \"dockerArgs\" : {" + LS
      + "      \"StopTimeout\" : 12," + LS
      + "      \"HostConfig\": { \"PortBindings\": { \"8080/tcp\": [{ \"HostPort\": \"%p\" }] } }" + LS
      + "    }" + LS
      + "  }" + LS
      + "}";

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docSampleDockerModule).post("/_/proxy/modules")
      .then()
      .statusCode(201);
    context.assertTrue(c.getLastReport().isEmpty(),
      "raml: " + c.getLastReport().toString());

    final String doc1 = "{" + LS
      + "  \"srvcId\" : \"sample-unknown-1\"," + LS
      + "  \"nodeId\" : \"localhost\"" + LS
      + "}";

    c = api.createRestAssured3();
    c.given().header("Content-Type", "application/json")
      .body(doc1).post("/_/discovery/modules")
      .then().statusCode(400);
    context.assertTrue(c.getLastReport().isEmpty(),
      "raml: " + c.getLastReport().toString());
  }

  @Test
  public void deployBadListeningPort(TestContext context) {
    org.junit.Assume.assumeTrue(haveDocker);
    RestAssuredClient c;
    Response r;
    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
      .assumingBaseUri("https://okapi.cloud");

    // forward to 8090, but with no listening port.
    final String docUserDockerModule = "{" + LS
      + "  \"id\" : \"mod-users-5.0.0-bad-listening-port\"," + LS
      + "  \"name\" : \"users\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"users\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/test\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"waitIterations\" : 5," + LS
      + "    \"dockerImage\" : \"folioci/mod-users:5.0.0-SNAPSHOT\"," + LS
      + "    \"dockerArgs\" : {" + LS
      + "      \"HostConfig\": { \"PortBindings\": { \"8090/tcp\": [{ \"HostPort\": \"%p\" }] } }" + LS
      + "    }" + LS
      + "  }" + LS
      + "}";

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docUserDockerModule).post("/_/proxy/modules")
      .then()
      .statusCode(201);
    context.assertTrue(c.getLastReport().isEmpty(),
      "raml: " + c.getLastReport().toString());

    final String doc2 = "{" + LS
      + "  \"srvcId\" : \"mod-users-5.0.0-bad-listening-port\"," + LS
      + "  \"nodeId\" : \"localhost\"" + LS
      + "}";

    c = api.createRestAssured3();
    r = c.given().header("Content-Type", "application/json")
      .body(doc2).post("/_/discovery/modules")
      .then().statusCode(400).extract().response();
    context.assertTrue(c.getLastReport().isEmpty(),
      "raml: " + c.getLastReport().toString());
    context.assertTrue(r.getBody().asString().contains("Could not connect to localhost:9234"),
      "body is " + r.getBody().asString());
  }

  @Test
  public void deployModUsers(TestContext context) {
    org.junit.Assume.assumeTrue(haveDocker);
    RestAssuredClient c;
    Response r;
    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
      .assumingBaseUri("https://okapi.cloud");

    final String docUserDockerModule = "{" + LS
      + "  \"id\" : \"mod-users-5.0.0\"," + LS
      + "  \"name\" : \"users\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"users\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/test\"," + LS
      + "      \"permissionsRequired\" : [ ]" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"launchDescriptor\" : {" + LS
      + "    \"waitIterations\" : 10," + LS
      + "    \"dockerImage\" : \"folioci/mod-users:5.0.0-SNAPSHOT\"," + LS
      + "    \"dockerArgs\" : {" + LS
      + "      \"HostConfig\": {" + LS
      + "         \"PortBindings\": { \"8081/tcp\": [{ \"HostIp\": \"%c\", \"HostPort\": \"%p\" }] } }" + LS
      + "    }" + LS
      + "  }" + LS
      + "}";

    c = api.createRestAssured3();
    c.given()
      .header("Content-Type", "application/json")
      .body(docUserDockerModule).post("/_/proxy/modules")
      .then()
      .statusCode(201);
    context.assertTrue(c.getLastReport().isEmpty(),
      "raml: " + c.getLastReport().toString());

    final String doc2 = "{" + LS
      + "  \"srvcId\" : \"mod-users-5.0.0\"," + LS
      + "  \"nodeId\" : \"localhost\"" + LS
      + "}";

    c = api.createRestAssured3();
    r = c.given().header("Content-Type", "application/json")
      .body(doc2).post("/_/discovery/modules")
      .then().extract().response();
    int statusCode = r.getStatusCode();
    context.assertTrue(c.getLastReport().isEmpty(),
      "raml: " + c.getLastReport().toString());
    // Deal with port forwarding not working in Jenkins pipeline FOLIO-2404
    String rBody = r.getBody().asString();
    if (statusCode == 400) {
      context.assertTrue(rBody.contains("Could not connect to localhost:9234")
        || rBody.contains("port is already allocated"), "body is " + rBody);
    } else {
      context.assertEquals(201, statusCode);
    }
  }
}
