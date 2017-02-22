package okapi.bean;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.folio.okapi.bean.DeploymentDescriptor;
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
}
