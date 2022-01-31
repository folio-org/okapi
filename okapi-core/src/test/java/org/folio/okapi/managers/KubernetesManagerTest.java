package org.folio.okapi.managers;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.okapi.ConfNames.*;

@ExtendWith(VertxExtension.class)
public class KubernetesManagerTest {

  @Test
  void testNoConfig(Vertx vertx, VertxTestContext context) {
    KubernetesManager kubernetesManager = new KubernetesManager(new JsonObject());
    kubernetesManager.init(vertx).onComplete(context.succeeding(res -> {
      assertThat(kubernetesManager.server).isNull();
      assertThat(kubernetesManager.token).isNull();
      assertThat(kubernetesManager.namespace).isEqualTo("default");
      context.completeNow();
    }));
  }

  @Test
  void testConfigNoSuchFile(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put(KUBE_CONFIG, "no_such_file.yaml");
    KubernetesManager kubernetesManager = new KubernetesManager(config);
    kubernetesManager.init(vertx).onComplete(context.failing(e -> {
      assertThat(e.getMessage()).isEqualTo("Unable to read file at path 'no_such_file.yaml'");
      context.completeNow();
    }));
  }

  @Test
  void testConfigBadSyntaxYaml(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put(KUBE_CONFIG, "kube-config-bad-syntax.yaml");
    KubernetesManager kubernetesManager = new KubernetesManager(config);
    kubernetesManager.init(vertx).onComplete(context.failing(e -> {
      assertThat(e.getMessage()).contains("while parsing a block");
      context.completeNow();
    }));
  }

  @Test
  void testConfigNoUser(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put(KUBE_CONFIG, "kube-config-no-user.yaml");
    KubernetesManager kubernetesManager = new KubernetesManager(config);
    kubernetesManager.init(vertx).onComplete(context.failing(e -> {
      assertThat(e.getMessage()).contains("No property with name 'folio-eks-2-us-west-2' in array 'users'");
      context.completeNow();
    }));
  }

  @Test
  void testConfigNoContexts(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put(KUBE_CONFIG, "kube-config-no-contexts.yaml");
    KubernetesManager kubernetesManager = new KubernetesManager(config);
    kubernetesManager.init(vertx).onComplete(context.failing(e -> {
      context.completeNow();
    }));
  }

  @Test
  void testConfigOK(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put(KUBE_CONFIG, "kube-config.yaml");
    KubernetesManager kubernetesManager = new KubernetesManager(config);
    kubernetesManager.init(vertx).onComplete(context.succeeding(res -> {
      assertThat(kubernetesManager.server).isEqualTo("https://rancher.dev.folio.org/k8s/clusters/c-479xv");
      assertThat(kubernetesManager.token).isEqualTo("kubeconfig-u-k2zqca6scw:kpzqbctgnbl9s8znnp5bpt9rrdf8xpdhtwhmhz58zqh9lz7k9fpd91");
      context.completeNow();
    }));
  }

  @Test
  void testConfigOKTokenOverride(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put(KUBE_CONFIG, "kube-config.yaml");
    config.put(KUBE_TOKEN, "1234");
    config.put(KUBE_NAMESPACE, "folio-1");
    KubernetesManager kubernetesManager = new KubernetesManager(config);
    kubernetesManager.init(vertx).onComplete(context.succeeding(res -> {
      assertThat(kubernetesManager.server).isEqualTo("https://rancher.dev.folio.org/k8s/clusters/c-479xv");
      assertThat(kubernetesManager.token).isEqualTo("1234");
      assertThat(kubernetesManager.namespace).isEqualTo("folio-1");
      context.completeNow();
    }));
  }

  @Test
  void testConfigOKServerOverride(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put(KUBE_CONFIG, "kube-config.yaml");
    config.put(KUBE_SERVER, "http://localhost:9100");
    config.put(KUBE_NAMESPACE, "folio-1");
    KubernetesManager kubernetesManager = new KubernetesManager(config);
    kubernetesManager.init(vertx).onComplete(context.succeeding(res -> {
      assertThat(kubernetesManager.server).isEqualTo("http://localhost:9100");
      assertThat(kubernetesManager.token).isEqualTo("kubeconfig-u-k2zqca6scw:kpzqbctgnbl9s8znnp5bpt9rrdf8xpdhtwhmhz58zqh9lz7k9fpd91");
      assertThat(kubernetesManager.namespace).isEqualTo("folio-1");
      context.completeNow();
    }));
  }

  @Test
  void testConfigServer(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put(KUBE_SERVER, "http://localhost:9100");
    KubernetesManager kubernetesManager = new KubernetesManager(config);
    kubernetesManager.init(vertx).onComplete(context.succeeding(res -> {
      assertThat(kubernetesManager.token).isNull();
      assertThat(kubernetesManager.server).isEqualTo("http://localhost:9100");
      context.completeNow();
    }));
  }

  @Test
  void testConfigTokenServer(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put(KUBE_SERVER, "http://localhost:9100");
    config.put(KUBE_TOKEN, "1234");
    config.put(KUBE_NAMESPACE, "folio-1");
    KubernetesManager kubernetesManager = new KubernetesManager(config);
    kubernetesManager.init(vertx).onComplete(context.succeeding(res -> {
      assertThat(kubernetesManager.server).isEqualTo("http://localhost:9100");
      assertThat(kubernetesManager.token).isEqualTo("1234");
      assertThat(kubernetesManager.namespace).isEqualTo("folio-1");
      context.completeNow();
    }));
  }


}
