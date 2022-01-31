package org.folio.okapi.managers;

import static org.folio.okapi.ConfNames.KUBE_CONFIG;
import static org.folio.okapi.ConfNames.KUBE_NAMESPACE;
import static org.folio.okapi.ConfNames.KUBE_SERVER;
import static org.folio.okapi.ConfNames.KUBE_TOKEN;

import io.vertx.config.yaml.YamlProcessor;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.Config;
import org.folio.okapi.common.ModuleId;
import org.folio.okapi.common.OkapiLogger;

public class KubernetesManager {

  private final Logger logger = OkapiLogger.get();
  final String fname;
  String token;
  String server;
  final String namespace;
  WebClient webClient;
  final DiscoveryManager discoveryManager;

  /**
   * Construct Kubernetes manager.
   * @param config configuration.
   */
  public KubernetesManager(DiscoveryManager discoveryManager, JsonObject config) {
    this.discoveryManager = discoveryManager;
    fname = Config.getSysConf(KUBE_CONFIG, null, config);
    token = Config.getSysConf(KUBE_TOKEN, null, config);
    server = Config.getSysConf(KUBE_SERVER, null, config);
    namespace = Config.getSysConf(KUBE_NAMESPACE, "default", config);
  }

  JsonObject findNameInJsonArray(JsonObject o, String arName, String name) {
    JsonArray ar = o.getJsonArray(arName + "s");
    for (int i = 0; i < ar.size(); i++) {
      if (ar.getJsonObject(i).getString("name").equals(name)) {
        return ar.getJsonObject(i).getJsonObject(arName);
      }
    }
    throw new RuntimeException("No property with name '" + name + "' in array '" + arName + "s'");
  }

  Future<Void> readKubeConfig(Vertx vertx, String fname) {
    if (fname == null) {
      return Future.succeededFuture();
    }
    FileSystem fs = vertx.fileSystem();
    return fs.readFile(fname).compose(content -> {
      YamlProcessor yamlProcessor = new YamlProcessor();
      return yamlProcessor.process(vertx, null, content);
    }).map(conf -> {
      JsonObject context = findNameInJsonArray(conf, "context", conf.getString("current-context"));
      if (token == null) {
        JsonObject user = findNameInJsonArray(conf, "user", context.getString("user"));
        token = user.getString("token");
      }
      if (server == null) {
        JsonObject cluster = findNameInJsonArray(conf, "cluster", context.getString("cluster"));
        server = cluster.getString("server");
      }
      return null;
    });
  }

  /**
   * async initialization of the manager.
   * @param vertx Vert.x handle
   * @return future result.
   */
  public Future<Void> init(Vertx vertx) {
    WebClientOptions webClientOptions = new WebClientOptions()
        .setVerifyHost(false)
        .setTrustAll(true);
    webClient = WebClient.create(vertx, webClientOptions);

    return readKubeConfig(vertx, fname).onSuccess(x -> {
      if (server != null) {
        logger.info("Enable Kubernetes config server {} namespace {}", server, namespace);
      }
    });
  }

  JsonObject parseService(JsonObject item) {
    try {
      JsonObject metadata = item.getJsonObject("metadata");
      JsonObject labels = metadata.getJsonObject("labels");
      String name = labels.getString("app.kubernetes.io/name");
      if (name == null) {
        logger.warn("No app.kubernetes.io/name property for {}",
            metadata.encodePrettily());
        return null;
      }
      String version = labels.getString("app.kubernetes.io/version");
      if (version == null) {
        logger.warn("No app.kubernetes.io/version property");
        return null;
      }
      ModuleId moduleId = new ModuleId(name + "-" + version);
      JsonArray urls = new JsonArray();
      JsonObject spec = item.getJsonObject("spec");
      JsonArray ports = spec.getJsonArray("ports");
      if (!ports.isEmpty()) {
        JsonObject port = ports.getJsonObject(0);
        String transport = port.getString("name");
        if (!"http".equals(transport) && !"https".equals(transport)) {
          transport = "http";
        }
        Integer portNumber = port.getInteger("port");
        JsonArray clusterIPs = spec.getJsonArray("clusterIPs");
        for (int k = 0; k < clusterIPs.size(); k++) {
          urls.add(transport + "://" + clusterIPs.getString(k) + ":" + portNumber);
        }
      }
      return new JsonObject().put("id", moduleId.toString()).put("urls", urls);
    } catch (Exception e) {
      logger.warn("Parsing item {} resulted in {}", item.encodePrettily(), e.getMessage(), e);
      return null;
    }
  }

  JsonArray parseServices(JsonObject response) {
    JsonArray res = new JsonArray();
    JsonArray items = response.getJsonArray("items");
    for (int i = 0; i < items.size(); i++) {
      JsonObject moduleItem = parseService(items.getJsonObject(i));
      if (moduleItem != null) {
        res.add(moduleItem);
      }
    }
    return res;
  }

  /**
   * Get Services from Kubernetes cluster.
   * @return endpoints in JSON object.
   */
  public Future<JsonArray> getServices() {
    if (server == null) {
      return Future.succeededFuture(new JsonArray());
    }
    String uri = server + "/api/v1/namespaces/" + namespace + "/services";
    HttpRequest<Buffer> abs = webClient.getAbs(uri);
    if (token != null) {
      abs.putHeader("Authorization", "Bearer " + token);
    }
    return abs.putHeader("Accept", "application/json")
        .expect(ResponsePredicate.SC_OK)
        .send().map(res -> parseServices(res.bodyAsJsonObject()));
  }

}
