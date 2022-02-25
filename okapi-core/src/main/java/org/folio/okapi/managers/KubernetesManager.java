package org.folio.okapi.managers;

import io.vertx.config.yaml.YamlProcessor;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.ConfNames;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.common.Config;
import org.folio.okapi.common.ModuleId;
import org.folio.okapi.common.OkapiLogger;

public class KubernetesManager {

  private static final Logger logger = OkapiLogger.get();

  static final String KUBE_INST_PREFIX = "kube_";
  final String fname;
  int refreshInterval;
  String token;
  String server;
  final String namespace;
  WebClient webClient;
  final WebClientOptions webClientOptions;
  final DiscoveryManager discoveryManager;

  /**
   * Construct Kubernetes manager.
   * @param config configuration.
   */
  public KubernetesManager(DiscoveryManager discoveryManager, JsonObject config) {
    this.discoveryManager = discoveryManager;
    refreshInterval = Config.getSysConfInteger(ConfNames.KUBE_REFRESH_INTERVAL, 30000, config);
    fname = Config.getSysConf(ConfNames.KUBE_CONFIG, null, config);
    token = Config.getSysConf(ConfNames.KUBE_TOKEN, null, config);
    server = Config.getSysConf(ConfNames.KUBE_SERVER_URL, null, config);
    namespace = Config.getSysConf(ConfNames.KUBE_NAMESPACE, "default", config);
    webClientOptions = new WebClientOptions();
    String kubeServerPem = Config.getSysConf(ConfNames.KUBE_SERVER_PEM, null, config);
    if (kubeServerPem != null) {
      webClientOptions.setPemTrustOptions(new PemTrustOptions().addCertPath(kubeServerPem));
    }
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
    return vertx.fileSystem().readFile(fname)
        .compose(content -> new YamlProcessor().process(vertx, null, content))
        .map(conf -> {
          JsonObject context = findNameInJsonArray(conf, "context",
              conf.getString("current-context"));
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
    webClient = WebClient.create(vertx, webClientOptions);
    return readKubeConfig(vertx, fname).compose(x -> {
      if (server == null) {
        return Future.succeededFuture();
      }
      logger.info("Enable Kubernetes config server {} namespace {}", server, namespace);
      return refresh().onComplete(y -> refreshLoop(vertx));
    });
  }

  static JsonObject getFirstPort(JsonArray ports) {
    if (ports == null) {
      return null;
    }
    for (int i = 0; i < ports.size(); i++) {
      JsonObject port = ports.getJsonObject(i);
      String portName = port.getString("name");
      if ("http".equals(portName) || "https".equals(portName)) {
        return port; // pick first http/https port
      }
    }
    return null;
  }

  static List<DeploymentDescriptor> parseEndpoint(JsonObject item) {
    List<DeploymentDescriptor> dds = new ArrayList<>();
    try {
      JsonObject metadata = item.getJsonObject("metadata");
      String metadataName = metadata.getString("name");
      JsonObject labels = metadata.getJsonObject("labels");
      String name = labels.getString("app.kubernetes.io/name");
      if (name == null) {
        logger.warn("No app.kubernetes.io/name property for {}", metadataName);
        return dds;
      }
      String version = labels.getString("app.kubernetes.io/version");
      if (version == null) {
        logger.warn("No app.kubernetes.io/version property for {}", metadataName);
        return dds;
      }
      ModuleId moduleId = new ModuleId(name + "-" + version);
      JsonArray subsets = item.getJsonArray("subsets");
      if (subsets == null) {
        return dds;
      }
      for (int k = 0; k < subsets.size(); k++) {
        JsonObject subset = subsets.getJsonObject(k);
        JsonObject port = getFirstPort(subset.getJsonArray("ports"));
        if (port == null) {
          continue;
        }
        Integer portNumber = port.getInteger("port");
        String transport = port.getString("name");
        JsonArray addresses = subset.getJsonArray("addresses");
        for (int i = 0; i < addresses.size(); i++) {
          JsonObject address = addresses.getJsonObject(i);
          String ip = address.getString("ip");
          DeploymentDescriptor dd = new DeploymentDescriptor();
          dd.setSrvcId(moduleId.toString());
          dd.setUrl(transport + "://" + ip + ":" + portNumber);
          dd.setInstId(KUBE_INST_PREFIX + ip + ":" + portNumber);
          dds.add(dd);
        }
      }
      return dds;
    } catch (Exception e) {
      logger.warn("Parsing item {} resulted in {}", item.encodePrettily(), e.getMessage(), e);
      return dds;
    }
  }

  static List<DeploymentDescriptor> parseItems(JsonObject response) {
    List<DeploymentDescriptor> res = new ArrayList<>();
    JsonArray items = response.getJsonArray("items");
    for (int i = 0; i < items.size(); i++) {
      res.addAll(parseEndpoint(items.getJsonObject(i)));
    }
    return res;
  }

  /**
   * Get endpoints from Kubernetes cluster.
   * @return deployment descriptors list.
   */
  Future<List<DeploymentDescriptor>> getEndpoints() {
    String uri = server + "/api/v1/namespaces/" + namespace + "/endpoints";
    HttpRequest<Buffer> abs = webClient.getAbs(uri);
    if (token != null) {
      abs.putHeader("Authorization", "Bearer " + token);
    }
    return abs.putHeader("Accept", "application/json")
        .expect(ResponsePredicate.SC_OK)
        .expect(ResponsePredicate.JSON)
        .send()
        .map(res -> parseItems(res.bodyAsJsonObject()));
  }

  void refreshLoop(Vertx vertx) {
    vertx.setTimer(refreshInterval, y -> {
      // only need to refresh in one instance in the cluster - as that information is
      // shared with Hazelcast anyway.
      Future<Void> f = discoveryManager.isLeader() ? refresh() : Future.succeededFuture();
      f.onComplete(x -> refreshLoop(vertx));
    });
  }

  /**
   * Refresh discovery with Kubernetes service information.
   * @return async result.
   */
  public Future<Void> refresh() {
    if (server == null) {
      return Future.succeededFuture();
    }
    return discoveryManager.get().compose(existing ->
            getEndpoints().compose(incoming -> {
              List<DeploymentDescriptor> removeList = new ArrayList<>();
              List<DeploymentDescriptor> addList = new ArrayList<>();
              getDiffs(existing, incoming, removeList, addList);
              Future<Void> future = Future.succeededFuture();
              for (DeploymentDescriptor dd : addList) {
                logger.info("Kubernetes: add {} {}", dd.getSrvcId(), dd.getUrl());
                future = future.compose(x -> discoveryManager.add(dd));
              }
              for (DeploymentDescriptor dd : removeList) {
                logger.info("Kubernetes: remove {} {}", dd.getSrvcId(), dd.getUrl());
                future = future.compose(x -> discoveryManager.removeAndUndeploy(
                    dd.getSrvcId(), dd.getInstId()));
              }
              return future;
            }))
        .onSuccess(x -> logger.info("Kubernetes refresh OK"))
        .onFailure(x -> logger.info("Kubernetes refresh failed {}", x.getMessage(), x));
  }

  static void getDiffs(List<DeploymentDescriptor> existing, List<DeploymentDescriptor> incoming,
      List<DeploymentDescriptor> removeList, List<DeploymentDescriptor> addList) {

    Map<String, DeploymentDescriptor> instances = new HashMap<>(incoming.size());
    for (DeploymentDescriptor dd : incoming) {
      String key = dd.getSrvcId() + "-" + dd.getInstId();
      instances.put(key, dd);
    }
    for (DeploymentDescriptor dd : existing) {
      String instId = dd.getInstId();
      if (instId.startsWith(KUBE_INST_PREFIX)) {
        String key = dd.getSrvcId() + "-" + dd.getInstId();
        if (instances.remove(key) == null) {
          removeList.add(dd);
        }
      }
    }
    addList.addAll(instances.values());
  }
}
