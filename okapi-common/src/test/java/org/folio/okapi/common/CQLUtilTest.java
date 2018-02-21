package org.folio.okapi.common;

import java.util.Comparator;
import org.junit.Test;
import static org.junit.Assert.*;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLRelation;
import org.z3950.zing.cql.CQLTermNode;

public class CQLUtilTest {

  private boolean eval(String input) {
    CQLParser parser = new CQLParser(CQLParser.V1POINT2);
    try {
      CQLRelation rel = new CQLRelation("=");
      CQLTermNode source = new CQLTermNode("source", rel, "kb");
      CQLNode top = parser.parse(input);
      Comparator<CQLTermNode> f = (CQLTermNode n1, CQLTermNode n2) -> {
        if (n1.getIndex().equals(n2.getIndex()) && !n1.getTerm().equals(n2.getTerm())) {
          return -1;
        }
        return 0;
      };
      return CQLUtil.eval(top, source, f);
    } catch (Exception ex) {
      return false;
    }
  }

  @Test
  public void testEval() {
    assertTrue(eval("a"));
    assertTrue(eval("a sortby title"));
    assertTrue(eval(">dc = \"xx\" a sortby title"));
    assertTrue(eval(">dc = \"xx\" a sortby title"));
    assertFalse(eval("source=x and x"));
    assertTrue(eval("source=x or x"));
    assertTrue(eval("source=kb and x"));
    assertTrue(eval("(source=kb or source=other) and x"));
    assertFalse(eval("(source=foo or source=bar) and x"));
    assertFalse(eval("a and source=x sortby title"));
    assertTrue(eval("(a and source=x) or (b and source=kb) sortby title"));
    assertTrue(eval("a not b"));
    assertTrue(eval("source=kb and a not b"));
    assertFalse(eval("source=x and a not b"));
  }

  private String reduce(String input) {
    CQLParser parser = new CQLParser(CQLParser.V1POINT2);
    try {
      CQLRelation rel = new CQLRelation("=");
      CQLTermNode source = new CQLTermNode("source", rel, "kb");
      CQLNode top = parser.parse(input);
      Comparator<CQLTermNode> f = (CQLTermNode n1, CQLTermNode n2)
        -> n1.getIndex().equals(n2.getIndex()) ? 0 : -1;
      CQLNode res = CQLUtil.reducer(top, source, f);
      if (res == null) {
        return "null";
      } else {
        return res.toCQL();
      }
    } catch (Exception ex) {
      return "Error: " + ex.getMessage();
    }
  }

  @Test
  public void testReduce() {

    assertEquals("a", reduce("a"));
    assertEquals("(a) and (b)", reduce("a and source=x and b"));
    assertEquals("(a) or (b)", reduce("a and source=x or (source=y and b)"));
    assertEquals("(a) and (b)", reduce("(a and source=x or source=y) and b"));
    assertEquals("(a) and (b)", reduce("a and source=x or source=y and b"));
    assertEquals("a sortby title", reduce("source=x and a sortby title"));
    assertEquals("null", reduce("source=x"));
    assertEquals("null", reduce("source=x sortby title"));
    assertEquals("(a) not (b)", reduce("a not b"));
    assertEquals("a", reduce("a not source=x"));
    assertEquals("a", reduce("source=x not a"));
    assertEquals("null", reduce("source=x not source=y"));
    assertEquals("(a) prox/distance = 1 (b)", reduce("a prox/distance=1 b"));
    assertEquals("a", reduce("a prox/distance=1 source=x"));
    assertEquals("a", reduce("source=x prox/distance=1 a"));
    assertEquals("null", reduce("source=x prox/distance=1 source=y"));
    assertEquals(">dc=\"xx\" (a)", reduce(">dc = \"xx\" a"));
    assertEquals("null", reduce(">dc = \"xx\" source=x"));
  }
}
