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

public class GraphMLTest {

  InterfaceDescriptor int10 = new InterfaceDescriptor("int", "1.0");
  InterfaceDescriptor[] int10a = {int10};
  InterfaceDescriptor int11 = new InterfaceDescriptor("int", "1.1");
  InterfaceDescriptor[] int11a = {int11};
  InterfaceDescriptor int20 = new InterfaceDescriptor("int", "2.0");
  InterfaceDescriptor[] int20a = {int20};

  public GraphMLTest() {
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
    Map<String, ModuleDescriptor> modlist = new LinkedHashMap();

    String s = GraphML.report(modlist);
    System.out.println(s);
    assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
      + "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">\n"
      + "<graph edgedefault=\"directed\" id=\"G\">\n"
      + "<key attr.name=\"color\" attr.type=\"string\" for=\"node\" id=\"module\"/>\n"
      + "<key attr.name=\"weight\" attr.type=\"string\" for=\"edge\" id=\"interface\"/>\n"
      + "</graph>\n"
      + "</graphml>\n", s);
  }

  @Test
  public void testTwoResolvable() {
    Map<String, ModuleDescriptor> modlist = new LinkedHashMap();

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

    String s = GraphML.report(modlist);
    System.out.println(s);
    assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
      + "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">\n"
      + "<graph edgedefault=\"directed\" id=\"G\">\n"
      + "<key attr.name=\"color\" attr.type=\"string\" for=\"node\" id=\"module\"/>\n"
      + "<key attr.name=\"weight\" attr.type=\"string\" for=\"edge\" id=\"interface\"/>\n"
      + "<node id=\"mod-a-1.0.0\"/>\n"
      + "<node id=\"mod-b-1.0.0\"/>\n"
      + "<edge id=\"int-1.1\" source=\"mod-a-1.0.0\" target=\"mod-b-1.0.0\">\n"
      + "<data key=\"interface\">int 1.1</data>\n"
      + "</edge>\n"
      + "</graph>\n"
      + "</graphml>\n", s);
  }

  @Test
  public void testTwoUnResolvable() {
    Map<String, ModuleDescriptor> modlist = new LinkedHashMap();

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

    String s = GraphML.report(modlist);
    System.out.println(s);
    assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
      + "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">\n"
      + "<graph edgedefault=\"directed\" id=\"G\">\n"
      + "<key attr.name=\"color\" attr.type=\"string\" for=\"node\" id=\"module\"/>\n"
      + "<key attr.name=\"weight\" attr.type=\"string\" for=\"edge\" id=\"interface\"/>\n"
      + "<node id=\"mod-a-1.0.0\"/>\n"
      + "<node id=\"mod-b-1.0.0\"/>\n"
      + "<node id=\"missing-int\"/>\n"
      + "<edge id=\"int-1.1\" source=\"missing-int\" target=\"mod-b-1.0.0\">\n"
      + "<data key=\"interface\">int 1.1</data>\n"
      + "</edge>\n"
      + "</graph>\n"
      + "</graphml>\n", s);
  }

  @Test
  public void testOneUnResolvable() {
    Map<String, ModuleDescriptor> modlist = new LinkedHashMap();

    {
      ModuleDescriptor md = new ModuleDescriptor();
      md.setId("mod-a-1.0.0");
      md.setRequires(int11a);
      modlist.put(md.getId(), md);
    }

    String s = GraphML.report(modlist);
    System.out.println(s);
    assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
      + "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">\n"
      + "<graph edgedefault=\"directed\" id=\"G\">\n"
      + "<key attr.name=\"color\" attr.type=\"string\" for=\"node\" id=\"module\"/>\n"
      + "<key attr.name=\"weight\" attr.type=\"string\" for=\"edge\" id=\"interface\"/>\n"
      + "<node id=\"mod-a-1.0.0\"/>\n"
      + "<node id=\"missing-int\"/>\n"
      + "<edge id=\"int-1.1\" source=\"missing-int\" target=\"mod-a-1.0.0\">\n"
      + "<data key=\"interface\">int 1.1</data>\n"
      + "</edge>\n"
      + "</graph>\n"
      + "</graphml>\n", s);
  }

}
