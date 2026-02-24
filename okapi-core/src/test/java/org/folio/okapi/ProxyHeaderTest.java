package org.folio.okapi;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.vertx.core.Expectation;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.RequestOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Objects;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ExtendWith(VertxExtension.class)
class ProxyHeaderTest {

  Vertx vertx;
  HttpClient httpClient;

  @BeforeEach
  void beforeEach(Vertx vertx) {
    this.vertx = vertx;
    this.httpClient = vertx.createHttpClient();
  }

  @ParameterizedTest
  @CsvSource({
    "gzip, deflate",
    "gzip,",
    "deflate, gzip",
    "deflate,"
  })
  void test(String contentEncoding1, String contentEncoding2, VertxTestContext vtc) {
    deployOkapi()
    .compose(x -> deployModule(contentEncoding1))
    .compose(server -> put("http://localhost:8081/foo", null, contentEncoding1))
    .expecting(assertContentEncoding(contentEncoding1))
    .compose(x -> put("http://localhost:9130/foo", "br", contentEncoding2))
    .expecting(assertContentEncoding(contentEncoding2))
    .onComplete(vtc.succeedingThenComplete());
  }

  Future<HttpClientResponse> postOkapi(String path, String body) {
    var requestOptions = new RequestOptions()
        .setMethod(HttpMethod.POST).setAbsoluteURI("http://localhost:9130" + path);
    return httpClient.request(requestOptions)
        .compose(httpClientRequest -> httpClientRequest.send(body))
        .expecting(HttpResponseExpectation.status(200, 299));
  }

  Future<HttpClientResponse> deployOkapi() {
    return vertx.deployVerticle(new MainVerticle())  // okapi at 9130
        .compose(x -> postOkapi("/_/proxy/tenants", """
            {"id":"diku"}
            """))
        .compose(x -> postOkapi("/_/proxy/modules", """
            {"id":"mod-foo-1.0.0", "provides":
              [{"id":"foo", "version":"1.0", "handlers":
                [{"methods":["PUT"], "pathPattern": "/foo", "permissionsRequired": []}]}]}
            """))
        .compose(x -> postOkapi("/_/discovery/modules", """
            {"srvcId": "mod-foo-1.0.0", "instId": "mod-foo-1.0.0",
             "url": "http://localhost:8081"}
            """))
        .compose(x -> postOkapi("/_/proxy/tenants/diku/modules", """
            {"id":"mod-foo-1.0.0"}
            """));
  }

  Future<HttpServer> deployModule(String contentEncoding) {
    var compressionOptions = contentEncoding.equals("gzip")
        ? StandardCompressionOptions.gzip() : StandardCompressionOptions.deflate();
    var httpServerOptions = new HttpServerOptions()
        .setCompressionSupported(true)
        .addCompressor(compressionOptions);
    return vertx.createHttpServer(httpServerOptions)
        .requestHandler(req -> {
          try {
            assertThat("Content-Encoding", req.getHeader("Content-Encoding"), is(nullValue()));
            req.response().end("y".repeat(100000));
          } catch (Exception e) {
            req.response().setStatusCode(400).end(ExceptionUtils.getStackTrace(e));
          }
        })
        .listen(8081);
  }

  Future<HttpClientResponse> put(String uri, String contentEncoding, String acceptEncoding) {
    var requestOptions = new RequestOptions()
        .setMethod(HttpMethod.PUT)
        .setAbsoluteURI(uri)
        .addHeader("X-Okapi-Tenant", "diku");
    if (contentEncoding != null) {
      requestOptions.addHeader("Content-Encoding", contentEncoding);
    }
    if (acceptEncoding != null) {
      requestOptions.addHeader("Accept-Encoding", acceptEncoding);
    }
    return httpClient.request(requestOptions)
        .compose(httpClientRequest -> httpClientRequest.send("x".repeat(100000)))
        .expecting(HttpResponseExpectation.SC_OK);
  }

  Expectation<HttpClientResponse> assertContentEncoding(String contentEncoding) {
    return new Expectation<HttpClientResponse>() {
      @Override
      public boolean test(HttpClientResponse httpClientResponse) {
        return Objects.equals(httpClientResponse.getHeader("Content-Encoding"), contentEncoding);
      }

      @Override
      public Throwable describe(HttpClientResponse httpClientResponse) {
        return new VertxException("Expected Content-Encoding: " + contentEncoding
            + " but got " + httpClientResponse.getHeader("Content-Encoding"), true);
      }
    };
  }
}
