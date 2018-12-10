package org.folio.okapi.bean;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.junit.Test;
import static org.junit.Assert.*;

@java.lang.SuppressWarnings({"squid:S1166", "squid:S1192"})
public class BeanTest {
  private static final String LS = System.lineSeparator();

  @Test
  public void testDeploymentDescriptor1() {
    int fail = 0;
    final String docSampleDeployment = "{" + LS
      + "  \"srvcId\" : \"sample-module-1\"," + LS
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
    assertEquals(0, fail);
  }

  @Test
  public void testDeploymentDescriptor2() {
    int fail = 0;
    final String docSampleDeployment = "{" + LS
      + "  \"srvcId\" : \"sample-module-1\"," + LS
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
    assertEquals(0, fail);
  }

  @Test
  public void testDeploymentDescriptor3() {
    int fail = 0;
    final String docSampleDeployment = "{" + LS
      + "  \"srvcId\" : \"sample-module-1\"," + LS
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
    assertEquals(0, fail);
  }

  @Test
  public void testDeploymentDescriptor4() {
    int fail = 0;
    final String docSampleDeployment = "{" + LS
      + "  \"srvcId\" : \"sample-module-1\"," + LS
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
    assertEquals(0, fail);
  }

  @Test
  public void testModuleDescriptor1() {
    int fail = 0;

    final String docModuleDescriptor = "{" + LS
      + "  \"id\" : \"sample-module-1\"," + LS
      + "  \"name\" : \"sample module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"version\" : \"1.0.0\"," + LS
      + "    \"handlers\" : [ {" + LS
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
      + "    \"handlers\" : [ {" + LS
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
      assertEquals(docModuleDescriptor, pretty);
    } catch (DecodeException ex) {
      ex.printStackTrace();
      fail = 400;
    }
    assertEquals(0, fail);
  }

  @Test
  public void testModuleDescriptor2() {
    int fail = 0;

    final String docModuleDescriptor = "{" + LS
      + "  \"id\" : \"sample-module-1\"," + LS
      + "  \"name\" : \"sample module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"version\" : \"1.0.0\"," + LS
      + "    \"handlers\" : [ {" + LS
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
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\", \"DELETE\" ]," + LS
      + "      \"path\" : \"/_/tenant\"," + LS
      + "      \"level\" : \"10\"," + LS
      + "      \"type\" : \"system\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"filters\" : [ {" + LS
      + "    \"methods\" : [ \"*\" ]," + LS
      + "    \"pathPattern\" : \"/*\"," + LS
      + "    \"rewritePath\" : \"/events\"," + LS
      + "    \"type\" : \"request-response\"" + LS
      + "  } ]" + LS
      + "}";

    try {
      final ModuleDescriptor md = Json.decodeValue(docModuleDescriptor,
        ModuleDescriptor.class);
      String pretty = Json.encodePrettily(md);
      assertEquals(docModuleDescriptor, pretty);
      final ModuleInstance mi = new ModuleInstance(md, md.getFilters()[0], "/test/123", HttpMethod.GET, true);
      assertEquals("/test/123", mi.getPath());
      assertEquals("/events", mi.getRewritePath());
      assertEquals("/events/test/123", mi.getRewritePath() + mi.getPath());
    } catch (DecodeException ex) {
      ex.printStackTrace();
      fail = 400;
    }
    assertEquals(0, fail);
  }

}
