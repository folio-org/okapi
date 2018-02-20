package org.folio.okapi.bean;

/**
 * An Instance of a Module that has been enabled for a given tenant.
 * Used internally in the proxy for deciding the routing of requests.
 */
public class ModuleInstance {

  private final ModuleDescriptor md;
  private String url; // Absolute URL to the module instance
  private final RoutingEntry re;
  private String authToken;
  private final String path; // The relative URI from the proxy request

  public ModuleInstance(ModuleDescriptor md, RoutingEntry re, String path) {
    this.md = md;
    this.url = null;
    this.re = re;
    this.authToken = null;
    this.path = path;
  }

  public ModuleDescriptor getModuleDescriptor() {
    return md;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public RoutingEntry getRoutingEntry() {
    return re;
  }

  public String getAuthToken() {
    return authToken;
  }

  public void setAuthToken(String authToken) {
    this.authToken = authToken;
  }

  public String getPath() {
    return path;
  }
}
