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

    final DeploymentDescriptor md = Json.decodeValue(docSampleDeployment,
        DeploymentDescriptor.class);
    String pretty = Json.encodePrettily(md);
    assertEquals(docSampleDeployment, pretty);
  }

  @Test
  public void testDeploymentDescriptor2() {
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

    final DeploymentDescriptor md = Json.decodeValue(docSampleDeployment,
        DeploymentDescriptor.class);
    String pretty = Json.encodePrettily(md);
    assertEquals(docSampleDeployment, pretty);
  }

  @Test
  public void testDeploymentDescriptor3() {
    final String docSampleDeployment = "{" + LS
        + "  \"srvcId\" : \"sample-module-1\"," + LS
        + "  \"descriptor\" : {" + LS
        + "    \"exec\" : "
        + "\"java -Dport=%p -jar ../okapi-test-module/target/okapi-test-module-fat.jar\"" + LS
        + "  }" + LS
        + "}";

    final DeploymentDescriptor md = Json.decodeValue(docSampleDeployment,
        DeploymentDescriptor.class);
    String pretty = Json.encodePrettily(md);
    assertEquals(docSampleDeployment, pretty);
  }

  @Test
  public void testDeploymentDescriptor4() {
    final String docSampleDeployment = "{" + LS
        + "  \"srvcId\" : \"sample-module-1\"," + LS
        + "  \"descriptor\" : {" + LS
        + "    \"dockerImage\" : \"my-image\"," + LS
        + "    \"dockerArgs\" : {" + LS
        + "      \"Hostname\" : \"localhost\"," + LS
        + "      \"User\" : \"nobody\"" + LS
        + "    }," + LS
        + "    \"dockerCMD\" : [ \"a\", \"b\" ]" + LS
        + "  }" + LS
        + "}";

    final DeploymentDescriptor md = Json.decodeValue(docSampleDeployment,
        DeploymentDescriptor.class);
    assertEquals("b", md.getDescriptor().getDockerCmd()[1]);
    String pretty = Json.encodePrettily(md);
    assertEquals(docSampleDeployment, pretty);
  }

  @Test
  public void testModuleDescriptor1() {
    final String docModuleDescriptor = "{" + LS
      + "  \"id\" : \"sample-module-1\"," + LS
      + "  \"name\" : \"sample module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"version\" : \"1.0\"," + LS
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
      + "    \"version\" : \"1.0\"," + LS
      + "    \"interfaceType\" : \"system\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"POST\", \"DELETE\" ]," + LS
      + "      \"path\" : \"/_/tenant\"," + LS
      + "      \"level\" : \"10\"," + LS
      + "      \"type\" : \"system\"" + LS
      + "    } ]" + LS
      + "  } ]," + LS
      + "  \"optional\" : [ {" + LS
      + "    \"id\" : \"foo\"," + LS
      + "    \"version\" : \"1.0\"" + LS
      + "  } ]," + LS
      + "  \"env\" : [ {" + LS
      + "    \"name\" : \"DB_HOST\"," + LS
      + "    \"value\" : \"localhost\"" + LS
      + "  } ]," + LS
      + "  \"metadata\" : {" + LS
      + "    \"scm\" : \"https://github.com/folio-org/mod-something\"," + LS
      + "    \"language\" : \"java\"" + LS
      + "  }," + LS
      + "  \"replaces\" : [ \"old-module\", \"other-module\" ]" + LS
        + "}";

    final ModuleDescriptor md = Json.decodeValue(docModuleDescriptor,
        ModuleDescriptor.class);
    String pretty = Json.encodePrettily(md);
    assertEquals(docModuleDescriptor, pretty);
  }

  @Test
  public void testModuleDescriptor2() {

    final String docModuleDescriptor = "{" + LS
      + "  \"id\" : \"sample-module-1\"," + LS
      + "  \"name\" : \"sample module\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"version\" : \"1.0\"," + LS
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
      + "    \"version\" : \"1.0\"," + LS
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

    final ModuleDescriptor md = Json.decodeValue(docModuleDescriptor,
        ModuleDescriptor.class);
    String pretty = Json.encodePrettily(md);
    assertEquals(docModuleDescriptor, pretty);
    final ModuleInstance mi = new ModuleInstance(md, md.getFilters()[0], "/test/123", HttpMethod.GET, true);
    assertEquals("/events/test/123", mi.getPath());
  }

  @Test
  public void testModuleDescriptorBadMethod()  {
    final String docModuleDescriptor = "{" + LS
      + "  \"id\" : \"sample-module-1\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"sample\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\", \"NOST\" ]," + LS
      + "      \"pathPattern\" : \"/users/{id}\"" + LS
      + "    } ]" + LS
      + "  } ]" + LS
      + "}";

    String msg = null;
    try {
      Json.decodeValue(docModuleDescriptor, ModuleDescriptor.class);
    } catch (DecodeException ex) {
      msg = ex.getMessage();
    }
    assertTrue(msg, msg.startsWith("Failed to decode:NOST"));
  }

  @Test
  public void testModuleDescriptorTimers() {
    final String docModuleDescriptor = "{" + LS
      + "  \"id\" : \"sample-module-1\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"_timer\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\" ]," + LS
      + "      \"pathPattern\" : \"/test\"," + LS
      + "      \"delay\" : \"1\"," + LS
      + "      \"unit\" : \"second\"" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"GET\" ]," + LS
      + "      \"pathPattern\" : \"/test\"," + LS
      + "      \"delay\" : \"1\"," + LS
      + "      \"unit\" : \"minute\"" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"GET\" ]," + LS
      + "      \"pathPattern\" : \"/test\"," + LS
      + "      \"delay\" : \"1\"," + LS
      + "      \"unit\" : \"hour\"" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"GET\" ]," + LS
      + "      \"pathPattern\" : \"/test\"," + LS
      + "      \"delay\" : \"1\"," + LS
      + "      \"unit\" : \"day\"" + LS
      + "    }, {" + LS
      + "      \"methods\" : [ \"GET\" ]," + LS
      + "      \"pathPattern\" : \"/test\"," + LS
      + "      \"delay\" : \"1\"" + LS
      + "    } ]" + LS
      + "  } ]" + LS
      + "}";

    ModuleDescriptor moduleDescriptor = Json.decodeValue(docModuleDescriptor, ModuleDescriptor.class);
    moduleDescriptor.setPermissionSets(null);
    assertNull(moduleDescriptor.getPermissionSets());
  }

  @Test
  public void testModuleDescriptorTimers2() {
    final String docModuleDescriptor = "{" + LS
      + "  \"id\" : \"sample-module-1\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"_timer\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\" ]," + LS
      + "      \"pathPattern\" : \"/test\"," + LS
      + "      \"delay\" : \"1\"," + LS
      + "      \"unit\" : \"second1\"" + LS
      + "    } ]" + LS
      + "  } ]" + LS
      + "}";

    String msg = null;
    try {
      Json.decodeValue(docModuleDescriptor, ModuleDescriptor.class);
    } catch (DecodeException ex) {
      msg = ex.getMessage();
    }
    assertTrue(msg, msg.startsWith("Failed to decode:second1"));
  }

  @Test
  public void testMultipleProvides() {
    final String docModuleDescriptor = "{" + LS
      + "  \"id\" : \"sample-module-1\"," + LS
      + "  \"provides\" : [ {" + LS
      + "    \"id\" : \"_timer\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\" ]," + LS
      + "      \"pathPattern\" : \"/test\"," + LS
      + "      \"delay\" : \"1\"," + LS
      + "      \"unit\" : \"second\"" + LS
      + "    } ]" + LS
      + "  }, {" + LS
      + "    \"id\" : \"_timer\"," + LS
      + "    \"version\" : \"1.0\"," + LS
      + "    \"handlers\" : [ {" + LS
      + "      \"methods\" : [ \"GET\" ]," + LS
      + "      \"pathPattern\" : \"/test\"," + LS
      + "      \"delay\" : \"1\"," + LS
      + "      \"unit\" : \"second\"" + LS
      + "    } ]" + LS
      + "  } ]" + LS
      + "}";

    String msg = null;
    try {
      Json.decodeValue(docModuleDescriptor, ModuleDescriptor.class);
    } catch (DecodeException ex) {
      msg = ex.getMessage();
    }
    assertTrue(msg, msg.startsWith("Failed to decode:Interface"));
  }

  @Test
  public void testMissingPermissionName() {
    final String docModuleDescriptor = "{" + LS
        + "  \"id\" : \"sample-module-1\"," + LS
        + "  \"provides\" : [ {" + LS
        + "    \"id\" : \"api\"," + LS
        + "    \"version\" : \"1.0\"," + LS
        + "    \"handlers\" : [ {" + LS
        + "      \"methods\" : [ \"GET\" ]," + LS
        + "      \"pathPattern\" : \"/test\"," + LS
        + "      \"permissionsRequired\" : [\"/test.get\"]" + LS
        + "    } ]" + LS
        + "  } ]," + LS
        + "  \"requires\" : [ ]," + LS
        + "  \"permissionSets\": [ {" + LS
        + "    \"permissionName\" : \"foo\"" + LS
        + " }, {" + LS
        + "    \"description\" : \"foo\"" + LS
        + " }]" + LS
        + "}";

    String msg = null;
    try {
      Json.decodeValue(docModuleDescriptor, ModuleDescriptor.class);
    } catch (DecodeException e) {
      msg = e.getMessage();
    }
    assertTrue(msg, msg.startsWith("Failed to decode:Missing permissionName"));
  }
}
