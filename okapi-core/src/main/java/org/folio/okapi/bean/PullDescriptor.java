package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PullDescriptor {

  private String[] urls;

  public PullDescriptor() {
  }

  public String[] getUrls() {
    return urls;
  }

  public void setUrls(String[] urls) {
    this.urls = urls;
  }
}
