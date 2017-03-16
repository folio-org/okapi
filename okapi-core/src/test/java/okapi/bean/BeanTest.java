package okapi.bean;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.junit.Test;
import static org.junit.Assert.*;

public class BeanTest {
  private static final String LS = System.lineSeparator();

  public BeanTest() {
  }

  @Test
  public void testDeploymentDescriptor1() {
    int fail = 0;
    final String docSampleDeployment = "{" + LS
            + "  \"srvcId\" : \"sample-module\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"exec\" : "
            + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"," + LS
            + "    \"env\" : [ {" + LS
            + "      \"name\" : \"helloGreeting\"," + LS
            + "      \"value\" : \"hej\"" + LS
            + "    } ]" + LS
            + "  }" + LS
            + "}";

    try {
      final DeploymentDescriptor md = Json.decodeValue(docSampleDeployment,
              DeploymentDescriptor.class);
      String pretty = Json.encodePrettily(md);
      assertEquals(docSampleDeployment, pretty);
    } catch (DecodeException ex) {
      ex.printStackTrace();
      fail = 400;
    }
    assertEquals(fail, 0);
  }

  @Test
  public void testDeploymentDescriptor2() {
    int fail = 0;
    final String docSampleDeployment = "{" + LS
            + "  \"srvcId\" : \"sample-module\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"exec\" : "
            + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"," + LS
            + "    \"env\" : [ {" + LS
            + "      \"name\" : \"helloGreeting\"" + LS
            + "    } ]" + LS
            + "  }" + LS
            + "}";

    try {
      final DeploymentDescriptor md = Json.decodeValue(docSampleDeployment,
              DeploymentDescriptor.class);
      String pretty = Json.encodePrettily(md);
      assertEquals(docSampleDeployment, pretty);
    } catch (DecodeException ex) {
      ex.printStackTrace();
      fail = 400;
    }
    assertEquals(fail, 0);

  }

  @Test
  public void testDeploymentDescriptor3() {
    int fail = 0;
    final String docSampleDeployment = "{" + LS
            + "  \"srvcId\" : \"sample-module\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"exec\" : "
            + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
            + "  }" + LS
            + "}";

    try {
      final DeploymentDescriptor md = Json.decodeValue(docSampleDeployment,
              DeploymentDescriptor.class);
      String pretty = Json.encodePrettily(md);
      assertEquals(docSampleDeployment, pretty);
    } catch (DecodeException ex) {
      ex.printStackTrace();
      fail = 400;
    }
    assertEquals(fail, 0);
  }

  @Test
  public void testDeploymentDescriptor4() {
    int fail = 0;
    final String docSampleDeployment = "{" + LS
            + "  \"srvcId\" : \"sample-module\"," + LS
            + "  \"descriptor\" : {" + LS
            + "    \"dockerImage\" : \"my-image\"," + LS
            + "    \"dockerArgs\" : {" + LS
            + "      \"Hostname\" : \"localhost\"," + LS
            + "      \"User\" : \"nobody\"" + LS
            + "    }" + LS
            + "  }" + LS
            + "}";

    try {
      final DeploymentDescriptor md = Json.decodeValue(docSampleDeployment,
              DeploymentDescriptor.class);
      String pretty = Json.encodePrettily(md);
      assertEquals(docSampleDeployment, pretty);
    } catch (DecodeException ex) {
      ex.printStackTrace();
      fail = 400;
    }
    assertEquals(fail, 0);
  }

  @Test
  public void testModuleDescriptor1() {
    int fail = 0;

    final String docModuleDescriptor = "{" + LS
      + "  \"id\" : \"sample-module\"," + LS
      + "  \"name\" : \"sample module\"," + LS
      + "  \"env\" : [ {" + LS
      + "    \"name\" : \"helloGreeting\"" + LS
      + "  } ]," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"version\" : \"1.0.0\"," + LS
      + "    \"routingEntries\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"POST\" ]," + LS
      + "      \"pathPattern\" : \"/users/{id}\"," + LS
      + "      \"level\" : \"30\"," + LS
      + "      \"type\" : \"request-response\"," + LS
      + "      \"permissionsRequired\" : [ \"sample.needed\" ]," + LS
      + "      \"permissionsDesired\" : [ \"sample.extra\" ]," + LS
      + "      \"modulePermissions\" : [ \"sample.modperm\" ]" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"_tenant\"," + LS
      + "    \"version\" : \"1.0.0\"," + LS
      + "    \"interfaceType\" : \"system\"," + LS
      + "    \"routingEntries\" : [ {" + LS
      + "      \"methods\" : [ \"POST\", \"DELETE\" ]," + LS
      + "      \"path\" : \"/_/tenant\"," + LS
      + "      \"level\" : \"10\"," + LS
      + "      \"type\" : \"system\"" + LS
      + "    } ]" + LS
      + "  } ]" + LS
      + "}";

    try {
      final ModuleDescriptor md = Json.decodeValue(docModuleDescriptor,
        ModuleDescriptor.class);
      String pretty = Json.encodePrettily(md);
      System.out.println(pretty);
      System.out.println(docModuleDescriptor);
      assertEquals(docModuleDescriptor, pretty);
    } catch (DecodeException ex) {
      ex.printStackTrace();
      fail = 400;
    }
    assertEquals(fail, 0);
  }

}
