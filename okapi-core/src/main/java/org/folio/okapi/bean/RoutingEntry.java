package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.folio.okapi.util.ProxyContext;

/**
 * One entry in Okapi's routing table. Each entry contains one or more HTTP
 * methods, and the path they mean, for example "GET /foo". Incoming requests
 * are mapped to a series of routingEntries, ordered by their level. Also
 * carries the permission bits required and desired for this operation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoutingEntry {

  private String[] methods;
  private String pathPattern;
  private String path;
  private String rewritePath; // used to rewrite base of proxy request, used for filters
  private String phase;
  private String level;
  private String type;
  private String redirectPath; // only for type='redirect'
  private String unit;
  private String delay;
  private long factor;
  private String[] permissionsRequired;
  private String[] permissionsDesired;
  private String[] modulePermissions;
  private static final String INVALID_PATH_CHARS = "\\%+{}()[].;:=?@#^$\"' ";
  @JsonIgnore
  private String phaseLevel = "50"; // default for regular handler

  public enum ProxyType {
    REQUEST_RESPONSE,
    REQUEST_ONLY,
    HEADERS,
    REDIRECT,
    INTERNAL,
    REQUEST_RESPONSE_1_0,
    REQUEST_LOG
  }

  @JsonIgnore
  private ProxyType proxyType = ProxyType.REQUEST_RESPONSE;

  public String[] getPermissionsRequired() {
    return permissionsRequired;
  }

  public void setPermissionsRequired(String[] permissionsRequired) {
    this.permissionsRequired = permissionsRequired;
  }

  public String[] getPermissionsDesired() {
    return permissionsDesired;
  }

  public void setPermissionsDesired(String[] permissionsDesired) {
    this.permissionsDesired = permissionsDesired;
  }

  public String[] getModulePermissions() {
    return modulePermissions;
  }

  public void setModulePermissions(String[] modulePermissions) {
    this.modulePermissions = modulePermissions;
  }

  @JsonIgnore
  public ProxyType getProxyType() {
    return this.proxyType;
  }

  public String getType() {
    return type;
  }

  /**
   * Set routing entry type.
   * @param type routing entry type
   */
  public void setType(String type) {
    if ("request-response".equals(type)) {
      proxyType = ProxyType.REQUEST_RESPONSE;
    } else if ("request-only".equals(type)) {
      proxyType = ProxyType.REQUEST_ONLY;
    } else if ("headers".equals(type)) {
      proxyType = ProxyType.HEADERS;
    } else if ("redirect".equals(type)) {
      proxyType = ProxyType.REDIRECT;
    } else if ("system".equals(type)) {
      proxyType = ProxyType.REQUEST_RESPONSE;
    } else if ("internal".equals(type)) {
      proxyType = ProxyType.INTERNAL;
    } else if ("request-response-1.0".equals(type)) {
      proxyType = ProxyType.REQUEST_RESPONSE_1_0;
    } else if ("request-log".equals(type)) {
      proxyType = ProxyType.REQUEST_LOG;
    } else {
      throw new DecodeException("Invalid entry type: " + type);
    }
    this.type = type;
  }

  public String getRedirectPath() {
    return redirectPath;
  }

  public void setRedirectPath(String redirectPath) {
    this.redirectPath = redirectPath;
  }

  public String getUnit() {
    return unit;
  }

  /**
   * Set timer unit for routing entry.
   * @param unit unit name
   */
  public void setUnit(String unit) {
    this.unit = unit;
    if (unit != null) {
      switch (unit) {
        case "millisecond":
          factor = 1;
          break;
        case "second":
          factor = 1000;
          break;
        case "minute":
          factor = 60000;
          break;
        case "hour":
          factor = 3600000;
          break;
        case "day":
          factor = 86400000;
          break;
        default: throw new IllegalArgumentException(unit);
      }
    }
  }

  public String getDelay() {
    return delay;
  }

  public void setDelay(String delay) {
    this.delay = delay;
  }

  /**
   * get timer delay in milliseconds.
   */
  @JsonIgnore
  public long getDelayMilliSeconds() {
    if (this.delay != null && unit != null) {
      long delayMilliSeconds = Integer.parseInt(this.delay);
      return delayMilliSeconds * factor;
    } else {
      return 0;
    }
  }

  public String getLevel() {
    return level;
  }

  public void setLevel(String level) {
    this.level = level;
    this.phaseLevel = level;
  }

  @JsonIgnore
  public String getPhaseLevel() {
    return phaseLevel;
  }

  public String[] getMethods() {
    return methods;
  }

  /**
   * Set routing methods.
   * @param methods HTTP method name or "*" for all
   */
  public void setMethods(String[] methods) {
    for (String s : methods) {
      if (!s.equals("*")) {
        HttpMethod.valueOf(s);
      }
    }
    this.methods = methods;
  }

  /**
   * Get path pattern/path - whichever exist.
   */
  @JsonIgnore
  public String getStaticPath() {
    if (path == null || path.isEmpty()) {
      return pathPattern;
    } else {
      return path;
    }
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
    this.pathPattern = null;
  }

  public String getPathPattern() {
    return pathPattern;
  }

  public String getRewritePath() {
    return rewritePath;
  }

  public void setRewritePath(String rewritePath) {
    this.rewritePath = rewritePath;
  }

  private int skipNamedPattern(String pathPattern, int i, char c) {
    i++;
    for (; i < pathPattern.length(); i++) {
      c = pathPattern.charAt(i);
      if (c == '}') {
        break;
      } else if (INVALID_PATH_CHARS.indexOf(c) != -1 || c == '/') {
        throw new DecodeException("Invalid character " + c + " inside {}-construct in pathPattern");
      }
    }
    if (c != '}') {
      throw new DecodeException("Missing }-character for {}-construct in pathPattern");
    }
    return i;
  }

  /**
   * set path pattern.
   * Special constructs like {name} and * are supported.
   * @param pathPattern pattern string
   * @throws DecodeException if pattern is invalid.
   */
  public void setPathPattern(String pathPattern) {
    this.path = null;
    this.pathPattern = pathPattern;
    int i = 0;
    while (i < pathPattern.length()) {
      char c = pathPattern.charAt(i);
      if (c == '{') {
        i = skipNamedPattern(pathPattern, i, c);
      } else if (INVALID_PATH_CHARS.indexOf(c) != -1) {
        throw new DecodeException("Invalid character " + c + " for pathPattern");
      }
      i++;
    }
  }

  static int cutUri(String uri) {
    int len = uri.indexOf('?');
    if (len == -1) {
      len = uri.length();
    }
    int idx = uri.indexOf('#');
    if (idx != -1 && idx < len) {
      len = idx;
    }
    return len;
  }

  static boolean fastMatch(String pathPattern, String uri) {
    return fastMatch(pathPattern, 0, uri, 0, cutUri(uri));
  }

  static boolean fastMatch(String pathPattern, int patternI, String uri, int uriI, int uriLength) {
    while (patternI < pathPattern.length()) {
      char patternC = pathPattern.charAt(patternI);
      patternI++;
      if (patternC == '{') {
        while (patternI < pathPattern.length()) {
          if (pathPattern.charAt(patternI) == '}') {
            patternI++;
            break;
          }
          patternI++;
        }
        boolean empty = true;
        while (uriI < uriLength && uri.charAt(uriI) != '/') {
          uriI++;
          empty = false;
        }
        if (empty) {
          return false;
        }
      } else if (patternC != '*') {
        if (uriI == uriLength || patternC != uri.charAt(uriI)) {
          return false;
        }
        uriI++;
      } else {
        do {
          if (fastMatch(pathPattern, patternI, uri, uriI, uriLength)) {
            return true;
          }
          uriI++;
        } while (uriI <= uriLength);
        return false;
      }
    }
    return uriI == uriLength;
  }

  private boolean matchUri(String uri) {
    if (uri == null) {
      return true;
    }
    if (pathPattern != null) {
      return fastMatch(pathPattern, uri);
    }
    return path == null || uri.startsWith(path);
  }

  /**
   * Match uri and method against routing entry.
   * @param uri path in fact
   * @param method HTTP method
   * @return true on match; false otherwise
   */
  public boolean match(String uri, String method) {
    if (methods != null) {
      for (String m : methods) {
        if (method == null || m.equals("*") || m.equals(method)) {
          return matchUri(uri);
        }
      }
    }
    return false;
  }

  /**
   * Get redirect URI path.
   * @param uri path
   * @return null if no redirect; redirect path otherwise
   */
  public String getRedirectUri(String uri) {
    if (path != null) {
      return redirectPath + uri.substring(path.length());
    }
    final int indx1 = cutUri(uri);
    String p = this.redirectPath;
    if (indx1 < uri.length()) {
      p = p.concat(uri.substring(indx1));
    }
    return p;
  }

  public String getPhase() {
    return phase;
  }

  /**
   * Set routing entry phrase.
   * @param phase such as "auth", "pre", ..
   */
  public void setPhase(String phase) {
    if (phase != null) {
      switch (phase) {
        case "auth":
          phaseLevel = "10";
          break;
        case "pre":
          phaseLevel = "40";
          break;
        case "post":
          phaseLevel = "60";
          break;
        default:
          throw new DecodeException("Invalid phase " + phase);
      }
    }
    this.phase = phase;
  }

  /**
   * Validate handler of routing entry.
   * May log warnings via ProxyContext.warn.
   * @param pc Proxy context
   * @param mod module name
   * @return empty string if OK; non-empty string with message otherwise
   */
  public String validateHandlers(ProxyContext pc, String mod) {
    String section = "handlers";
    String err = validateCommon(pc, section, mod);
    if (err.isEmpty()) {
      String prefix = "Module '" + mod + "' " + section;
      if (phase != null) {
        pc.warn(prefix
            + " uses 'phase' in the handlers section. "
            + "Leave it out");
      }
      if ("request-response".equals(type)) {
        pc.warn(prefix
            + " uses type=request-response. "
            + "That is the default, you can leave it out");
      }
    }
    return err;
  }

  /**
   * Validate filters of routing entry.
   * May log warnings via ProxyContext.warn.
   * @param pc Proxy context
   * @param mod module name
   * @return empty string if OK; non-empty string with message otherwise
   */
  public String validateFilters(ProxyContext pc, String mod) {
    return validateCommon(pc, "filters", mod);
  }

  private String validateCommon(ProxyContext pc, String section, String mod) {
    String prefix = "Module '" + mod + "' " + section;
    if (pathPattern != null && !pathPattern.isEmpty()) {
      prefix += " " + pathPattern;
    } else if (path != null && !path.isEmpty()) {
      prefix += " " + path;
    }
    prefix += ": ";
    pc.debug(prefix
        + "Validating RoutingEntry " + Json.encode(this));
    if ((path == null || path.isEmpty())
        && (pathPattern == null || pathPattern.isEmpty())) {
      return "Bad routing entry, needs a pathPattern or at least a path";
    }

    if ("redirect".equals(type)) {
      if (redirectPath == null || redirectPath.isEmpty()) {
        return "Redirect entry without redirectPath";
      }
    } else {
      if (redirectPath != null && !redirectPath.isEmpty()) {
        pc.warn(prefix
            + "has a redirectPath, even though it is not a redirect");
      }
      if (pathPattern == null || pathPattern.isEmpty()) {
        pc.warn(prefix
            + " uses old type path"
            + ". Use a pathPattern instead");
      }
      if (level != null) {
        String ph = "";
        if ("filters".equals(section)) {
          ph = "Use a phase=auth instead";
        }
        pc.warn(prefix
            + "uses DEPRECATED level. " + ph);
      }

      if (pathPattern != null && pathPattern.endsWith("/")) {
        pc.warn(prefix
            + "ends in a slash. Probably not what you intend");
      }
      if ("system".equals(type)) {
        pc.warn(prefix
            + "uses DEPRECATED type 'system'");
      }
    }
    return "";
  }
}
