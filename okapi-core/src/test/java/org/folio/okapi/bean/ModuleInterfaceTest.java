package org.folio.okapi.bean;

import io.vertx.core.logging.Logger;
import org.folio.okapi.common.OkapiLogger;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author heikki
 */
@java.lang.SuppressWarnings({"squid:S1166", "squid:S1192", "squid:S1313"})
public class ModuleInterfaceTest {

  private final Logger logger = OkapiLogger.get();

  @Test
  public void simpleTests() {
    logger.debug("simpleTests()");
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

    logger.debug("simpleTests() ok");
  }

  @Test
  public void validateTests() {
    logger.debug("validateTests()");
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
      logger.debug("Refused a bad version number 'XXX' as it should");
    }
    logger.debug("validateTests() ok");
  }

  @Test
  public void compatibilityTests() {
    logger.debug("compatibilityTests()");
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
    logger.debug("compatibilityTests() ok");
  }
}
