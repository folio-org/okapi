package org.folio.okapi.bean;

import io.vertx.core.http.HttpMethod;

/**
 * An Instance of a Module that has been enabled for a given tenant.
 * Used internally in the proxy for deciding the routing of requests.
 */
public class ModuleInstance {

  private final ModuleDescriptor md;
  private String url; // Absolute URL to the module instance
  private final RoutingEntry re;
  private String authToken;
  private String userId;
  private String permissions;
  private String path; // The relative URI from the proxy request
  private final HttpMethod method;
  private final boolean handler;  // is true if handler; false otherwise (filter)
  private boolean withRetry;

  /**
   * Create module instance.
   * @param md descriptor
   * @param re routing entry
   * @param path request path
   * @param method HTTP method
   * @param handler true: handler, false; filter/other
   */
  public ModuleInstance(ModuleDescriptor md, RoutingEntry re,
                        String path, HttpMethod method, boolean handler) {

    this.md = md;
    this.url = null;
    this.re = re;
    this.authToken = null;
    String base = "";
    if (re != null && re.getRewritePath() != null) {
      base = re.getRewritePath();
    }
    this.path = base + path;
    this.method = method;
    this.handler = handler;
    this.withRetry = false;
  }

  /**
   * Substitute {id} in path.
   * @param id identifier
   */
  public void substPathId(String id) {
    path = path.replace("{id}", id);
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

  public HttpMethod getMethod() {
    return method;
  }

  public boolean isHandler() {
    return handler;
  }

  public boolean isWithRetry()  {
    return this.withRetry;
  }

  public ModuleInstance withRetry() {
    this.withRetry = true;
    return this;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getPermissions() {
    return permissions;
  }

  public void setPermissions(String permissions) {
    this.permissions = permissions;
  }
}
