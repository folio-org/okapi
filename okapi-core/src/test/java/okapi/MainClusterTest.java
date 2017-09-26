package okapi;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Response;
import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured.RestAssuredClient;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.okapi.MainCluster;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class MainClusterTest {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final int port = Integer.parseInt(System.getProperty("port", "9130"));
  Async async;
  Vertx vertx;

  public MainClusterTest() {
  }

  @BeforeClass
  public static void setUpClass() {
  }

  @AfterClass
  public static void tearDownClass() {
  }

  @Before
  public void setUp(TestContext context) {
    async = context.async();

    RestAssured.port = port;
    String[] args = {"dev"};

    MainCluster.main1(args, res -> {
      vertx = res.result();
      Assert.assertTrue("main1: " + res.cause(), res.succeeded());
      async.complete();
    });
  }

  @After
  public void tearDown(TestContext context) {
    async = context.async();
    if (vertx != null) {
      vertx.close(x -> {
        async.complete();
      });
    } else {
      async.complete();
    }
  }

  @Test
  public void testVersion(TestContext context) {
    async = context.async();

    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml");

    RestAssuredClient c;
    Response r;

    c = api.createRestAssured();
    r = c.given().get("/_/version").then().statusCode(200).log().ifError().extract().response();

    Assert.assertTrue("raml: " + c.getLastReport().toString(),
      c.getLastReport().isEmpty());
    async.complete();
  }

}
