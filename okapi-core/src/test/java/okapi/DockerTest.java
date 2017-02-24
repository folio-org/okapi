package okapi;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Response;
import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured.RestAssuredClient;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.okapi.MainVerticle;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class DockerTest {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private Vertx vertx;
  private HttpClient httpClient;
  private final int port = Integer.parseInt(System.getProperty("port", "9130"));
  private static final String LS = System.lineSeparator();
  private String locationSampleDeployment;
  private String locationSampleModule;

  public DockerTest() {
  }

  @BeforeClass
  public static void setUpClass() {
  }

  @AfterClass
  public static void tearDownClass() {
  }

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    DeploymentOptions opt = new DeploymentOptions();
    vertx.deployVerticle(MainVerticle.class.getName(),
            opt, context.asyncAssertSuccess());
    httpClient = vertx.createHttpClient();
    RestAssured.port = port;
  }

  @After
  public void tearDown(TestContext context) {
    td(context, context.async());
  }

  private void td(TestContext context, Async async) {
    if (locationSampleModule != null) {
      httpClient.delete(port, "localhost", locationSampleModule, res -> {
        td(context, async);
      }).end();
      locationSampleModule = null;
      return;
    }
    if (locationSampleDeployment != null) {
      httpClient.delete(port, "localhost", locationSampleDeployment, res -> {
        td(context, async);
      }).end();
      locationSampleDeployment = null;
      return;
    }
    vertx.close(x -> {
      async.complete();
    });
  }

  private void checkDocker(Handler<AsyncResult<Void>> future) {
    HttpClient client = vertx.createHttpClient();
    final String dockerUrl = "http://localhost:4243";
    final String url = dockerUrl + "/images/json?all=1";
    HttpClientRequest req = client.getAbs(url, res -> {
      Buffer body = Buffer.buffer();
      res.handler(d -> {
        body.appendBuffer(d);
      });
      res.endHandler(d -> {
        if (res.statusCode() == 200) {
          boolean gotIt = false;
          logger.info("RESULT\n" + body.toString());
          JsonArray ar = body.toJsonArray();
          for (int i = 0; i < ar.size(); i++) {
            JsonObject ob = ar.getJsonObject(i);
            JsonArray ar1 = ob.getJsonArray("RepoTags");
            for (int j = 0; j < ar1.size(); j++) {
              String tag = ar1.getString(j);
              if (tag != null && tag.startsWith("okapi-test-module")) {
                gotIt = true;
              }
            }
          }
          if (gotIt) {
            future.handle(Future.succeededFuture());
          } else {
            future.handle(Future.failedFuture("okapi-test-module not found"));
          }
        } else {
          String m = "checkDocker HTTP error "
                  + Integer.toString(res.statusCode()) + "\n"
                  + body.toString();
          logger.error(m);
          future.handle(Future.failedFuture(m));
        }
      });
    });
    req.exceptionHandler(d -> {
      future.handle(Future.failedFuture(d.getMessage()));
    });
    req.end();
  }

  @Test
  public void testDockerModule(TestContext context) {
    Async async = context.async();
    checkDocker(res -> {
      if (res.succeeded()) {
        dockerTests(context, async);
      } else {
        logger.info("NOT running module within Docker test. Reason: " + res.cause().getMessage());
        async.complete();
      }
    });
  }

  private void dockerTests(TestContext context, Async async) {
    RestAssuredClient c;
    Response r;
    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml")
            .assumingBaseUri("https://okapi.cloud");

    final String docSampleDockerModule = "{" + LS
            + "  \"id\" : \"sample-module\"," + LS
            + "  \"name\" : \"sample module\"," + LS
            + "  \"provides\" : [ {" + LS
            + "    \"id\" : \"sample\"," + LS
            + "    \"version\" : \"1.0.0\"" + LS
            + "  } ]," + LS
            + "  \"routingEntries\" : [ {" + LS
            + "    \"methods\" : [ \"GET\", \"POST\" ]," + LS
            + "    \"path\" : \"/test\"," + LS
            + "    \"level\" : \"30\"," + LS
            + "    \"type\" : \"request-response\"" + LS
            + "  } ]," + LS
            + "  \"launchDescriptor\" : {" + LS
            + "    \"dockerImage\" : \"okapi-test-module\"," + LS
            + "    \"dockerCMD\" : [\"-Dfoo=bar\"]," + LS
            + "    \"dockerArgs\" : {" + LS
            + "      \"StopTimeout\" : 12" + LS
            + "    }" + LS
            + "  }" + LS
            + "}";

    c = api.createRestAssured();
    r = c.given()
            .header("Content-Type", "application/json")
            .body(docSampleDockerModule).post("/_/proxy/modules")
            .then()
            .statusCode(201)
            .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    locationSampleModule = r.getHeader("Location");

    final String doc1 = "{" + LS
            + "  \"srvcId\" : \"sample-module\"," + LS
            + "  \"nodeId\" : \"localhost\"" + LS
            + "}";

    c = api.createRestAssured();
    r = c.given().header("Content-Type", "application/json")
            .body(doc1).post("/_/discovery/modules")
            .then().statusCode(201)
            .extract().response();
    Assert.assertTrue("raml: " + c.getLastReport().toString(),
            c.getLastReport().isEmpty());
    locationSampleDeployment = r.getHeader("Location");
    async.complete();
  }
}
