package org.folio.okapi.common;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import org.junit.Test;

public class WebClientFactoryTest {

  @Test
  public void getWebClient() {
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
}

