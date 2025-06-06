package org.folio.okapi.util;

import java.util.LinkedHashMap;
import java.util.Map;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class GraphDotTest {

  InterfaceDescriptor int10 = new InterfaceDescriptor("int", "1.0");
  InterfaceDescriptor[] int10a = {int10};
  InterfaceDescriptor int11 = new InterfaceDescriptor("int", "1.1");
  InterfaceDescriptor[] int11a = {int11};
  InterfaceDescriptor int20 = new InterfaceDescriptor("int", "2.0");
  InterfaceDescriptor[] int20a = {int20};

  public GraphDotTest() {
  }

  @BeforeClass
  public static void setUpClass() {
  }

  @AfterClass
  public static void tearDownClass() {
  }

  @Before
  public void setUp() {
  }

  @After
  public void tearDown() {
  }

  @Test
  public void testEmpty() {
    Map<String, ModuleDescriptor> modlist = new LinkedHashMap<>();

    String s = GraphDot.report(modlist);
    System.out.println(s);
    assertEquals("digraph okapi {\n}\n", s);
  }

  @Test
  public void testTwoResolvable() {
    Map<String, ModuleDescriptor> modlist = new LinkedHashMap<>();

    {
      ModuleDescriptor md = new ModuleDescriptor();
      md.setId("mod-a-1.0.0");
      md.setProvides(int11a);
      modlist.put(md.getId(), md);

    }

    {
      ModuleDescriptor md = new ModuleDescriptor();
      md.setId("mod-b-1.0.0");
      md.setRequires(int10a);
      modlist.put(md.getId(), md);
    }

    String s = GraphDot.report(modlist);
    System.out.println(s);
    assertEquals("digraph okapi {\n"
      + "  mod__a__1_0_0 [label=\"mod-a-1.0.0\"];\n"
      + "  mod__b__1_0_0 [label=\"mod-b-1.0.0\"];\n"
      + "  mod__b__1_0_0 -> mod__a__1_0_0;\n"
      + "}\n", s);
  }

  @Test
  public void testTwoUnResolvable() {
    Map<String, ModuleDescriptor> modlist = new LinkedHashMap<>();

    {
      ModuleDescriptor md = new ModuleDescriptor();
      md.setId("mod-a-1.0.0");
      md.setProvides(int10a);
      modlist.put(md.getId(), md);

    }

    {
      ModuleDescriptor md = new ModuleDescriptor();
      md.setId("mod-b-1.0.0");
      md.setRequires(int11a);
      modlist.put(md.getId(), md);
    }

    String s = GraphDot.report(modlist);
    System.out.println(s);
    assertEquals("digraph okapi {\n"
      + "  mod__a__1_0_0 [label=\"mod-a-1.0.0\"];\n"
      + "  mod__b__1_0_0 [label=\"mod-b-1.0.0\"];\n"
      + "  missing_int_1_1 [label=\"missing int 1.1\", color=red];\n"
      + "  mod__b__1_0_0 -> missing_int_1_1;\n"
      + "}\n", s);
  }

  @Test
  public void testOneUnResolvable() {
    Map<String, ModuleDescriptor> modlist = new LinkedHashMap<>();

    {
      ModuleDescriptor md = new ModuleDescriptor();
      md.setId("mod-a-1.0.0");
      md.setRequires(int11a);
      modlist.put(md.getId(), md);
    }

    String s = GraphDot.report(modlist);
    System.out.println(s);
    assertEquals("digraph okapi {\n"
      + "  mod__a__1_0_0 [label=\"mod-a-1.0.0\"];\n"
      + "  missing_int_1_1 [label=\"missing int 1.1\", color=red];\n"
      + "  mod__a__1_0_0 -> missing_int_1_1;\n"
      + "}\n", s);
  }

}
