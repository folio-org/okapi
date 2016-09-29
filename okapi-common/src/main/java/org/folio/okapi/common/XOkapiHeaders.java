package org.folio.okapi.common;

/**
 * X-Okapi Headers used in the system. Some are needed by every module, and some
 * are only used between Okapi itself and the authorization/authentication
 * modules.
 * @author heikki
 */
public class XOkapiHeaders {

  /**
   * The common "X-Okapi" prefix for all the headers defined here.
   * 
   */
  public static final String PREFIX = "X-Okapi";

  /** X-Okapi-Token. A token that identifies the user who is making the current
   * request. May carry additional permissions and other stuff related to
   * authorization. Only the authorization modules should look inside, for the
   * rest of the system this should be opaque. When a module needs to make a
   * call to another module, it needs to pass the token it received in its
   * request into the request to the new module.
   */
  public static final String TOKEN = "X-Okapi-Token";

  /** X-Okapi-Url. Tells the URL where the modules may contact Okapi, for
   * making requests to other modules. Can be set on Okapi's command line
   * when starting up. Note that it may point to some kind of load balancer
   * or other network trickery, but in the end there will be an Okapi instance
   * listening on it. Does not including a trailing slash. Defaults to
   * http://localhost:9130
   */
  public static final String URL = "X-Okapi-Url";

  /** X-Okapi-Tenant. Tells which tenant is making the request. Every request
   * to Okapi should have this header set to a valid tenant ID. When making a
   * call from one module to another, remember to copy this over.
   */
  public static final String TENANT = "X-Okapi-Tenant";

  /** X-Okapi-Trace. Will be added to the responses from Okapi, to help
   * debugging where the request actually went, and how long did it take.
   */
  public static final String TRACE = "X-Okapi-Trace";

  /** X-Okapi-Permissions-Required. Lists the permissions a given module requires.
   * Only used between Okapi and the auth complex.
   */
  public static final String PERMISSIONS_REQUIRED = "X-Okapi-Permissions-Required";

  /** X-Okapi-Permissions-Desired. Lists the permissions a given module is
   * interested in, without strictly needing them. Only used between Okapi and
   * the auth complex.
   */
  public static final String PERMISSIONS_DESIRED = "X-Okapi-Permissions-Desired";

  
  /** X-Okapi-Permissions. The permissions a module expressed interest in, and
   * which were granted to this user. Can be used for modifying the way a module
   * behaves.
   */
  public static final String PERMISSIONS = "X-Okapi-Permissions";

  /** X-Okapi-Module-Permissions. Permissions granted to a module.
   * Used only between Okapi and the authorization module.
   */
  public static final String MODULE_PERMISSIONS = "X-Okapi-Module-Permissions";

  /** X-Okapi-Module-Tokens. JWT tokens specifically made for invoking given
   * modules. Only used between Okapi and the authorization module.
   */
  public static final String MODULE_TOKENS = "X-Okapi-Module-Tokens";

  /** X-Okapi-Stop. A signal from a module to Okapi to stop the
   * pipeline processing and return the result immediately. Only to be used
   * in special circumstances, like in the auth subsystem.
   */
  public static final String STOP = "X-Okapi-Stop";


}
