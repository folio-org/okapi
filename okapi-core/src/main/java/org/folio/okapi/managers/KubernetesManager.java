package org.folio.okapi.managers;

import io.vertx.config.yaml.YamlProcessor;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import org.folio.okapi.common.Config;

public class KubernetesManager {

  final String fname;
  JsonObject kubeConfig;

  public KubernetesManager(JsonObject config) {
    fname = Config.getSysConf("kube_config", null, config);
  }

  /**
   * async initialization of the manager.
   * @param vertx Vert.x handle
   * @return future result.
   */
  public Future<Void> init(Vertx vertx) {
    if (fname == null) {
      return Future.succeededFuture();
    }
    FileSystem fs = vertx.fileSystem();
    return fs.readFile(fname).compose(content -> {
      YamlProcessor yamlProcessor = new YamlProcessor();
      return yamlProcessor.process(vertx, null, content).map(conf -> {
        kubeConfig = conf;
        return null;
      });
    });
  }
}
