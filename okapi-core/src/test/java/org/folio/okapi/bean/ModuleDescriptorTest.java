package org.folio.okapi.bean;

import static org.junit.Assert.*;

import org.junit.Test;

import io.vertx.core.json.Json;

public class ModuleDescriptorTest {

  @Test
  public void testGetExpandedPermissionsSets() {

    // no handlers and no filters
    String modId = "test-1.0.0";
    ModuleDescriptor md = new ModuleDescriptor();
    md.setId(modId);
    assertNull(md.getExpandedPermissionSets());

    // empty filter
    md.setFilters(new RoutingEntry[] {});
    assertNull(md.getExpandedPermissionSets());

    // empty handlers
    md.setProvides(new InterfaceDescriptor[] {});
    assertNull(md.getExpandedPermissionSets());

    InterfaceDescriptor id = new InterfaceDescriptor();
    id.setId("test");
    id.setVersion("1.0");
    md.setProvides(new InterfaceDescriptor[] { id });
    assertNull(md.getExpandedPermissionSets());

    id.setHandlers(new RoutingEntry[] {});
    assertNull(md.getExpandedPermissionSets());

    // handler without module permission
    RoutingEntry handler = new RoutingEntry();
    handler.setPathPattern("/abc");
    handler.setMethods(new String[] { "GET", "POST" });
    id.setHandlers(new RoutingEntry[] { handler });
    assertNull(md.getExpandedPermissionSets());

    // filter without permission
    RoutingEntry filter = new RoutingEntry();
    filter.setPathPattern("/*");
    filter.setMethods(new String[] { "*" });
    md.setFilters(new RoutingEntry[] { filter });
    assertNull(md.getExpandedPermissionSets());

    // empty module permissions
    handler.setModulePermissions(new String[] { });
    id.setHandlers(new RoutingEntry[] { handler });
    filter.setModulePermissions(new String[] { });
    md.setFilters(new RoutingEntry[] { filter });
    assertNull(md.getExpandedPermissionSets());

    // add module permissions
    handler.setModulePermissions(new String[] { "handler.read", "handler.write" });
    id.setHandlers(new RoutingEntry[] { handler });
    filter.setModulePermissions(new String[] { "auth.check" });
    md.setFilters(new RoutingEntry[] { filter });

    Permission[] perms = md.getExpandedPermissionSets();
    String handlerPermName = handler.generateSystemId(modId);
    String filterPermName = filter.generateSystemId(modId);
    for (Permission perm : perms) {
      assertFalse(perm.getVisible());
      if (handlerPermName.contentEquals(perm.getPermissionName())) {
        assertEquals("System generated: " + handlerPermName, perm.getDisplayName());
        assertEquals("System generated permission set", perm.getDescription());
        assertEquals(2, perm.getSubPermissions().length);
        assertEquals("handler.read", perm.getSubPermissions()[0]);
        assertEquals("handler.write", perm.getSubPermissions()[1]);
      } else if (filterPermName.contentEquals(perm.getPermissionName())) {
        assertEquals("System generated: " + filterPermName, perm.getDisplayName());
        assertEquals("System generated permission set", perm.getDescription());
        assertEquals(1, perm.getSubPermissions().length);
        assertEquals("auth.check", perm.getSubPermissions()[0]);
      } else {
        fail("No other permission set should be defined: " + Json.encode(perm));
      }
    }

    // add regular permission sets
    Permission perm = new Permission();
    perm.setPermissionName("regular");
    md.setPermissionSets(new Permission[] { perm });
    perms = md.getExpandedPermissionSets();
    assertEquals(3, perms.length);
    assertTrue(Json.encode(perms).contains("regular"));
  }

  @Test
  public void testConstructorWithId() {
    ModuleDescriptor md = new ModuleDescriptor("foo-1.2.3");
    assertEquals("foo-1.2.3", md.getId());
    assertEquals("foo", md.getProduct());
  }
}
