package org.folio.okapi.managers;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.ArrayList;
import java.util.List;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.service.impl.DeploymentStoreNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.okapi.ConfNames.*;

@ExtendWith(VertxExtension.class)
public class KubernetesManagerTest {

  static final int KUBE_MOCK_PORT = 9235;
  static final String KUBE_MOCK_SERVER = "http://localhost:" + KUBE_MOCK_PORT;
  static JsonObject mockServicesResponse;
  static DiscoveryManager discoveryManager;

  @BeforeAll
  static void beforeEach(Vertx vertx, VertxTestContext context) {
    mockServicesResponse = new JsonObject();
    HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
    Router router = Router.router(vertx);
    router.get("/api/v1/namespaces/folio-1/services").handler(x -> {
      x.response().setStatusCode(200);
      x.response().putHeader("Content-Type", "application/json");
      x.request().endHandler(e -> x.response().end(mockServicesResponse.encode()));
    });
    vertx.createHttpServer(so)
        .requestHandler(router)
        .listen(KUBE_MOCK_PORT)
        .compose(x -> {
          discoveryManager = new DiscoveryManager(new DeploymentStoreNull());
          return discoveryManager.init(vertx);
        })
        .compose(x -> {
          WebClient webClient = WebClient.create(vertx);
          return webClient.getAbs(KUBE_MOCK_SERVER + "/api/v1/namespaces/folio-1/services")
              .expect(ResponsePredicate.SC_OK)
              .expect(ResponsePredicate.JSON)
              .send().mapEmpty();
        })
        .onComplete(context.succeeding(res -> context.completeNow()));
  }

  @BeforeEach
  void beforeEach() {
    mockServicesResponse = new JsonObject()
        .put("apiVersion", "v1")
        .put("items", new JsonArray()
            .add(new JsonObject()
                .put("apiVersion", "v1")
                .put("kind", "Service")
                .put("metadata", new JsonObject()
                    .put("labels", new JsonObject()
                        .put("component", "apiserver")
                        .put("provider", "kubernetes")
                    )
                )
                .put("spec", new JsonObject()
                    .put("clusterIP", "10.1.1.1")
                    .put("clusterIPs", new JsonArray()
                        .add("10.1.1.1")
                    )
                    .put("ports", new JsonArray()
                        .add(new JsonObject()
                            .put("name", "https")
                            .put("port", 443)
                            .put("protocol", "TCP")
                            .put("targetPort", 16443)
                        )
                    )
                )
            )
            .add(new JsonObject()
                .put("apiVersion", "v1")
                .put("kind", "Service")
                .put("metadata", new JsonObject()
                    .put("labels", new JsonObject()
                        .put("app.kubernetes.io/name", "mod-users")
                        .put("app.kubernetes.io/version", "5.0.0")
                    )
                )
                .put("spec", new JsonObject()
                    .put("clusterIP", "10.1.2.1")
                    .put("clusterIPs", new JsonArray()
                        .add("10.1.2.1")
                        .add("10.1.2.2")
                    )
                    .put("ports", new JsonArray()
                        .add(new JsonObject()
                            .put("name", "http")
                            .put("port", 8099)
                            .put("protocol", "TCP")
                            .put("targetPort", 8099)
                        )
                    )
                )
            )
        );
  }

  @Test
  void testNoConfig(Vertx vertx, VertxTestContext context) {
    KubernetesManager kubernetesManager = new KubernetesManager(discoveryManager, new JsonObject());
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
    KubernetesManager kubernetesManager = new KubernetesManager(discoveryManager, config);
    kubernetesManager.init(vertx).onComplete(context.failing(e -> {
      assertThat(e.getMessage()).isEqualTo("Unable to read file at path 'no_such_file.yaml'");
      context.completeNow();
    }));
  }

  @Test
  void testConfigBadSyntaxYaml(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put(KUBE_CONFIG, "kube-config-bad-syntax.yaml");
    KubernetesManager kubernetesManager = new KubernetesManager(discoveryManager, config);
    kubernetesManager.init(vertx).onComplete(context.failing(e -> {
      assertThat(e.getMessage()).contains("while parsing a block");
      context.completeNow();
    }));
  }

  @Test
  void testConfigNoUser(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put(KUBE_CONFIG, "kube-config-no-user.yaml");
    KubernetesManager kubernetesManager = new KubernetesManager(discoveryManager, config);
    kubernetesManager.init(vertx).onComplete(context.failing(e -> {
      assertThat(e.getMessage()).contains("No property with name 'folio-eks-2-us-west-2' in array 'users'");
      context.completeNow();
    }));
  }

