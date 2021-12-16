package org.folio.okapi.common;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.okapi.testing.UtilityClassTester;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class WebClientFactoryTest {

  @Test
  void isUtilityClass() {
    UtilityClassTester.assertUtilityClass(WebClientFactory.class);
  }

  @Test
  void getWebClient() {
    Vertx vertxA = Vertx.vertx();
    Vertx vertxB = Vertx.vertx();
    WebClient webClientA1 = WebClientFactory.getWebClient(vertxA);
    WebClient webClientB1 = WebClientFactory.getWebClient(vertxB);
    WebClient webClientA2 = WebClientFactory.getWebClient(vertxA);
    WebClient webClientB2 = WebClientFactory.getWebClient(vertxB);
    assertThat(webClientA1, is(webClientA2));
    assertThat(webClientB1, is(webClientB2));
    assertThat(webClientA1, is(not(webClientB1)));
  }

  @Test
  void getWebClientWithOptions(Vertx vertx, VertxTestContext vtc) {
    vertx.createHttpServer()
        .requestHandler(request -> request.response().end(request.getHeader("User-Agent")))
        .listen(8000)
        .compose(x -> {
          WebClientOptions options = new WebClientOptions().setUserAgent("Nimbus");
          WebClient webClient = WebClientFactory.getWebClient(vertx, options);
          return webClient.getAbs("http://localhost:8000").send();
        })
        .onComplete(vtc.succeeding(res -> assertThat(res.bodyAsString(), is("Nimbus"))))
        .compose(x -> {
          WebClient webClient = WebClientFactory.getWebClient(Vertx.vertx());  // new Vertx
          return webClient.getAbs("http://localhost:8000").send();
        })
        .onComplete(vtc.succeeding(res -> assertThat(res.bodyAsString(), containsString("Vert"))))
        .compose(x -> {
          WebClient webClient = WebClientFactory.getWebClient(vertx);  // first Vertx
          return webClient.getAbs("http://localhost:8000").send();
        })
        .onComplete(vtc.succeeding(res -> assertThat(res.bodyAsString(), is("Nimbus"))))
        .onSuccess(x -> vtc.completeNow());
  }
}

