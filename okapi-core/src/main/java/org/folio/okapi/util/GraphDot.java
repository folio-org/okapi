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

  public static String report(Map<String, ModuleDescriptor> modlist) {
    List<ModuleDescriptor> list = new LinkedList<>();
    list.addAll(modlist.values());
    return report(list);
  }

  private static String encodeDotId(String s) {
    return s.replace("-", "__").replace(".", "_").replace(' ', '_');
  }

  public static String report(List<ModuleDescriptor> modlist) {
    StringBuilder doc = new StringBuilder();

    doc.append("digraph okapi {\n");
    for (ModuleDescriptor md : modlist) {
      doc.append("  " + encodeDotId(md.getId()) + " [label=\"" + md.getId() + "\"];\n");
    }
    Set<String> pseudoNodes = new TreeSet<>();
    for (ModuleDescriptor tmd : modlist) {
      for (InterfaceDescriptor req : tmd.getRequiresList()) {
        int number = 0;
        for (ModuleDescriptor smd : modlist) {
          for (InterfaceDescriptor pi : smd.getProvidesList()) {
            if (req.getId().equals(pi.getId()) && pi.isCompatible(req)) {
              doc.append("  " + encodeDotId(tmd.getId()) + " -> " + encodeDotId(smd.getId()) + ";\n");
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
}