  @Test
  void testConfigNoContexts(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put(KUBE_CONFIG, "kube-config-no-contexts.yaml");
    KubernetesManager kubernetesManager = new KubernetesManager(discoveryManager, config);
    kubernetesManager.init(vertx).onComplete(context.failing(e -> {
      context.completeNow();
    }));
  }

  @Test
  void testConfigOK(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put(KUBE_CONFIG, "kube-config.yaml");
    KubernetesManager kubernetesManager = new KubernetesManager(discoveryManager, config);
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
    KubernetesManager kubernetesManager = new KubernetesManager(discoveryManager, config);
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
    config.put(KUBE_SERVER, KUBE_MOCK_SERVER);
    config.put(KUBE_NAMESPACE, "folio-1");
    KubernetesManager kubernetesManager = new KubernetesManager(discoveryManager, config);
    kubernetesManager.init(vertx).onComplete(context.succeeding(res -> {
      assertThat(kubernetesManager.server).isEqualTo(KUBE_MOCK_SERVER);
      assertThat(kubernetesManager.token).isEqualTo("kubeconfig-u-k2zqca6scw:kpzqbctgnbl9s8znnp5bpt9rrdf8xpdhtwhmhz58zqh9lz7k9fpd91");
      assertThat(kubernetesManager.namespace).isEqualTo("folio-1");
      context.completeNow();
    }));
  }

  @Test
  void testConfigServer(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put(KUBE_SERVER, KUBE_MOCK_SERVER);
    KubernetesManager kubernetesManager = new KubernetesManager(discoveryManager, config);
    kubernetesManager.init(vertx).onComplete(context.succeeding(res -> {
      assertThat(kubernetesManager.token).isNull();
      assertThat(kubernetesManager.server).isEqualTo(KUBE_MOCK_SERVER);
      context.completeNow();
    }));
  }

  @Test
  void testParseService() {
    assertThat(KubernetesManager.parseService(new JsonObject())).isEmpty();
    assertThat(KubernetesManager.parseService(new JsonObject()
        .put("apiVersion", "v1")
        .put("kind", "Service")
        .put("metadata", new JsonObject()
            .put("labels", new JsonObject()
                .put("component", "apiserver")
                .put("provider", "kubernetes")
            )
        )
        .put("spec", new JsonObject()
            .put("clusterIP", "10.1.1.1")
            .put("clusterIPs", new JsonArray()
                .add("10.1.1.1")
            )
            .put("ports", new JsonArray()
                .add(new JsonObject()
                    .put("name", "https")
                    .put("port", 443)
                    .put("protocol", "TCP")
                    .put("targetPort", 16443)
                )
            )
        ))).isEmpty();
    assertThat(KubernetesManager.parseService(new JsonObject()
                .put("apiVersion", "v1")
                .put("kind", "Service")
                .put("metadata", new JsonObject()
                    .put("labels", new JsonObject()
                        .put("app.kubernetes.io/name", "mod-other")
                    )
                )
            )).isEmpty();
    assertThat(KubernetesManager.parseService(new JsonObject()
            .put("apiVersion", "v1")
            .put("kind", "Service")
            .put("metadata", new JsonObject()
                .put("labels", new JsonObject()
                    .put("app.kubernetes.io/name", "mod-no-ports")
                    .put("app.kubernetes.io/version", "1.0.0")
                )
            )
        )).isEmpty();
    List<DeploymentDescriptor> dds = KubernetesManager.parseService(new JsonObject()
        .put("apiVersion", "v1")
        .put("kind", "Service")
        .put("metadata", new JsonObject()
            .put("labels", new JsonObject()
                .put("app.kubernetes.io/name", "mod-users")
                .put("app.kubernetes.io/version", "5.0.0")
            )
        )
        .put("spec", new JsonObject()
            .put("clusterIP", "10.1.2.1")
            .put("clusterIPs", new JsonArray()
                .add("10.1.2.1")
                .add("10.1.2.2")
            )
            .put("ports", new JsonArray()
                .add(new JsonObject()
                    .put("name", "http")
                    .put("port", 8099)
                    .put("protocol", "TCP")
                    .put("targetPort", 8099)
                )
            )
        ));
    assertThat(dds).hasSize(2);
    assertThat(dds.get(0).getSrvcId()).isEqualTo("mod-users-5.0.0");
    assertThat(dds.get(0).getUrl()).isEqualTo("http://10.1.2.1:8099");
    assertThat(dds.get(1).getSrvcId()).isEqualTo("mod-users-5.0.0");
    assertThat(dds.get(1).getUrl()).isEqualTo("http://10.1.2.2:8099");

    dds = KubernetesManager.parseService(new JsonObject()
        .put("apiVersion", "v1")
        .put("kind", "Service")
        .put("metadata", new JsonObject()
            .put("labels", new JsonObject()
                .put("app.kubernetes.io/name", "mod-users")
                .put("app.kubernetes.io/version", "5.0.0")
            )
        )
        .put("spec", new JsonObject()
            .put("clusterIP", "10.1.2.1")
            .put("clusterIPs", new JsonArray().add("10.1.2.1"))
            .put("ports", new JsonArray()
                .add(new JsonObject()
                    .put("port", 8099)
                    .put("protocol", "TCP")
                    .put("targetPort", 8099)
                )
            )
        ));
    assertThat(dds).hasSize(1);
    assertThat(dds.get(0).getSrvcId()).isEqualTo("mod-users-5.0.0");
    assertThat(dds.get(0).getUrl()).isEqualTo("http://10.1.2.1:8099");
  }

