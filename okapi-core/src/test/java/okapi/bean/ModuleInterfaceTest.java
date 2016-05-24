/*
 * Copyright (C) 2016 Index Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okapi.bean;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author heikki
 */
public class ModuleInterfaceTest {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  public ModuleInterfaceTest() {
  }

  @Test
  public void simpleTests() {
    logger.debug("simpleTests()");
    ModuleInterface mi = new ModuleInterface();
    // Test defaults
    String id = mi.getId();
    assertEquals(null, id);
    String ver = mi.getVersion();
    assertEquals(null, ver);
    mi.setId("idhere");
    assertEquals("idhere", mi.getId());
    mi.setVersion("1.2.3");
    assertEquals("1.2.3", mi.getVersion());
    mi = new ModuleInterface("hello", "4.5.6");
    assertEquals("hello", mi.getId());
    assertEquals("4.5.6", mi.getVersion());
    try {
      mi = new ModuleInterface("fail", "4.x");
      fail("Managed to set a bad version number 4.x");
    } catch (IllegalArgumentException e) {
      // no problem
    }

    logger.debug("simpleTests() ok");
  }

  @Test
  public void validateTests() {
    logger.debug("validateTests()");
    assertFalse(ModuleInterface.validateVersion("1"));
    assertFalse(ModuleInterface.validateVersion("1."));
    assertTrue(ModuleInterface.validateVersion("1.2"));
    assertFalse(ModuleInterface.validateVersion("1.2."));
    assertTrue(ModuleInterface.validateVersion("1.2.3"));
    assertFalse(ModuleInterface.validateVersion("1.2.3."));
    assertFalse(ModuleInterface.validateVersion("1.2.3.4"));
    assertFalse(ModuleInterface.validateVersion("X"));
    assertFalse(ModuleInterface.validateVersion("X.Y.X"));
    assertFalse(ModuleInterface.validateVersion("1.2.*"));
    ModuleInterface mi = new ModuleInterface();
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
  public void splitTests() {
    logger.debug("splitTests()");
    ModuleInterface mi = new ModuleInterface();
    mi.setVersion("7.8.9");
    assertEquals(7, mi.majorInterfaceVersion());
    assertEquals(8, mi.minorInterfaceVersion());
    assertEquals(9, mi.softwareVersion());
    logger.debug("splitTests() ok");
  }

  @Test
  public void compatibilityTests() {
    logger.debug("compatibilityTests()");
    ModuleInterface a = new ModuleInterface("m", "3.4.5");
    assertFalse(a.isCompatible(new ModuleInterface("somethingelse", "3.4.5")));
    assertTrue(a.isCompatible(new ModuleInterface("m", "3.4.5")));
    assertFalse(a.isCompatible(new ModuleInterface("m", "2.1.9")));
    assertFalse(a.isCompatible(new ModuleInterface("m", "2.1")));
    assertFalse(a.isCompatible(new ModuleInterface("m", "9.1.9")));
    assertFalse(a.isCompatible(new ModuleInterface("m", "9.1")));
    assertTrue(a.isCompatible(new ModuleInterface("m", "3.4")));
    assertTrue(a.isCompatible(new ModuleInterface("m", "3.3")));
    assertFalse(a.isCompatible(new ModuleInterface("m", "3.5")));
    assertTrue(a.isCompatible(new ModuleInterface("m", "3.4.1")));
    assertFalse(a.isCompatible(new ModuleInterface("m", "3.4.6")));
    logger.debug("compatibilityTests() ok");
  }

  @Test
  public void compareTests() {
    logger.debug("compareTests()");
    ModuleInterface mi = new ModuleInterface("m", "3.4.5");
    assertEquals(0, mi.compare(new ModuleInterface("m", "3.4.5")));
    assertEquals(-1, mi.compare(new ModuleInterface("s", "3.4.5")));
    assertEquals(1, mi.compare(new ModuleInterface("d", "3.4.5")));
    assertEquals(-2, mi.compare(new ModuleInterface("m", "5.4.5")));
    assertEquals(2, mi.compare(new ModuleInterface("m", "2.4.5")));
    assertEquals(-3, mi.compare(new ModuleInterface("m", "3.7.5")));
    assertEquals(3, mi.compare(new ModuleInterface("m", "3.2.5")));
    assertEquals(-4, mi.compare(new ModuleInterface("m", "3.4.8")));
    assertEquals(4, mi.compare(new ModuleInterface("m", "3.4.2")));
    assertEquals(4, mi.compare(new ModuleInterface("m", "3.4")));
    logger.debug("compareTests() ok");
  }

}
