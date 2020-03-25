package org.folio.okapi.util;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;

public class GraphDot {
  private GraphDot() {
    throw new IllegalAccessError("GraphDot");
  }

  /**
   * Produce module graph in DOT format with dependencies.
   * @param modList list of modules in graph
   * @return graph in DOT format
   */
  public static String report(Map<String, ModuleDescriptor> modList) {
    List<ModuleDescriptor> list = new LinkedList<>(modList.values());
    return report(list);
  }

  /**
   * Produce module graph in DOT format with dependencies.
   * @param modList list of modules in graph
   * @return graph in DOT format
   */
  public static String report(List<ModuleDescriptor> modList) {
    StringBuilder doc = new StringBuilder();
    doc.append("digraph okapi {\n");
    for (ModuleDescriptor md : modList) {
      doc.append("  " + encodeDotId(md.getId()) + " [label=\"" + md.getId() + "\"];\n");
    }
    Set<String> pseudoNodes = new TreeSet<>();
    for (ModuleDescriptor tmd : modList) {
      for (InterfaceDescriptor req : tmd.getRequiresList()) {
        int number = 0;
        for (ModuleDescriptor smd : modList) {
          for (InterfaceDescriptor pi : smd.getProvidesList()) {
            if (pi.isRegularHandler()
                && req.getId().equals(pi.getId()) && pi.isCompatible(req)) {
              doc.append("  " + encodeDotId(tmd.getId())
                  + " -> " + encodeDotId(smd.getId()) + ";\n");
              number++;
            }
          }
        }
        if (number == 0) {
          final String k = "missing " + req.getId() + " " + req.getVersion();
          if (!pseudoNodes.contains(k)) {
            pseudoNodes.add(k);
            doc.append("  " + encodeDotId(k) + " [label=\"" + k + "\", color=red];\n");
          }
          doc.append("  " + encodeDotId(tmd.getId()) + " -> " + encodeDotId(k) + ";\n");
        }
      }
    }
    doc.append("}\n");
    return doc.toString();
  }

  private static String encodeDotId(String s) {
    return s.replace("-", "__").replace(".", "_").replace(' ', '_');
  }
}
