package org.folio.okapi.managers;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
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
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class KubernetesManagerTest {

  static final int KUBE_MOCK_PORT = 9235;
  static final String KUBE_MOCK_SERVER = "http://localhost:" + KUBE_MOCK_PORT;

  // server and token in test/resources/kube-config.yaml
  static final String KUBE_FILE_SERVER = "http://localhost:9236";
  static final String KUBE_FILE_TOKEN = "kubeconfig-u-k2zqca6scw:kpzqbctgnbl9s8znnp5bpt9rrdf8xpdhtwhmhz58zqh9lz7k9fpd91";
  static JsonObject mockEndpointsResponse;
  static DiscoveryManager discoveryManager;

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext context) {
    mockEndpointsResponse = new JsonObject();
    HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
    Router router = Router.router(vertx);
    router.get("/api/v1/namespaces/folio-1/endpoints").handler(x -> {
      x.response().setStatusCode(200);
      x.response().putHeader("Content-Type", "application/json");
      x.request().endHandler(e -> x.response().end(mockEndpointsResponse.encode()));
    });
    vertx.createHttpServer(so)
        .requestHandler(router)
        .listen(KUBE_MOCK_PORT)
        .compose(x -> {
          discoveryManager = new DiscoveryManager(new DeploymentStoreNull());
          return discoveryManager.init(vertx);
        })
        .onComplete(context.succeedingThenComplete());
  }

  @BeforeEach
  void beforeEach(Vertx vertx, VertxTestContext context) {
    mockEndpointsResponse = new JsonObject()
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
                .put("subsets", new JsonArray()
                    .add(new JsonObject()
                        .put("addresses", new JsonArray()
                            .add(new JsonObject().put("ip", "10.0.0.60"))
                        )
                        .put("ports", new JsonArray()
                            .add(new JsonObject()
                                .put("name", "https")
                                .put("port", 16443)
                                .put("protocol", "TCP")
                            )
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
                .put("subsets", new JsonArray()
                    .add(new JsonObject()
                        .put("addresses", new JsonArray()
                            .add(new JsonObject().put("ip", "10.1.2.1"))
                            .add(new JsonObject().put("ip", "10.1.2.2"))
                        )
                        .put("ports", new JsonArray()
                            .add(new JsonObject()
                                .put("name", "http")
                                .put("port", 8099)
                                .put("protocol", "TCP")
                            )
                        )
                    )
                )
            )
        );
    discoveryManager.removeAndUndeploy().onComplete(context.succeedingThenComplete());
  }

  @Test
  void testNoConfig(Vertx vertx, VertxTestContext context) {
    KubernetesManager kubernetesManager = new KubernetesManager(discoveryManager, new JsonObject());
    kubernetesManager.init(vertx)
        .compose(x -> kubernetesManager.refresh())
        .onComplete(context.succeeding(res -> {
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
    kubernetesManager.init(vertx).onComplete(context.failingThenComplete());
  }

  @Test
  void testConfigOK(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put(KUBE_CONFIG, "kube-config.yaml");
    config.put(KUBE_SERVER_PEM, "fake.pem"); // coverage, but the file is apparently not loaded unless https is used
    KubernetesManager kubernetesManager = new KubernetesManager(discoveryManager, config);
    kubernetesManager.init(vertx).onComplete(context.failing(res -> {
      assertThat(kubernetesManager.server).isEqualTo(KUBE_FILE_SERVER);
      assertThat(kubernetesManager.token).isEqualTo(KUBE_FILE_TOKEN);
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
    kubernetesManager.init(vertx).onComplete(context.failing(res -> {
      assertThat(kubernetesManager.server).isEqualTo(KUBE_FILE_SERVER);
      assertThat(kubernetesManager.token).isEqualTo("1234");
      assertThat(kubernetesManager.namespace).isEqualTo("folio-1");
      context.completeNow();
    }));
  }

  @Test
  void testConfigOKServerOverride(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put(KUBE_CONFIG, "kube-config.yaml");
    config.put(KUBE_SERVER_URL, KUBE_MOCK_SERVER);
    config.put(KUBE_NAMESPACE, "folio-1");
    KubernetesManager kubernetesManager = new KubernetesManager(discoveryManager, config);
    kubernetesManager.init(vertx).onComplete(context.succeeding(res -> {
      assertThat(kubernetesManager.server).isEqualTo(KUBE_MOCK_SERVER);
      assertThat(kubernetesManager.token).isEqualTo(KUBE_FILE_TOKEN);
      assertThat(kubernetesManager.namespace).isEqualTo("folio-1");
      context.completeNow();
    }));
  }

  @Test
  void testConfigServer(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put(KUBE_SERVER_URL, KUBE_MOCK_SERVER);
    KubernetesManager kubernetesManager = new KubernetesManager(discoveryManager, config);
    kubernetesManager.init(vertx).onComplete(context.failing(res -> {
      assertThat(kubernetesManager.token).isNull();
      assertThat(kubernetesManager.server).isEqualTo(KUBE_MOCK_SERVER);
      context.completeNow();
    }));
  }

  @Test
  void testConfigTokenServer(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject();
    config.put(KUBE_SERVER_URL, KUBE_MOCK_SERVER);
    config.put(KUBE_TOKEN, "1234");
    config.put(KUBE_NAMESPACE, "folio-1");
    KubernetesManager kubernetesManager = new KubernetesManager(discoveryManager, config);

    // include in discovery a module already discovered with Kubernetes
    DeploymentDescriptor ddInitial = new DeploymentDescriptor(KubernetesManager.KUBE_INST_PREFIX + "mod",
        "mod-initial-1.0.0", "http://localhost", null, null);
    discoveryManager.addAndDeploy(ddInitial)
        .compose(x -> kubernetesManager.init(vertx))
        .compose(x -> discoveryManager.get())
        .onComplete(context.succeeding(res -> {
          // kubernetes.init will refresh and remove initial and add mod-users instead
          assertThat(res).hasSize(2);
          assertThat(res.get(0).getSrvcId()).isEqualTo("mod-users-5.0.0");
          assertThat(res.get(1).getSrvcId()).isEqualTo("mod-users-5.0.0");
          assertThat(kubernetesManager.server).isEqualTo(KUBE_MOCK_SERVER);
          assertThat(kubernetesManager.token).isEqualTo("1234");
          assertThat(kubernetesManager.namespace).isEqualTo("folio-1");
          context.completeNow();
        }));
  }

  @Test
  void testRefreshLoopLeaderFalse(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject().put(KUBE_REFRESH_INTERVAL, 1);
    DiscoveryManager dd = mock(DiscoveryManager.class, RETURNS_DEEP_STUBS);
    when(dd.isLeader()).thenReturn(false);
    KubernetesManager kubernetesManager = new KubernetesManager(dd, config);
    kubernetesManager.refreshLoop(vertx);
    vertx.setTimer(10, x -> context.verify(() -> {
          verify(dd, atLeastOnce()).isLeader();
          context.completeNow();
        })
    );
  }

  @Test
  void testRefreshLoopLeaderTrue(Vertx vertx, VertxTestContext context) {
    JsonObject config = new JsonObject().put(KUBE_REFRESH_INTERVAL, 1);
    DiscoveryManager dd = mock(DiscoveryManager.class, RETURNS_DEEP_STUBS);
    when(dd.isLeader()).thenReturn(true);
    KubernetesManager kubernetesManager = new KubernetesManager(dd, config);
    kubernetesManager.refreshLoop(vertx);
    vertx.setTimer(10, x -> context.verify(() -> {
          verify(dd, atLeastOnce()).isLeader();
          context.completeNow();
        })
    );
  }

  @Test
  void testParseServiceEmpty() {
    assertThat(KubernetesManager.parseEndpoint(new JsonObject())).isEmpty();
  }

  @Test
  void testParseServiceKubernetesApiServer() {
    assertThat(KubernetesManager.parseEndpoint(new JsonObject()
        .put("apiVersion", "v1")
        .put("kind", "Service")
        .put("metadata", new JsonObject()
            .put("labels", new JsonObject()
                .put("component", "apiserver")
                .put("provider", "kubernetes")
            )
        )
        .put("subsets", new JsonArray()
            .add(new JsonObject()
                .put("addresses", new JsonArray()
                    .add(new JsonObject().put("ip", "10.0.0.60"))
                )
                .put("ports", new JsonArray()
                    .add(new JsonObject()
                        .put("name", "https")
                        .put("port", 16443)
                        .put("protocol", "TCP")
                    )
                )
            )
        ))).isEmpty();
  }

  @Test
  void testParseServiceHttps() {
    List<DeploymentDescriptor> dds = KubernetesManager.parseEndpoint(new JsonObject()
        .put("apiVersion", "v1")
        .put("kind", "Service")
        .put("metadata", new JsonObject()
            .put("labels", new JsonObject()
                .put("app.kubernetes.io/name", "mod-users")
                .put("app.kubernetes.io/version", "5.0.0")
            )
        )
        .put("subsets", new JsonArray()
            .add(new JsonObject()
                .put("addresses", new JsonArray()
                    .add(new JsonObject().put("ip", "10.1.2.1"))
                    .add(new JsonObject().put("ip", "10.1.2.2"))
                )
                .put("ports", new JsonArray()
                    .add(new JsonObject()
                        .put("name", "https")
                        .put("port", 443)
                        .put("protocol", "TCP")
                    )
                    .add(new JsonObject()
                        .put("name", "http")
                        .put("port", 8001)
                        .put("protocol", "TCP")
                    )
                    .add(new JsonObject()
                        .put("name", "http")
                        .put("port", 8002)
                        .put("protocol", "TCP")
                    )
                )
            )
            .add(new JsonObject()
                .put("addresses", new JsonArray()
                    .add(new JsonObject().put("ip", "10.1.2.3"))
                )
                .put("ports", new JsonArray()
                    .add(new JsonObject()
                        .put("name", "http")
                        .put("port", 8080)
                        .put("protocol", "TCP")
                    )
                )
            )
        ));
    assertThat(dds).hasSize(3);
    assertThat(dds.get(0).getSrvcId()).isEqualTo("mod-users-5.0.0");
    assertThat(dds.get(0).getUrl()).isEqualTo("https://10.1.2.1:443");
    assertThat(dds.get(1).getSrvcId()).isEqualTo("mod-users-5.0.0");
    assertThat(dds.get(1).getUrl()).isEqualTo("https://10.1.2.2:443");
    assertThat(dds.get(2).getSrvcId()).isEqualTo("mod-users-5.0.0");
    assertThat(dds.get(2).getUrl()).isEqualTo("http://10.1.2.3:8080");
  }

  @Test
  void testParseServiceNoVersion() {
    List<DeploymentDescriptor> dds = KubernetesManager.parseEndpoint(new JsonObject()
        .put("apiVersion", "v1")
        .put("kind", "Service")
        .put("metadata", new JsonObject()
            .put("labels", new JsonObject()
                .put("app.kubernetes.io/name", "mod-users")
            )
        ));
    assertThat(dds).isEmpty();
  }

  @Test
  void testParseServiceNoTransport() {
    List<DeploymentDescriptor> dds = KubernetesManager.parseEndpoint(new JsonObject()
        .put("apiVersion", "v1")
        .put("kind", "Service")
        .put("metadata", new JsonObject()
            .put("labels", new JsonObject()
                .put("app.kubernetes.io/name", "mod-users")
                .put("app.kubernetes.io/version", "5.0.0")
            )
        )
        .put("subsets", new JsonArray()
            .add(new JsonObject()
                .put("addresses", new JsonArray()
                    .add(new JsonObject().put("ip", "10.1.2.1"))
                    .add(new JsonObject().put("ip", "10.1.2.2"))
                )
                .put("ports", new JsonArray()
                    .add(new JsonObject()
                        .put("port", 8100)
                        .put("protocol", "TCP")
                    )
                )
            )
        ));
    assertThat(dds).isEmpty();
  }

  @Test
  void testParseServiceNoPorts() {
    List<DeploymentDescriptor> dds = KubernetesManager.parseEndpoint(new JsonObject()
        .put("apiVersion", "v1")
        .put("kind", "Service")
        .put("metadata", new JsonObject()
            .put("labels", new JsonObject()
                .put("app.kubernetes.io/name", "mod-users")
                .put("app.kubernetes.io/version", "5.0.0")
            )
        )
        .put("subsets", new JsonArray()
            .add(new JsonObject()
                .put("addresses", new JsonArray()
                    .add(new JsonObject().put("ip", "10.1.2.1"))
                )
            )
        ));
    assertThat(dds).isEmpty();
  }

  @Test
  void testParseServiceEmptyPorts() {
    List<DeploymentDescriptor> dds = KubernetesManager.parseEndpoint(new JsonObject()
        .put("apiVersion", "v1")
        .put("kind", "Service")
        .put("metadata", new JsonObject()
            .put("labels", new JsonObject()
                .put("app.kubernetes.io/name", "mod-users")
                .put("app.kubernetes.io/version", "5.0.0")
            )
        )
        .put("subsets", new JsonArray()
            .add(new JsonObject()
                .put("addresses", new JsonArray()
                    .add(new JsonObject().put("ip", "10.1.2.1"))
                )
                .put("ports", new JsonArray())
            )
        ));
    assertThat(dds).isEmpty();
  }

  @Test
  void testParseServiceNoSubsets() {
    List<DeploymentDescriptor> dds = KubernetesManager.parseEndpoint(new JsonObject()
        .put("apiVersion", "v1")
        .put("kind", "Service")
        .put("metadata", new JsonObject()
            .put("labels", new JsonObject()
                .put("app.kubernetes.io/name", "mod-users")
                .put("app.kubernetes.io/version", "5.0.0")
            )
        ));
    assertThat(dds).isEmpty();
  }

  @Test
  void testParseServices() {
    JsonObject config = new JsonObject();
    config.put(KUBE_SERVER_URL, KUBE_MOCK_SERVER);
    config.put(KUBE_TOKEN, "1234");
    config.put(KUBE_NAMESPACE, "folio-1");
    List<DeploymentDescriptor> dds = KubernetesManager.parseItems(mockEndpointsResponse);
    assertThat(dds).hasSize(2);
    assertThat(dds.get(0).getUrl()).isEqualTo("http://10.1.2.1:8099");
    assertThat(dds.get(0).getSrvcId()).isEqualTo("mod-users-5.0.0");
    assertThat(dds.get(1).getUrl()).isEqualTo("http://10.1.2.2:8099");
    assertThat(dds.get(1).getSrvcId()).isEqualTo("mod-users-5.0.0");
    assertThat(dds.get(0).getInstId()).isNotEqualTo(dds.get(1).getInstId());
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

    removeList = new ArrayList<>();
    addList = new ArrayList<>();
    DeploymentDescriptor dd_a_101 = new DeploymentDescriptor("kube_10.0.0.1:10000", "a-1.0.1",
        "http://localhost:10.0.0.1:10000", null, null);
    KubernetesManager.getDiffs(List.of(dd_a), List.of(dd_a_101), removeList, addList);
    assertThat(removeList).contains(dd_a);
    assertThat(addList).contains(dd_a_101);

  }

}
