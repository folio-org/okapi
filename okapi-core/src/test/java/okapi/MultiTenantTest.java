package okapi;

import com.jayway.restassured.RestAssured;
import static com.jayway.restassured.RestAssured.given;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.okapi.MainVerticle;
import org.folio.okapi.common.OkapiLogger;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@java.lang.SuppressWarnings({"squid:S1192"})
@RunWith(VertxUnitRunner.class)
public class MultiTenantTest {

  private final Logger logger = OkapiLogger.get();
  private int port = 9230;

  private Vertx vertx;
  private static final String LS = System.lineSeparator();

  final String docAuthModule = "{" + LS
    + "  \"id\" : \"auth-1\"," + LS
    + "  \"name\" : \"auth\"," + LS
    + "  \"provides\" : [ {" + LS
    + "    \"id\" : \"auth\"," + LS
    + "    \"version\" : \"1.2\"," + LS
    + "    \"handlers\" : [ {" + LS
    + "      \"methods\" : [ \"POST\" ]," + LS
    + "      \"path\" : \"/authn/login\"," + LS
    + "      \"level\" : \"20\"," + LS
    + "      \"type\" : \"request-response\"" + LS
    + "    } ]" + LS
    + "  } ]," + LS
    + "  \"filters\" : [ {" + LS
    + "    \"methods\" : [ \"*\" ]," + LS
    + "    \"path\" : \"/\"," + LS
    + "    \"phase\" : \"auth\"," + LS
    + "    \"type\" : \"request-response\"," + LS
    + "    \"permissionsDesired\" : [ \"auth.extra\" ]" + LS
    + "  } ]," + LS
    + "  \"requires\" : [ ]," + LS
    + "  \"launchDescriptor\" : {" + LS
    + "    \"exec\" : "
    + "\"java -Dport=%p -jar ../okapi-test-auth-module/target/okapi-test-auth-module-fat.jar\"" + LS
    + "  }" + LS
    + "}";

  final String docTestModule = "{" + LS
    + "  \"id\" : \"sample-module-1.2.0\"," + LS
    + "  \"name\" : \"this module\"," + LS
    + "  \"provides\" : [ {" + LS
    + "    \"id\" : \"_tenant\"," + LS
    + "    \"version\" : \"1.0\"," + LS
    + "    \"interfaceType\" : \"system\"," + LS
    + "    \"handlers\" : [ {" + LS
    + "      \"methods\" : [ \"POST\", \"DELETE\" ]," + LS
    + "      \"pathPattern\" : \"/_/tenant\"" + LS
    + "    } ]" + LS
    + "  }, {" + LS
    + "    \"id\" : \"myint\"," + LS
    + "    \"version\" : \"1.0\"," + LS
    + "    \"handlers\" : [ {" + LS
    + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
    + "      \"pathPattern\" : \"/testb\"" + LS
    + "    } ]" + LS
    + "  } ]," + LS
    + "  \"requires\" : [ ]," + LS
    + "  \"launchDescriptor\" : {" + LS
    + "    \"exec\" : "
    + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
    + "  }" + LS
    + "}";

  public MultiTenantTest() {
  }

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();

    RestAssured.port = port;
    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(new JsonObject().put("port", Integer.toString(port)));
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
    given().get("/_/version").then().statusCode(200).log().ifValidationFails();

    given()
      .header("Content-Type", "application/json")
      .body(docAuthModule)
      .post("/_/proxy/modules")
      .then().statusCode(201).log().ifValidationFails();

    given()
      .header("Content-Type", "application/json")
      .body(docTestModule)
      .post("/_/proxy/modules")
      .then().statusCode(201).log().ifValidationFails();

    final String supertenant = "supertenant";
    given()
      .header("Content-Type", "application/json")
      .body("[ {\"id\" : \"auth-1\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + supertenant + "/install?deploy=true")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"auth-1\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));

    final String tenant1 = "tenant1";
    final String docTenant1 = "{" + LS
      + "  \"id\" : \"" + tenant1 + "\"," + LS
      + "  \"name\" : \"" + tenant1 + "\"," + LS
      + "  \"description\" : \"" + tenant1 + " bibliotek\"" + LS
      + "}";

    // create tenant should fail.. Must login first
    given()
      .header("Content-Type", "application/json")
      .body(docTenant1)
      .post("/_/proxy/tenants")
      .then().statusCode(401)
      .log().ifValidationFails();

    // login and get token
    final String docLogin = "{" + LS
      + "  \"tenant\" : \"" + supertenant + "\"," + LS
      + "  \"username\" : \"peter\"," + LS
      + "  \"password\" : \"peter-password\"" + LS
      + "}";
    final String okapiToken = given()
      .header("Content-Type", "application/json").body(docLogin)
      .post("/authn/login")
      .then().statusCode(200).extract().header("X-Okapi-Token");

    // create tenant1
    given()
      .header("Content-Type", "application/json")
      .header("X-Okapi-Token", okapiToken)
      .body(docTenant1)
      .post("/_/proxy/tenants")
      .then().statusCode(201)
      .header("Location", containsString("/_/proxy/tenants"))
      .log().ifValidationFails();

    // enable+deploy sample-module-1.2.0 for tenant1
    given()
      .header("Content-Type", "application/json")
      .header("X-Okapi-Token", okapiToken)
      .body("[ {\"id\" : \"sample-module-1.2.0\", \"action\" : \"enable\"} ]")
      .post("/_/proxy/tenants/" + tenant1 + "/install?deploy=true")
      .then().statusCode(200).log().ifValidationFails()
      .body(equalTo("[ {" + LS
        + "  \"id\" : \"sample-module-1.2.0\"," + LS
        + "  \"action\" : \"enable\"" + LS
        + "} ]"));

    // undeploy sample-module-1.2.0
    given()
      .header("Content-Type", "application/json")
      .header("X-Okapi-Token", okapiToken)
      .body("[ {\"id\" : \"sample-module-1.2.0\", \"action\" : \"disable\"} ]")
      .post("/_/proxy/tenants/" + tenant1 + "/install?deploy=true")
      .then().statusCode(200).log().ifValidationFails();

    // undeploy auth-1
    given()
      .header("Content-Type", "application/json")
      .header("X-Okapi-Token", okapiToken)
      .body("[ {\"id\" : \"auth-1\", \"action\" : \"disable\"} ]")
      .post("/_/proxy/tenants/" + supertenant + "/install?deploy=true")
      .then().statusCode(200).log().ifValidationFails();
  }
}
