package org.folio.okapi.common;

import java.util.Comparator;
import java.util.List;
import org.z3950.zing.cql.CQLAndNode;
import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLNotNode;
import org.z3950.zing.cql.CQLOrNode;
import org.z3950.zing.cql.CQLPrefix;
import org.z3950.zing.cql.CQLPrefixNode;
import org.z3950.zing.cql.CQLProxNode;
import org.z3950.zing.cql.CQLSortNode;
import org.z3950.zing.cql.CQLTermNode;
import org.z3950.zing.cql.Modifier;
import org.z3950.zing.cql.ModifierSet;

public class CqlUtil {
  private CqlUtil() {
    throw new IllegalStateException("CQLUtil");
  }

  /**
   * Evaluate CQL tree with limit.
   * @param vn1 The CQL query
   * @param tn Term node that is used for comparison
   * @param cmp Comparison handler
   * @return true if query is not limited by tn+cmp; false if query is limited
   */
  public static boolean eval(CQLNode vn1, CQLTermNode tn, Comparator<CQLTermNode> cmp) {
    if (vn1 instanceof CQLBooleanNode) {
      CQLBooleanNode n1 = (CQLBooleanNode) vn1;
      switch (n1.getOperator()) {
        case AND:
        case PROX:
          return eval(n1.getLeftOperand(), tn, cmp) && eval(n1.getRightOperand(), tn, cmp);
        case OR:
          return eval(n1.getLeftOperand(), tn, cmp) || eval(n1.getRightOperand(), tn, cmp);
        case NOT:
          return eval(n1.getLeftOperand(), tn, cmp);
        default:
          throw new IllegalArgumentException("unknown operator for CQLBooleanNode: "
            + n1.getOperator());
      }
    } else if (vn1 instanceof CQLTermNode) {
      CQLTermNode n1 = (CQLTermNode) vn1;
      return cmp.compare(n1, tn) == 0;
    } else if (vn1 instanceof CQLSortNode) {
      CQLSortNode n1 = (CQLSortNode) vn1;
      return eval(n1.getSubtree(), tn, cmp);
    } else if (vn1 instanceof CQLPrefixNode) {
      CQLPrefixNode n1 = (CQLPrefixNode) vn1;
      return eval(n1.getSubtree(), tn, cmp);
    } else {
      throw new IllegalArgumentException("unknown type for CQLNode: " + vn1.toString());
    }
  }

  /**
   * Impose limit on CQL query.
   * @param vn1 CQL query tree
   * @param tn Term node that is used for comparison
   * @param cmp Comparison handler
   * @return simplified query or null if limit makes query empty
   */
  public static CQLNode reducer(CQLNode vn1, CQLTermNode tn, Comparator<CQLTermNode> cmp) {
    if (vn1 instanceof CQLBooleanNode) {
      return reduceBoolean((CQLBooleanNode) vn1, tn, cmp);
    } else if (vn1 instanceof CQLTermNode) {
      CQLTermNode n1 = (CQLTermNode) vn1;
      if (cmp != null && cmp.compare(n1, tn) == 0) {
        return null;
      }
      return new CQLTermNode(n1.getIndex(), n1.getRelation(), n1.getTerm());
    } else if (vn1 instanceof CQLSortNode) {
      CQLSortNode n1 = (CQLSortNode) vn1;
      CQLNode n2 = reducer(n1.getSubtree(), tn, cmp);
      if (n2 == null) {
        return null;
      } else {
        CQLSortNode sn = new CQLSortNode(n2);
        List<ModifierSet> mods = n1.getSortIndexes();
        for (ModifierSet mset : mods) {
          sn.addSortIndex(mset);
        }
        return sn;
      }
    } else if (vn1 instanceof CQLPrefixNode) {
      CQLPrefixNode n1 = (CQLPrefixNode) vn1;
      CQLNode n2 = reducer(n1.getSubtree(), tn, cmp);
      if (n2 == null) {
        return null;
      } else {
        CQLPrefix prefix = n1.getPrefix();
        return new CQLPrefixNode(prefix.getName(), prefix.getIdentifier(), n2);
      }
    } else {
      throw new IllegalArgumentException("unknown type for CQLNode: "
        + vn1.toString());
    }
  }

  private static CQLNode reduceBoolean(CQLBooleanNode n1, CQLTermNode tn,
                                       Comparator<CQLTermNode> cmp) {

    CQLNode n2 = null;
    CQLNode left = reducer(n1.getLeftOperand(), tn, cmp);
    CQLNode right = reducer(n1.getRightOperand(), tn, cmp);

    ModifierSet mset = new ModifierSet(n1.getOperator().toString().toLowerCase());
    List<Modifier> mods = n1.getModifiers();
    for (Modifier m : mods) {
      mset.addModifier(m.getType(), m.getComparison(), m.getValue());
    }
    if (left == null) {
      n2 = right;
    } else if (right == null) {
      n2 = left;
    }
    switch (n1.getOperator()) {
      case AND:
        if (left != null && right != null) {
          n2 = new CQLAndNode(left, right, mset);
        }
        break;
      case OR:
        if (left != null && right != null) {
          n2 = new CQLOrNode(left, right, mset);
        }
        break;
      case NOT:
        if (left != null && right != null) {
          n2 = new CQLNotNode(left, right, mset);
        }
        break;
      case PROX:
        if (left != null && right != null) {
          n2 = new CQLProxNode(left, right, mset);
        }
        break;
      default:
    }
    return n2;
  }
}