  @Test
  void testParseServices() {
    JsonObject config = new JsonObject();
    config.put(KUBE_SERVER, KUBE_MOCK_SERVER);
    config.put(KUBE_TOKEN, "1234");
    config.put(KUBE_NAMESPACE, "folio-1");
    List<DeploymentDescriptor> dds = KubernetesManager.parseServices(mockServicesResponse);
    assertThat(dds).hasSize(2);
    assertThat(dds.get(0).getUrl()).isEqualTo("http://10.1.2.1:8099");
    assertThat(dds.get(0).getSrvcId()).isEqualTo("mod-users-5.0.0");
    assertThat(dds.get(1).getUrl()).isEqualTo("http://10.1.2.2:8099");
    assertThat(dds.get(1).getSrvcId()).isEqualTo("mod-users-5.0.0");
  }

  @Test
  void testConfigTokenServer(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put(KUBE_SERVER, KUBE_MOCK_SERVER);
    config.put(KUBE_TOKEN, "1234");
    config.put(KUBE_NAMESPACE, "folio-1");
    KubernetesManager kubernetesManager = new KubernetesManager(discoveryManager, config);
    kubernetesManager.init(vertx).onComplete(context.succeeding(res -> {
      assertThat(kubernetesManager.server).isEqualTo(KUBE_MOCK_SERVER);
      assertThat(kubernetesManager.token).isEqualTo("1234");
      assertThat(kubernetesManager.namespace).isEqualTo("folio-1");
      context.completeNow();
    }));
  }

  @Test
  void testGetDiffs() {
    DeploymentDescriptor dd_a = new DeploymentDescriptor("kube_10.0.0.1:10000", "a-1.0.0",
        "http://localhost:10.0.0.1:10000", null, null);
    DeploymentDescriptor dd_b = new DeploymentDescriptor("kube_10.0.0.1:10001", "b-1.0.0",
        "http://localhost:10.0.0.1:10001", null, null);
    DeploymentDescriptor dd_c = new DeploymentDescriptor("10.0.0.1:10002", "c-1.0.0",
        "http://localhost:10.0.0.1:10002", null, null);

    List<DeploymentDescriptor> removeList = new ArrayList<>();
    List<DeploymentDescriptor> addList = new ArrayList<>();
    KubernetesManager.getDiffs(List.of(), List.of(dd_a, dd_b), removeList, addList);
    assertThat(removeList).isEmpty();
    assertThat(addList).contains(dd_a, dd_b);

    removeList = new ArrayList<>();
    addList = new ArrayList<>();
    KubernetesManager.getDiffs(List.of(dd_a, dd_b, dd_c), List.of(dd_a), removeList, addList);
    assertThat(removeList).contains(dd_b);
    assertThat(addList).isEmpty();

    removeList = new ArrayList<>();
    addList = new ArrayList<>();
    KubernetesManager.getDiffs(List.of(dd_a, dd_c), List.of(dd_b), removeList, addList);
    assertThat(removeList).contains(dd_a);
    assertThat(addList).contains(dd_b);

    removeList = new ArrayList<>();
    addList = new ArrayList<>();
    KubernetesManager.getDiffs(List.of(dd_a, dd_b, dd_c), List.of(), removeList, addList);
    assertThat(removeList).contains(dd_a, dd_b);
    assertThat(addList).isEmpty();
  }

}
