package org.folio.okapi.util;

import io.vertx.core.logging.Logger;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class GraphML {

  private static Logger logger = OkapiLogger.get();
  private static Messages messages = Messages.getInstance();

  static DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();

  private static String documentToString(Document document) {
    try {
      TransformerFactory tf = TransformerFactory.newInstance();
      Transformer trans = tf.newTransformer();
      trans.setOutputProperty(OutputKeys.INDENT, "yes");
      StringWriter sw = new StringWriter();
      trans.transform(new DOMSource(document), new StreamResult(sw));
      return sw.toString();
    } catch (TransformerException ex) {
      ex.printStackTrace();
    }
    return null;
  }

  public static String report(Map<String, ModuleDescriptor> modlist) {
    List<ModuleDescriptor> list = new LinkedList<>();
    list.addAll(modlist.values());
    return report(list);
  }

  public static String report(List<ModuleDescriptor> modlist) {
    try {
      Document doc = reportToDocument(modlist);
      return documentToString(doc);
    } catch (ParserConfigurationException ex) {
      ex.printStackTrace();
    }
    return null;
  }

  private static Document reportToDocument(List<ModuleDescriptor> modlist) throws ParserConfigurationException {
    DocumentBuilder builder = dbFactory.newDocumentBuilder();
    Document doc = builder.newDocument();
    final String ns = "http://graphml.graphdrawing.org/xmlns";
    Element graphmlElement = doc.createElementNS(ns, "graphml");
    doc.appendChild(graphmlElement);
    Element graphElement = doc.createElementNS(ns, "graph");
    graphElement.setAttribute("id", "G");
    graphElement.setAttribute("edgedefault", "directed");
    graphmlElement.appendChild(graphElement);

    Element nodeKey = doc.createElement("key");
    nodeKey.setAttribute("id", "module");
    nodeKey.setAttribute("for", "node");
    nodeKey.setAttribute("attr.name", "color");
    nodeKey.setAttribute("attr.type", "string");
    graphElement.appendChild(nodeKey);

    Element edgeKey = doc.createElement("key");
    edgeKey.setAttribute("id", "interface");
    edgeKey.setAttribute("for", "edge");
    edgeKey.setAttribute("attr.name", "weight");
    edgeKey.setAttribute("attr.type", "string");
    graphElement.appendChild(edgeKey);

    for (ModuleDescriptor md : modlist) {
      Element modElement = doc.createElementNS(ns, "node");
      modElement.setAttribute("id", md.getId());
      graphElement.appendChild(modElement);
    }
    Set<String> pseudoNodes = new TreeSet<>();
    for (ModuleDescriptor tmd : modlist) {
      for (InterfaceDescriptor req : tmd.getRequiresList()) {
        int number = 0;
        for (ModuleDescriptor smd : modlist) {
          for (InterfaceDescriptor pi : smd.getProvidesList()) {
            if (req.getId().equals(pi.getId()) && pi.isCompatible(req)) {
              Element edgeElement = doc.createElementNS(ns, "edge");
              edgeElement.setAttribute("id", req.getId() + "-" + pi.getVersion());
              edgeElement.setAttribute("target", tmd.getId());
              edgeElement.setAttribute("source", smd.getId());
              graphElement.appendChild(edgeElement);

              Element dataElement = doc.createElementNS(ns, "data");
              dataElement.setAttribute("key", "interface");
              dataElement.appendChild(doc.createTextNode(req.getId() + " " + pi.getVersion()));
              edgeElement.appendChild(dataElement);

              number++;
            }
          }
        }
        if (number == 0) {
          final String k = "missing-" + req.getId();
          if (!pseudoNodes.contains(k)) {
            pseudoNodes.add(k);
            Element modElement = doc.createElementNS(ns, "node");
            modElement.setAttribute("id", k);
            graphElement.appendChild(modElement);
          }
          Element edgeElement = doc.createElementNS(ns, "edge");
          edgeElement.setAttribute("id", req.getId() + "-" + req.getVersion());
          edgeElement.setAttribute("target", tmd.getId());
          edgeElement.setAttribute("source", k);
          graphElement.appendChild(edgeElement);

          Element dataElement = doc.createElementNS(ns, "data");
          dataElement.setAttribute("key", "interface");
          dataElement.appendChild(doc.createTextNode(req.getId() + " " + req.getVersion()));
          edgeElement.appendChild(dataElement);
        }
      }
    }
    return doc;
  }

}
