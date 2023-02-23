package org.folio.okapi.common.refreshtoken.client;

import io.vertx.ext.web.client.WebClient;

/**
 * Some common options used by most clients.
 *
 * <p>More could be added in the future without breaking the API.
 */
public class ClientOptions {

  private String okapiUrl;

  private WebClient webClient;

  public ClientOptions() {
    okapiUrl = "http://localhost:9130";
  }

  public ClientOptions okapiUrl(String okapiUrl) {
    this.okapiUrl = okapiUrl;
    return this;
  }

  public String getOkapiUrl() {
    return okapiUrl;
  }

  public ClientOptions webClient(WebClient webClient) {
    this.webClient = webClient;
    return this;
  }

  public WebClient getWebClient() {
    return webClient;
  }
}
