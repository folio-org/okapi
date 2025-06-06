package org.folio.okapi.bean;

import org.apache.logging.log4j.Logger;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@java.lang.SuppressWarnings({"squid:S1166", "squid:S1192", "squid:S1313"})
public class ModuleInterfaceTest {

  @Test
  public void simpleTests() {
    InterfaceDescriptor mi = new InterfaceDescriptor();
    // Test defaults
    String id = mi.getId();
    assertEquals(null, id);
    String ver = mi.getVersion();
    assertEquals(null, ver);
    mi.setId("idhere");
    assertEquals("idhere", mi.getId());
    mi.setVersion("1.2.3");
    assertEquals("1.2.3", mi.getVersion());
    mi = new InterfaceDescriptor("hello", "4.5.6");
    assertEquals("hello", mi.getId());
    assertEquals("4.5.6", mi.getVersion());
    try {
      mi = new InterfaceDescriptor("fail", "4.x");
      fail("Managed to set a bad version number 4.x");
    } catch (IllegalArgumentException e) {
      // no problem
    }
  }

  @Test
  public void validateTests() {
    assertFalse(InterfaceDescriptor.validateVersion("1"));
    assertFalse(InterfaceDescriptor.validateVersion("1."));
    assertTrue(InterfaceDescriptor.validateVersion("1.2"));
    assertTrue(InterfaceDescriptor.validateVersion("1.2."));
    assertTrue(InterfaceDescriptor.validateVersion("1.2.3"));
    assertTrue(InterfaceDescriptor.validateVersion("1.2.3."));
    assertFalse(InterfaceDescriptor.validateVersion("1.2.3.4")); //not an IP!
    assertFalse(InterfaceDescriptor.validateVersion("X"));
    assertFalse(InterfaceDescriptor.validateVersion("X.Y.X"));
    assertFalse(InterfaceDescriptor.validateVersion("1.2.*"));
    assertTrue(InterfaceDescriptor.validateVersion("1.2 2.3"));
    InterfaceDescriptor mi = new InterfaceDescriptor();
    try {
      mi.setVersion("1.2.3");
    } catch (IllegalArgumentException e) {
      fail("Failed to set version: " + e.getMessage());
    }
    try {
      mi.setVersion("XXX");
      fail("Managed to set a bad version number");
    } catch (IllegalArgumentException e) {
    }
  }

  @Test
  public void compatibilityTests() {
    InterfaceDescriptor a = new InterfaceDescriptor("m", "3.4.5");
    assertFalse(a.isCompatible(new InterfaceDescriptor("somethingelse", "3.4.5")));
    assertTrue(a.isCompatible(new InterfaceDescriptor("m", "3.4.5")));
    assertFalse(a.isCompatible(new InterfaceDescriptor("m", "2.1.9")));
    assertFalse(a.isCompatible(new InterfaceDescriptor("m", "2.1")));
    assertFalse(a.isCompatible(new InterfaceDescriptor("m", "9.1.9")));
    assertFalse(a.isCompatible(new InterfaceDescriptor("m", "9.1")));
    assertTrue(a.isCompatible(new InterfaceDescriptor("m", "3.4")));
    assertTrue(a.isCompatible(new InterfaceDescriptor("m", "3.3")));
    assertFalse(a.isCompatible(new InterfaceDescriptor("m", "3.5")));
    assertTrue(a.isCompatible(new InterfaceDescriptor("m", "3.4.1")));
    assertFalse(a.isCompatible(new InterfaceDescriptor("m", "3.4.6")));
    assertFalse(a.isCompatible(new InterfaceDescriptor("m", "2.9.2 3.4.6")));
    assertTrue(a.isCompatible(new InterfaceDescriptor("m", "2.9.2 3.4.4")));
    assertTrue(a.isCompatible(new InterfaceDescriptor("m", "3.4.4 2.9.2")));
    assertFalse(a.isCompatible(new InterfaceDescriptor("m", "2.9.2 3.4.6 4.0.0")));
  }

  @Test
  public void testPermissionsRequired() {
    Logger mockLogger = mock(Logger.class);
    String section = "provides";
    String mod = "test";
    String expected = "Missing field permissionsRequired";

    InterfaceDescriptor desc = new InterfaceDescriptor();
    desc.setId("a");
    desc.setVersion("1.0");
    assertTrue(desc.validate(mockLogger, section, mod).isEmpty());

    desc.setHandlers(new RoutingEntry[] {});
    assertTrue(desc.validate(mockLogger, section, mod).isEmpty());

    RoutingEntry entry = new RoutingEntry();
    desc.setHandlers(new RoutingEntry[] { entry });
    assertFalse(desc.validate(mockLogger, section, mod).isEmpty());

    desc.getHandlers()[0].setPathPattern("/pattern");
    assertTrue(desc.validate(mockLogger, section, mod).contains(expected));
    desc.getHandlers()[0].setPath("/path");
    assertTrue(desc.validate(mockLogger, section, mod).contains(expected));
    desc.getHandlers()[0].setPath(" ");
    assertTrue(desc.validate(mockLogger, section, mod).contains(expected));

    desc.getHandlers()[0].setPermissionsRequired(new String[] {});
    assertTrue(desc.validate(mockLogger, section, mod).isEmpty());

    desc.getHandlers()[0].setPermissionsRequired(new String[] { "perm.a" });
    assertTrue(desc.validate(mockLogger, section, mod).isEmpty());

    desc.getHandlers()[0].setPermissionsRequired(new String[] { "perm.b", "perm.c" });
    assertTrue(desc.validate(mockLogger, section, mod).isEmpty());
  }

  @Test
  public void testTenantPermissionsVersion() {
    Logger logger = mock(Logger.class);
    String section = "provides";
    String mod = "test";

    InterfaceDescriptor desc = new InterfaceDescriptor();
    desc.setId("_tenantPermissions");
    desc.setVersion("1.0");
    assertTrue(desc.validate(logger, section, mod).isEmpty());
    verify(logger, never()).warn(anyString());

    desc.setVersion("1.1");
    assertTrue(desc.validate(logger, section, mod).isEmpty());
    verify(logger, never()).warn(anyString());

    desc.setVersion("1.2");
    assertTrue(desc.validate(logger, section, mod).isEmpty());
    verify(logger, times(1)).warn(anyString(), anyString(), anyString());
  }
}
