package org.folio.okapi;

import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.vertx.core.Expectation;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.RequestOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Objects;
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
    var compressionOptions1 = contentEncoding1.equals("gzip")
        ? StandardCompressionOptions.gzip() : StandardCompressionOptions.deflate();

    deployOkapi()
    .compose(x -> {
      var httpServerOptions = new HttpServerOptions()
          .setCompressionSupported(true)
          .addCompressor(compressionOptions1);
      return vertx.createHttpServer(httpServerOptions)
          .requestHandler(req -> {
            var contentEncoding = req.getHeader("Content-Encoding");
            if (contentEncoding != null) {
              vtc.failNow("Got unexpected Content-Encoding: " + contentEncoding);
            }
            req.response().end("answer");
          })
          .listen(8081);  // mod-foo at 8081
    })
    .compose(server -> get("http://localhost:8081/foo", null, contentEncoding1))
    .expecting(assertContentEncoding(contentEncoding1))
    .compose(x -> get("http://localhost:9130/foo", "br", contentEncoding2))
    .expecting(assertContentEncoding(contentEncoding2))
    .onComplete(vtc.succeedingThenComplete());
  }

  Future<HttpClientResponse> post(String path, String body) {
    var requestOptions = new RequestOptions()
        .setMethod(HttpMethod.POST).setAbsoluteURI("http://localhost:9130" + path);
    return httpClient.request(requestOptions)
        .compose(httpClientRequest -> httpClientRequest.send(body))
        .expecting(HttpResponseExpectation.status(200, 299));
  }

  Future<HttpClientResponse> deployOkapi() {
    return vertx.deployVerticle(new MainVerticle())  // okapi at 9130
        .compose(x -> post("/_/proxy/tenants", """
            {"id":"diku"}
            """))
        .compose(x -> post("/_/proxy/modules", """
            {"id":"mod-foo-1.0.0", "provides":
              [{"id":"foo", "version":"1.0", "handlers":
                [{"methods":["PUT"], "pathPattern": "/foo", "permissionsRequired": []}]}]}
            """))
        .compose(x -> post("/_/discovery/modules", """
            {"srvcId": "mod-foo-1.0.0", "instId": "mod-foo-1.0.0",
             "url": "http://localhost:8081"}
            """))
        .compose(x -> post("/_/proxy/tenants/diku/modules", """
            {"id":"mod-foo-1.0.0"}
            """));
  }

  Future<HttpClientResponse> get(String uri, String contentEncoding, String acceptEncoding) {
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
