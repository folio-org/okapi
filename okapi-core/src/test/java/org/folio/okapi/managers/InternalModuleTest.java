package org.folio.okapi.managers;

import static org.mockito.Mockito.*;

import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.assertj.core.api.WithAssertions;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.util.ProxyContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.junit.jupiter.MockitoExtension;

@Timeout(5)
@ExtendWith(VertxExtension.class)
@ExtendWith(MockitoExtension.class)
class InternalModuleTest implements WithAssertions {

  Future<String> internalService(HttpMethod httpMethod, String path, String body) {
    // deep stub is not a code smell because HttpServerRequest implementations
    // don't have a public constructor
    ProxyContext proxyContext = mock(ProxyContext.class, RETURNS_DEEP_STUBS);
    when(proxyContext.getCtx().request().method()).thenReturn(httpMethod);
    when(proxyContext.getCtx().normalizedPath()).thenReturn(path);
    InternalModule internalModule = new InternalModule(
        mock(ModuleManager.class), mock(TenantManager.class), null,
        mock(DiscoveryManager.class), null, mock(PullManager.class), mock(KubernetesManager.class), "1.2.3");
    return internalModule.internalService(body, proxyContext);
  }

  @Test
  void upgradeModuleForTenantWithBody(VertxTestContext vtc) {
    String body = "[{ \"id\":\"mod-users-17.3.0\", \"action\": \"enable\" }]";
    internalService(HttpMethod.POST, "/_/proxy/tenants/diku/upgrade", body)
        .onComplete(vtc.failing(t -> {
          assertThat(t).hasMessageContaining("must not have a body");
          vtc.completeNow();
        }));
  }

  @ParameterizedTest
  @CsvSource({
    "/_/proxy/import/modules",
    "/_/proxy/modules",
    "/_/proxy/modules/:id",
    "/_/proxy/tenants",
    "/_/proxy/tenants/:id",
    "/_/proxy/tenants/:id/interfaces/:int",
    "/_/proxy/tenants/:id/timers",
    "/_/proxy/tenants/:id/timers/:timer",
    "/_/proxy/pull/modules",
    "/_/proxy/health",
    "/_/discovery/modules",
    "/_/discovery/modules/:srvcid",
    "/_/discovery/modules/:srvcid/:instid",
    "/_/discovery/health",
    "/_/discovery/health/:srvcId",
    "/_/discovery/health/:srvcId/:instid",
    "/_/env",
    "/_/env/:name",
    "/_/version",
  })
  void pathNotFound(String path, VertxTestContext vtc) {
    internalService(HttpMethod.OPTIONS, path, null)
        .onComplete(vtc.failing(t -> {
          assertThat(t).hasMessageContainingAll("Unhandled internal module path", path);
          vtc.completeNow();
        }));
  }

  /**
   * Check that invalid JSON in body is ignored.
   */
  @ParameterizedTest
  @CsvSource({
    "GET,  /_/proxy/health",
    "GET,  /_/version",
  })
  @SuppressWarnings("java:S2699")  // Suppress "Add at least one assertion to this test case"
  // as it is a false positive: https://github.com/SonarSource/sonar-java/pull/4141
  void ignoreBody(HttpMethod httpMethod, String path, VertxTestContext vtc) {
    internalService(httpMethod, path, "}").onComplete(vtc.succeedingThenComplete());
  }

  /**
   * Check that invalid JSON in body is caught.
   */
  @ParameterizedTest
  @CsvSource({
    "POST, /_/proxy/import/modules",
    "GET,  /_/proxy/modules",
    "POST, /_/proxy/modules",
    "POST, /_/proxy/pull/modules",
    "POST, /_/discovery/modules",
    "POST, /_/env",
  })
  void invalidJson(HttpMethod httpMethod, String path, VertxTestContext vtc) {
    internalService(httpMethod, path, "}").onComplete(vtc.failing(cause -> {
      assertThat(cause).hasMessageContaining("Failed to decode");
      vtc.completeNow();
    }));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void validateTenantId(String tenantId) {
    var td = new TenantDescriptor(tenantId, "foo");
    assertThat(InternalModule.validateTenantId(td).succeeded()).isTrue();
    assertThat(td.getId()).matches("t[0-9a-f]{30}");
    assertThat(td.getName()).isEqualTo("foo");
  }
}
