package org.folio.okapi.common;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.okapi.testing.UtilityClassTester;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class HttpClientFactoryTest {

  @Test
  void isUtilityClass() {
    UtilityClassTester.assertUtilityClass(HttpClientFactory.class);
  }

  @Test
  void getHttpClient() {
    Vertx vertxA = Vertx.vertx();
    Vertx vertxB = Vertx.vertx();
    HttpClient httpClientA1 = HttpClientFactory.getHttpClient(vertxA);
    HttpClient httpClientB1 = HttpClientFactory.getHttpClient(vertxB);
    HttpClient httpClientA2 = HttpClientFactory.getHttpClient(vertxA);
    HttpClient httpClientB2 = HttpClientFactory.getHttpClient(vertxB);
    assertThat(httpClientA1, is(httpClientA2));
    assertThat(httpClientB1, is(httpClientB2));
    assertThat(httpClientA1, is(not(httpClientB1)));
  }

  Future<String> get(HttpClient httpClient) {
    return WebClient.wrap(httpClient).get("/").send().map(res -> res.bodyAsString());
  }

  @Test
  void getHttpClientWithOptions(Vertx vertx1, VertxTestContext vtc) {
    var vertx2 = Vertx.vertx();
    var options1 = new HttpClientOptions().setDefaultPort(8001);
    var options2 = new HttpClientOptions().setDefaultPort(8002);
    var options3 = new HttpClientOptions().setDefaultPort(8003);
    var httpClient1 = HttpClientFactory.getHttpClient(vertx1, options1);
    var httpClient2 = HttpClientFactory.getHttpClient(vertx2, options2);
    var httpClient3 = HttpClientFactory.getHttpClient(vertx1, options3);

    vertx1.createHttpServer()
        .requestHandler(request -> request.response().end("1"))
        .listen(8001)
        .compose(x -> {
          return vertx1.createHttpServer()
              .requestHandler(request -> request.response().end("2"))
              .listen(8002);
        })
        .compose(x -> get(httpClient1))
        .onComplete(vtc.succeeding(res -> assertThat(res, is("1"))))
        .compose(x -> get(httpClient2))
        .onComplete(vtc.succeeding(res -> assertThat(res, is("2"))))
        .compose(x -> get(httpClient3))
        .onComplete(vtc.succeeding(res -> assertThat(res, is("1"))))
        .onSuccess(x -> vtc.completeNow());
  }
}

