package org.folio.okapi.managers;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class KubernetesManagerTest {

  @Test
  void testNoConfig(Vertx vertx, VertxTestContext context) {
    KubernetesManager kubernetesManager = new KubernetesManager(new JsonObject());
    kubernetesManager.init(vertx).onComplete(context.succeeding(res -> {
      assertThat(kubernetesManager.kubeConfig).isNull();
      context.completeNow();
    }));
  }

  @Test
  void testConfigNoSuchFile(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put("kube_config", "no_such_file.yaml");
    KubernetesManager kubernetesManager = new KubernetesManager(config);
    kubernetesManager.init(vertx).onComplete(context.failing(e -> {
      assertThat(e.getMessage()).isEqualTo("Unable to read file at path 'no_such_file.yaml'");
      context.completeNow();
    }));
  }

  @Test
  void testConfigBadYaml(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put("kube_config", "kube-config-bad.yaml");
    KubernetesManager kubernetesManager = new KubernetesManager(config);
    kubernetesManager.init(vertx).onComplete(context.failing(e -> {
      assertThat(e.getMessage()).contains("while parsing a block");
      context.completeNow();
    }));
  }

  @Test
  void testConfigOK(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put("kube_config", "kube-config.yaml");
    KubernetesManager kubernetesManager = new KubernetesManager(config);
    kubernetesManager.init(vertx).onComplete(context.succeeding(res -> {
      System.out.println(kubernetesManager.kubeConfig.encodePrettily());
      assertThat(kubernetesManager.kubeConfig.getString("current-context")).isEqualTo("folio-eks-2-us-west-2");
      context.completeNow();
    }));
  }
}
