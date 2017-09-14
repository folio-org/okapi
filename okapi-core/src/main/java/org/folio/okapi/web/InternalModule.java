package org.folio.okapi.web;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import static io.vertx.core.http.HttpMethod.*;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.Utils;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.ModuleDescriptorBrief;
import org.folio.okapi.bean.NodeDescriptor;
import org.folio.okapi.bean.PullDescriptor;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.deployment.DeploymentManager;
import org.folio.okapi.discovery.DiscoveryManager;
import org.folio.okapi.env.EnvManager;
import org.folio.okapi.pull.PullManager;
import org.folio.okapi.service.ModuleManager;
import org.folio.okapi.service.TenantManager;
import org.folio.okapi.util.LogHelper;
import org.folio.okapi.util.ModuleId;
import org.folio.okapi.util.ProxyContext;

/**
 * Okapi's built-in module. Managing /_/ endpoints.
 *
 * /_/proxy/modules /_/proxy/tenants /_/proxy/health /_/proxy/pull
 * /_/deployment /_/discovery /_/env /_/version /_/test loglevel etc
 *
 * TODO ModuleDescriptor
 *
 * Note that the endpoint /_/invoke/ can not be handled here, as the proxy must
 * read the request body before invoking this built-in module, and /_/invoke
 * uses ctx.reroute(), which assumes the body has not been read.
 *
 *
 */
public class InternalModule {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  private final ModuleManager moduleManager;
  private final TenantManager tenantManager;
  private final DeploymentManager deploymentManager;
  private final DiscoveryManager discoveryManager;
  private final EnvManager envManager;
  private final PullManager pullManager;
  private final LogHelper logHelper;
  private final String okapiVersion;
  private static final String interfaceVersion = "1.9";

  public InternalModule(ModuleManager modules, TenantManager tenantManager,
    DeploymentManager deploymentManager, DiscoveryManager discoveryManager,
    EnvManager envManager, PullManager pullManager, String okapiVersion) {
    this.moduleManager = modules;
    this.tenantManager = tenantManager;
    this.deploymentManager = deploymentManager;
    this.discoveryManager = discoveryManager;
    this.envManager = envManager;
    this.pullManager = pullManager;
    logHelper = new LogHelper();
    this.okapiVersion = okapiVersion;
    logger.warn("InternalModule starting: " + okapiVersion);
  }

  static public ModuleDescriptor moduleDescriptor(String okapiVersion) {
    String v = okapiVersion;
    if (v == null) {  // happens at compile time,
      v = "0.0.0";   // unit tests can just check for this
    }
    String okapiModule = XOkapiHeaders.OKAPI_MODULE + "-" + v;
    final String doc = "{"
      // Try to keep these in the same order as the RAML
      // Read-only operations should (normally?) not require permissions
      // Tighten that up when we know what is actually needed
      // (except env, it may contain database passwords)
      + " \"id\" : \"" + okapiModule + "\","
      + " \"name\" : \"" + okapiModule + "\","
      + " \"provides\" : [ {"
      + "   \"id\" : \"okapi-proxy\","
      + "   \"version\" : \"" + interfaceVersion + "\","
      + "   \"interfaceType\" : \"internal\"," // actually, null.
      + "   \"handlers\" : [ ]"
      + " }, {"
      + "   \"id\" : \"okapi\","
      + "   \"version\" : \"" + interfaceVersion + "\","
      + "   \"interfaceType\" : \"internal\","
      + "   \"handlers\" : [ "
      // Deployment service
      + "   {"
      + "    \"methods\" :  [ \"POST\" ],"
      + "    \"pathPattern\" : \"/_/deployment/modules\","
      + "    \"permissionsRequired\" : [ \"okapi.deployment.post\" ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/deployment/modules\","
      + "    \"permissionsRequired\" : [  ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/deployment/modules/{instanceId}\","
      + "    \"permissionsRequired\" : [  ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"DELETE\" ],"
      + "    \"pathPattern\" : \"/_/deployment/modules/{instanceId}\","
      + "    \"permissionsRequired\" : [ \"okapi.deployment.delete\"  ], "
      + "    \"type\" : \"internal\" "
      + "   }, "
      // Disovery service
      + "   {" // discovery, modules
      + "    \"methods\" :  [ \"POST\" ],"
      + "    \"pathPattern\" : \"/_/discovery/modules\","
      + "    \"permissionsRequired\" : [ \"okapi.discovery.post\"  ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/discovery/modules\","
      + "    \"permissionsRequired\" : [ ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/discovery/modules/{serviceId}\","
      + "    \"permissionsRequired\" : [ ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/discovery/modules/{serviceId}/{instanceId}\","
      + "    \"permissionsRequired\" : [ ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"PUT\" ],"
      + "    \"pathPattern\" : \"/_/discovery/modules/{serviceId}/{instanceId}\","
      + "    \"permissionsRequired\" : [ \"okapi.discovery.put\" ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"DELETE\" ],"
      + "    \"pathPattern\" : \"/_/discovery/modules/{serviceId}/{instanceId}\","
      + "    \"permissionsRequired\" : [ \"okapi.discovery.delete\" ], "
      + "    \"type\" : \"internal\" "
      + "   }, "
      + "   {" // discovery, health
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/discovery/health\","
      + "    \"permissionsRequired\" : [ ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/discovery/health/{serviceId}\","
      + "    \"permissionsRequired\" : [ ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/discovery/health/{serviceId}/{instanceId}\","
      + "    \"permissionsRequired\" : [ ], "
      + "    \"type\" : \"internal\" "
      + "   }, "
      + "   {" // discovery, nodes
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/discovery/nodes\","
      + "    \"permissionsRequired\" : [ ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"PUT\" ],"
      + "    \"pathPattern\" : \"/_/discovery/nodes/{nodeId}\","
      + "    \"permissionsRequired\" : [ \"okapi.discovery.nodes.put\" ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/discovery/nodes/{nodeId}\","
      + "    \"permissionsRequired\" : [ ], "
      + "    \"type\" : \"internal\" "
      + "   }, "
      // Proxy service
      + "   {" // proxy, modules
      + "    \"methods\" :  [ \"POST\" ],"
      + "    \"pathPattern\" : \"/_/proxy/modules\","
      + "    \"permissionsRequired\" : [ \"okapi.proxy.modules.post\" ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/proxy/modules\","
      + "    \"permissionsRequired\" : [ ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/proxy/modules/{moduleId}\","
      + "    \"permissionsRequired\" : [ ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"PUT\" ],"
      + "    \"pathPattern\" : \"/_/proxy/modules/{moduleId}\","
      + "    \"permissionsRequired\" : [ \"okapi.proxy.modules.put\" ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"DELETE\" ],"
      + "    \"pathPattern\" : \"/_/proxy/modules/{moduleId}\","
      + "    \"permissionsRequired\" : [ \"okapi.proxy.modules.delete\" ], "
      + "    \"type\" : \"internal\" "
      + "   },"
      + "   {" // proxy, tenants
      + "    \"methods\" :  [ \"POST\" ],"
      + "    \"pathPattern\" : \"/_/proxy/tenants\","
      + "    \"permissionsRequired\" : [ \"okapi.proxy.tenants.post\" ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/proxy/tenants\","
      + "    \"permissionsRequired\" : [ ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}\","
      + "    \"permissionsRequired\" : [ ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"PUT\" ],"
      + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}\","
      + "    \"permissionsRequired\" : [ \"okapi.proxy.tenants.put\" ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"DELETE\" ],"
      + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}\","
      + "    \"permissionsRequired\" : [ \"okapi.proxy.tenants.delete\" ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"POST\" ],"
      + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}/upgrade\","
      + "    \"permissionsRequired\" : [ \"okapi.proxy.tenants.upgrade.post\" ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"POST\" ],"
      + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}/install\","
      + "    \"permissionsRequired\" : [ \"okapi.proxy.tenants.install.post\" ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"POST\" ],"
      + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}/modules\","
      + "    \"permissionsRequired\" : [ \"okapi.proxy.tenants.modules.post\" ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}/modules\","
      + "    \"permissionsRequired\" : [ ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"POST\" ],"
      + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}/modules/{moduleId}\","
      + "    \"permissionsRequired\" : [ \"okapi.proxy.tenants.modules.enabled.post\" ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}/modules/{moduleId}\","
      + "    \"permissionsRequired\" : [ ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"DELETE\" ],"
      + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}/modules/{moduleId}\","
      + "    \"permissionsRequired\" : [ \"okapi.proxy.tenants.modules.enabled.delete\" ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}/interfaces\","
      + "    \"permissionsRequired\" : [  ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}/interfaces/{interfaceId}\","
      + "    \"permissionsRequired\" : [  ], "
      + "    \"type\" : \"internal\" "
      + "   },"
      + "   {" // proxy, health
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/proxy/health\","
      + "    \"permissionsRequired\" : [  ], "
      + "    \"type\" : \"internal\" "
      + "   },"
      + "   {" // proxy, pull
      + "    \"methods\" :  [ \"POST\" ],"
      + "    \"pathPattern\" : \"/_/proxy/pull/modules\","
      + "    \"permissionsRequired\" : [ \"okapi.proxy.pull.modules.post\" ], "
      + "    \"type\" : \"internal\" "
      + "   },"
      // Env service
      + "   {"
      + "    \"methods\" :  [ \"POST\" ],"
      + "    \"pathPattern\" : \"/_/env\","
      + "    \"permissionsRequired\" : [ \"okapi.env.post\" ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/env\","
      + "    \"permissionsRequired\" : [ \"okapi.env.list\" ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/env/{id}\","
      + "    \"permissionsRequired\" : [ \"okapi.env.get\" ], "
      + "    \"type\" : \"internal\" "
      + "   }, {"
      + "    \"methods\" :  [ \"DELETE\" ],"
      + "    \"pathPattern\" : \"/_/env/{id}\","
      + "    \"permissionsRequired\" : [ \"okapi.env.delete\" ], "
      + "    \"type\" : \"internal\" "
      + "   },"
      // version service
      + "{"
      + "    \"methods\" :  [ \"GET\" ],"
      + "    \"pathPattern\" : \"/_/version\","
      + "    \"permissionsRequired\" : [  ], "
      + "    \"type\" : \"internal\" "
      + "   }, "
      // The /_/invoke service can not be handled here, it needs to be hardcoded.
      // test service, only for developers
      + "{"
      + "    \"methods\" :  [ \"GET\", \"POST\" ],"
      + "    \"pathPattern\" : \"/_/test*\","
      + "    \"permissionsRequired\" : [  ], "
      + "    \"type\" : \"internal\" "
      + "   } ]"
      + " } ],"
      + "\"permissionSets\" : [ "
      // Permission bit names
      // Note that these don't get loaded to mod-perms yet. OKAPI-388
      + " { "
      + "   \"permissionName\" : \"okapi.deployment.post\", "
      + "   \"displayName\" : \"Okapi - deploy locally\", "
      + "   \"description\" : \"Deploy a module on 'this' node\" "
      + " }, { "
      + "   \"permissionName\" : \"okapi.deployment.delete\", "
      + "   \"displayName\" : \"Okapi - undeploy locally\", "
      + "   \"description\" : \"Undeploy a module on 'this' node\" "
      + " }, { "
      + "   \"permissionName\" : \"okapi.discovery.post\", "
      + "   \"displayName\" : \"Okapi - deploy a module on a given node\", "
      + "   \"description\" : \"Undeploy a module on 'this' node\" "
      + " }, { "
      + "   \"permissionName\" : \"okapi.discovery.put\", "
      + "   \"displayName\" : \"Okapi - update description of deployed module\", "
      + "   \"description\" : \"Update description\" "
      + " }, { "
      + "   \"permissionName\" : \"okapi.discovery.delete\", "
      + "   \"displayName\" : \"Okapi - undeploy a module instance\", "
      + "   \"description\" : \"Undeploy a given instance of a module\" "
      + " }, { "
      + "   \"permissionName\" : \"okapi.discovery.nodes.put\", "
      + "   \"displayName\" : \"Okapi - Update a node descriptor\", "
      + "   \"description\" : \"Update a node descriptor, usually to give it a new name\" "
      + " }, "
      + " { "
      + "   \"permissionName\" : \"okapi.proxy.modules.post\", "
      + "   \"displayName\" : \"Okapi - declare a module\", "
      + "   \"description\" : \"Declare a module\" "
      + " }, { "
      + "   \"permissionName\" : \"okapi.proxy.modules.put\", "
      + "   \"displayName\" : \"Okapi - update a module description\", "
      + "   \"description\" : \"Update a module description\" "
      + " }, { "
      + "   \"permissionName\" : \"okapi.proxy.modules.delete\", "
      + "   \"displayName\" : \"Okapi - undeclare a module\", "
      + "   \"description\" : \"Remove a moduleDescriptor from the system\" "
      + " }, "
      + " {"
      + "   \"permissionName\" : \"okapi.proxy.pull.modules.post\", "
      + "   \"displayName\" : \"Okapi - get ModuleDescriptors\", "
      + "   \"description\" : \"Get MDs from another Okapi, maybe a repo\" "
      + " },"
      + " { "
      + "   \"permissionName\" : \"okapi.proxy.tenants.post\", "
      + "   \"displayName\" : \"Okapi - create a tenant\", "
      + "   \"description\" : \"Declare a tenant\" "
      + " }, { "
      + "   \"permissionName\" : \"okapi.proxy.tenants.put\", "
      + "   \"displayName\" : \"Okapi - Update a tenant\", "
      + "   \"description\" : \"Update a tenant description\" "
      + " }, { "
      + "   \"permissionName\" : \"okapi.proxy.tenants.delete\", "
      + "   \"displayName\" : \"Okapi - Delete a tenant\", "
      + "   \"description\" : \"Remove a tenant description\" "
      + " }, { "
      + "   \"permissionName\" : \"okapi.proxy.tenants.upgrade.post\", "
      + "   \"displayName\" : \"Okapi - Upgrade modules\", "
      + "   \"description\" : \"Check if newer versions available, and upgrade\" "
      + " }, { "
      + "   \"permissionName\" : \"okapi.proxy.tenants.install.post\", "
      + "   \"displayName\" : \"Okapi - Enable modules and dependcencies\", "
      + "   \"description\" : \"Check dependencies and enable/disable modules as needed\" "
      + " }, { "
      + "   \"permissionName\" : \"okapi.proxy.tenants.modules.post\", "
      + "   \"displayName\" : \"Okapi - Enable a module for tenant\", "
      + "   \"description\" : \"Enable a module for the tenant\" "
      + " }, { "
      + "   \"permissionName\" : \"okapi.proxy.tenants.modules.enabled.post\", "
      + "   \"displayName\" : \"Okapi - Enable a module and disable another\", "
      + "   \"description\" : \"Enable a module for the tenant, and disable another one\" "
      + " }, {"
      + "   \"permissionName\" : \"okapi.proxy.tenants.modules.delete\", "
      + "   \"displayName\" : \"Okapi - Disable a module for tenant\", "
      + "   \"description\" : \"Disable a module for the tenant\" "
      + " }, "
      + " { "
      + "   \"permissionName\" : \"okapi.env.post\", "
      + "   \"displayName\" : \"Okapi - post env variable\", "
      + "   \"description\" : \"Set up an environment variable for all modules\" "
      + " }, { "
      + "   \"permissionName\" : \"okapi.env.list\", "
      + "   \"displayName\" : \"Okapi - list env variables\", "
      + "   \"description\" : \"List the environment variables\" "
      + " }, { "
      + "   \"permissionName\" : \"okapi.env.get\", "
      + "   \"displayName\" : \"Okapi - get one env variable\", "
      + "   \"description\" : \"Get one environment variable\" "
      + " }, { "
      + "   \"permissionName\" : \"okapi.env.delete\", "
      + "   \"displayName\" : \"Okapi - Delete env variable\", "
      + "   \"description\" : \"Delete one environment variable\" "
      + " }, "
      // Permission sets
      // Note that these don't get loaded to mod-perms yet. OKAPI-388
      + " { "
      + "   \"permissionName\" : \"okapi.deploy\", "
      + "   \"displayName\" : \"Okapi - Manage deployments\", "
      + "   \"description\" : \"Deploy and undeploy modules\", "
      + "   \"subPermissions\" : [ "
      + "     \"okapi.deployment.post\", \"okapi.deployment.delete\", "
      + "     \"okapi.discovery.post\", \"okapi.discovery.put\", "
      + "     \"okapi.discovery.delete\", \"okapi.discovery.nodes.put\" "
      + "   ]"
      + " }, "
      + " { "
      + "   \"permissionName\" : \"okapi.modules\", "
      + "   \"displayName\" : \"Okapi - Manage modules\", "
      + "   \"description\" : \"Manage ModuleDescriptors known to the system\", "
      + "   \"subPermissions\" : [ "
      + "     \"okapi.proxy.modules.post\", \"okapi.proxy.modules.put\", "
      + "     \"okapi.proxy.modules.delete\""
      + "   ]"
      + " }, "
      + " { "
      + "   \"permissionName\" : \"okapi.tenants\", "
      + "   \"displayName\" : \"Okapi - Manage tenants\", "
      + "   \"description\" : \"Manage tenants known to the system\", "
      + "   \"subPermissions\" : [ "
      + "     \"okapi.proxy.tenants.post\", \"okapi.proxy.tenants.put\", "
      + "     \"okapi.proxy.tenants.delete\""
      + "   ]"
      + " }, "
      + " { "
      + "   \"permissionName\" : \"okapi.tenantmodules\", "
      + "   \"displayName\" : \"Okapi - Manage modules enabled for a tenant\", "
      + "   \"description\" : \"Enable and disable modules for a tenant\", "
      + "   \"subPermissions\" : [ "
      + "     \"okapi.proxy.tenants.modules.post\", "
      + "     \"okapi.proxy.tenants.modules.enabled.post\", "
      + "     \"okapi.proxy.tenants.modules.enabled.delete\", "
      + "     \"okapi.proxy.tenants.modules.upgrade.post\", "
      + "     \"okapi.proxy.tenants.modules.install.post\" "
      + "   ]"
      + " }, "
      + " { "
      + "   \"permissionName\" : \"okapi.env\", "
      + "   \"displayName\" : \"Okapi - Manage environment variables\", "
      + "   \"description\" : \"Set up env vars for modules\", "
      + "   \"subPermissions\" : [ "
      + "     \"okapi.env.post\",  \"okapi.env.delete\", "
      + "     \"okapi.env.list\", \"okapi.env.get\" "
      + "   ]"
      + " }, "
      + " { "
      + "   \"permissionName\" : \"okapi.all\", "
      + "   \"displayName\" : \"Okapi - All permissions\", "
      + "   \"description\" : \"Anything goes\", "
      + "   \"subPermissions\" : [ "
      + "     \"okapi.deploy\",  \"okapi.modules\", "
      + "     \"okapi.tenants\", \"okapi.tenantmodules\", "
      + "     \"okapi.env\" "
      + "   ]"
      + " } "
      + "],"
      + "\"requires\" : [ ]" // can not require any other interfaces.
      + "}";
    final ModuleDescriptor md = Json.decodeValue(doc, ModuleDescriptor.class);
    return md;
  }

  /**
   * Helper to make a Location header. Takes the request path, and appends /id
   * to it, and puts it in the Location header in pc.response. Also sets the
   * return code to 201-Created. You can overwrite it after, if needed.
   *
   * @param pc
   * @param id
   * @return "" if all ok, or an error message
   */
  private String makeLocation(ProxyContext pc, String id, String baseUri) {
    String uri = "";
    try {
      if (baseUri == null) {
        uri = pc.getCtx().request().uri();
      } else {
        uri = baseUri;
      }
      uri = uri + "/" + URLEncoder.encode(id, "UTF-8");
      pc.getCtx().response().putHeader("Location", uri);
      pc.getCtx().response().setStatusCode(201);
      return "";
    } catch (UnsupportedEncodingException ex) {
      logger.warn("Could not encode location " + uri + "  " + id);
      return ("Error in encoding location id " + id + ". "
        + ex.getMessage());
    }
  }
  private String makeLocation(ProxyContext pc, String id) {
    return makeLocation(pc, id, null);
  }

  private void createTenant(ProxyContext pc, String body,
    Handler<ExtendedAsyncResult<String>> fut) {
    try {
      final TenantDescriptor td = Json.decodeValue(body, TenantDescriptor.class);
      if (td.getId() == null || td.getId().isEmpty()) {
        td.setId(UUID.randomUUID().toString());
      }
      final String id = td.getId();
      if (!id.matches("^[a-z0-9._-]+$")) {
        fut.handle(new Failure<>(USER, "Invalid tenant id '" + id + "'"));
        return;
      }
      Tenant t = new Tenant(td);
      tenantManager.insert(t, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
          return;
        }
        RoutingContext ctx = pc.getCtx();
        String locErr = makeLocation(pc, id);
        if (!locErr.isEmpty()) {
          fut.handle(new Failure<>(INTERNAL, locErr));
          return;
        }
        final String s = Json.encodePrettily(t.getDescriptor());
        fut.handle(new Success<>(s));
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(USER, ex));
    }
  }

  private void updateTenant(ProxyContext pc, String id, String body,
    Handler<ExtendedAsyncResult<String>> fut) {
    try {
      final TenantDescriptor td = Json.decodeValue(body, TenantDescriptor.class);
      if (!id.equals(td.getId())) {
        fut.handle(new Failure<>(USER, "Tenant.id=" + td.getId() + " id=" + id));
        return;
      }
      Tenant t = new Tenant(td);
      tenantManager.updateDescriptor(td, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(NOT_FOUND, res.cause()));
          return;
        }
        final String s = Json.encodePrettily(t.getDescriptor());
        fut.handle(new Success<>(s));
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(USER, ex));
    }
  }

  private void listTenants(ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {
    tenantManager.list(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      List<TenantDescriptor> tdl = res.result();
      String s = Json.encodePrettily(tdl);
      fut.handle(new Success<>(s));
    });
  }

  private void getTenant(ProxyContext pc, String id,
    Handler<ExtendedAsyncResult<String>> fut) {
    tenantManager.get(id, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      Tenant te = res.result();
      TenantDescriptor td = te.getDescriptor();
      String s = Json.encodePrettily(td);
      fut.handle(new Success<>(s));
    });
  }

  private void deleteTenant(ProxyContext pc, String id,
    Handler<ExtendedAsyncResult<String>> fut) {
    if (XOkapiHeaders.SUPERTENANT_ID.equals(id)) {
      fut.handle(new Failure<>(USER, "Can not delete the superTenant " + id));
      // Change of behavior, used to return 403
      return;
    }
    tenantManager.delete(id, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      fut.handle(new Success<>(""));
    });
  }

  private void enableModuleForTenant(ProxyContext pc, String id, String body,
    Handler<ExtendedAsyncResult<String>> fut) {
    try {
      final TenantModuleDescriptor td = Json.decodeValue(body,
        TenantModuleDescriptor.class);
      String module_to = td.getId();
      tenantManager.enableAndDisableModule(id, null, module_to, pc, eres -> {
        if (eres.failed()) {
          fut.handle(new Failure<>(eres.getType(), eres.cause()));
          return;
        }
        td.setId(eres.result());
        String locErr = makeLocation(pc, td.getId());
        if (!locErr.isEmpty()) {
          fut.handle(new Failure<>(INTERNAL, locErr));
          return;
        }
        fut.handle(new Success<>(Json.encodePrettily(td)));
      });

    } catch (DecodeException ex) {
      fut.handle(new Failure<>(USER, ex));
    }
  }

  private void disableModuleForTenant(ProxyContext pc, String id, String module,
    Handler<ExtendedAsyncResult<String>> fut) {
    pc.debug("disablemodule t=" + id + " m=" + module);
    tenantManager.enableAndDisableModule(id, module, null, pc, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      fut.handle(new Success<>(""));
    });
  }

  private boolean getParamBoolean(HttpServerRequest req, String name, boolean defValue) {
    String v = req.getParam(name);
    if (v == null) {
      return defValue;
    } else if ("true".equals(v)) {
      return true;
    } else if ("false".equals(v)) {
      return false;
    }
    throw new DecodeException("Bad boolean for parameter " + name + ": " + v);
  }

  private void installModulesForTenant(ProxyContext pc, String id,
    String body, Handler<ExtendedAsyncResult<String>> fut) {

    try {
      final boolean simulate = getParamBoolean(pc.getCtx().request(), "simulate", false);
      final boolean preRelease = getParamBoolean(pc.getCtx().request(), "preRelease", true);

      final TenantModuleDescriptor[] tml = Json.decodeValue(body,
        TenantModuleDescriptor[].class);
      List<TenantModuleDescriptor> tm = new LinkedList<>();
      for (int i = 0; i < tml.length; i++) {
        tm.add(tml[i]);
      }
      tenantManager.installUpgradeModules(id, pc, simulate, preRelease, tm, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          logger.info("installUpgradeModules returns:\n" + Json.encodePrettily(res.result()));
          fut.handle(new Success<>(Json.encodePrettily(res.result())));
        }
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(USER, ex));
    }
  }

  private void upgradeModulesForTenant(ProxyContext pc, String id,
    String body, Handler<ExtendedAsyncResult<String>> fut) {

    try {
      final boolean simulate = getParamBoolean(pc.getCtx().request(), "simulate", false);
      final boolean preRelease = getParamBoolean(pc.getCtx().request(), "preRelease", true);

      tenantManager.installUpgradeModules(id, pc, simulate, preRelease, null, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          logger.info("installUpgradeModules returns:\n" + Json.encodePrettily(res.result()));
          fut.handle(new Success<>(Json.encodePrettily(res.result())));
        }
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(USER, ex));
    }
  }

  private void upgradeModuleForTenant(ProxyContext pc, String id, String mod,
    String body, Handler<ExtendedAsyncResult<String>> fut) {
    try {
      final String module_from = mod;
      final TenantModuleDescriptor td = Json.decodeValue(body,
        TenantModuleDescriptor.class);
      final String module_to = td.getId();
      tenantManager.enableAndDisableModule(id, module_from, module_to, pc, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
          return;
        }
        td.setId(res.result());
        final String uri = pc.getCtx().request().uri();
        final String regex = "^(.*)/" + module_from + "$";
        final String newuri = uri.replaceAll(regex, "$1");
        String locErr = makeLocation(pc, td.getId(), newuri);
        if (!locErr.isEmpty()) {
          fut.handle(new Failure<>(INTERNAL, locErr));
          return;
        }
        fut.handle(new Success<>(Json.encodePrettily(td)));
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(USER, ex));
    }
  }

  private void listModulesForTenant(ProxyContext pc, String id,
    Handler<ExtendedAsyncResult<String>> fut) {
    tenantManager.listModules(id, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      List<String> ml = res.result();
      Iterator<String> mli = ml.iterator();  // into a list of objects
      ArrayList<TenantModuleDescriptor> ta = new ArrayList<>();
      while (mli.hasNext()) {
        TenantModuleDescriptor tmd = new TenantModuleDescriptor();
        tmd.setId(mli.next());
        ta.add(tmd);
      }
      String s = Json.encodePrettily(ta);
      fut.handle(new Success<>(s));
    });
  }

  private void getModuleForTenant(ProxyContext pc, String id, String mod,
    Handler<ExtendedAsyncResult<String>> fut) {
    tenantManager.get(id, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      Tenant t = res.result();
      Set<String> ml = t.listModules();  // Convert the list of module names
      if (!ml.contains(mod)) {
        fut.handle(new Failure<>(NOT_FOUND, mod));
        return;
      }
      TenantModuleDescriptor tmd = new TenantModuleDescriptor();
      tmd.setId(mod);
      String s = Json.encodePrettily(tmd);
      fut.handle(new Success<>(s));
    });
  }

  private void listModulesFromInterface(ProxyContext pc, String id, String intId,
    Handler<ExtendedAsyncResult<String>> fut) {
    tenantManager.listModulesFromInterface(id, intId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      List<ModuleDescriptor> mdL = res.result();
      ArrayList<TenantModuleDescriptor> ta = new ArrayList<>();
      for (ModuleDescriptor md : mdL) {
        TenantModuleDescriptor tmd = new TenantModuleDescriptor();
        tmd.setId(md.getId());
        ta.add(tmd);
      }
      String s = Json.encodePrettily(ta);
      fut.handle(new Success<>(s));
    });
  }

  private void createModule(ProxyContext pc, String body,
    Handler<ExtendedAsyncResult<String>> fut) {
    try {
      final ModuleDescriptor md = Json.decodeValue(body, ModuleDescriptor.class);
      if (md.getId() == null || md.getId().isEmpty()) {
        md.setId(UUID.randomUUID().toString());
      }
      String validerr = md.validate(pc);
      if (!validerr.isEmpty()) {
        fut.handle(new Failure<>(USER, validerr));
        return;
      }
      moduleManager.create(md, cres -> {
        String uri = "";
        if (cres.failed()) {
          fut.handle(new Failure<>(cres.getType(), cres.cause()));
          return;
        }
        final String s = Json.encodePrettily(md);
        String locErr = makeLocation(pc, md.getId());
        if (!locErr.isEmpty()) {
          fut.handle(new Failure<>(INTERNAL, locErr));
          return;
        }
        fut.handle(new Success<>(s));
      });
    } catch (DecodeException ex) {
      pc.debug("Failed to decode md: " + pc.getCtx().getBodyAsString());
      fut.handle(new Failure<>(USER, ex));
    }
  }

  private void getModule(ProxyContext pc, String id,
    Handler<ExtendedAsyncResult<String>> fut) {
    moduleManager.get(id, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void listModules(ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {
    try {
      ModuleId filter = null;
      String filterStr = pc.getCtx().request().getParam("filter");
      if (filterStr != null) {
        filter = new ModuleId(filterStr);
      }
      final String orderByStr = pc.getCtx().request().getParam("orderBy");
      final String orderStr = pc.getCtx().request().getParam("order");
      final boolean preRelease = getParamBoolean(pc.getCtx().request(), "preRelease", true);
      moduleManager.getModulesWithFilter(filter, preRelease, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
          return;
        }
        List<ModuleDescriptor> mdl = res.result();
        if (orderByStr != null) {
          if (!"id".equals(orderByStr)) {
            logger.warn("unknown orderBy field: " + orderByStr);
            fut.handle(new Failure<>(USER, "unknown orderBy field: " + orderByStr));
            return;
          }
          if (orderStr == null || "desc".equals(orderStr)) {
            Collections.sort(mdl, Collections.reverseOrder());
          } else if ("asc".equals(orderStr)) {
            Collections.sort(mdl);
          } else {
            logger.warn("invalid order value: " + orderStr);
            fut.handle(new Failure<>(USER, "invalid order value: " + orderStr));
            return;
          }
        } else {
          Collections.sort(mdl, Collections.reverseOrder());
        }
        List<ModuleDescriptorBrief> ml = new ArrayList<>(mdl.size());
        for (ModuleDescriptor md : mdl) {
          ml.add(new ModuleDescriptorBrief(md));
        }
        String s = Json.encodePrettily(ml);
        fut.handle(new Success<>(s));
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(USER, ex));
    }
  }

  private void updateModule(ProxyContext pc, String id, String body,
    Handler<ExtendedAsyncResult<String>> fut) {
    try {
      final ModuleDescriptor md = Json.decodeValue(body, ModuleDescriptor.class);
      if (!id.equals(md.getId())) {
        fut.handle(new Failure<>(USER, "Module.id=" + md.getId() + " id=" + id));
        return;
      }
      String validerr = md.validate(pc);
      if (!validerr.isEmpty()) {
        fut.handle(new Failure<>(USER, validerr));
        return;
      }
      moduleManager.update(md, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
          return;
        }
        final String s = Json.encodePrettily(md);
        fut.handle(new Success<>(s));
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(USER, ex));
    }
  }

  private void deleteModule(ProxyContext pc, String id,
    Handler<ExtendedAsyncResult<String>> fut) {
    moduleManager.delete(id, res -> {
      if (res.failed()) {
        pc.error("delete module failed: " + res.getType()
          + ":" + res.cause().getMessage());
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      fut.handle(new Success<>(""));
    });
  }

  private void getDeployment(ProxyContext pc, String id,
    Handler<ExtendedAsyncResult<String>> fut) {
    deploymentManager.get(id, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void listDeployments(ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {
    deploymentManager.list(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void createDeployment(ProxyContext pc, String body,
    Handler<ExtendedAsyncResult<String>> fut) {
    try {
      final DeploymentDescriptor pmd = Json.decodeValue(body,
        DeploymentDescriptor.class);
      deploymentManager.deploy(pmd, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
          return;
        }
        final String s = Json.encodePrettily(res.result());
        String locErr = makeLocation(pc, res.result().getInstId());
        if (!locErr.isEmpty()) {
          fut.handle(new Failure<>(INTERNAL, locErr));
          return;
        }
        fut.handle(new Success<>(s));
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(USER, ex));
    }
  }

  private void deleteDeployment(ProxyContext pc, String id,
    Handler<ExtendedAsyncResult<String>> fut) {
    deploymentManager.undeploy(id, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(""));
    });
  }

  private void getDiscoveryNode(ProxyContext pc, String id,
    Handler<ExtendedAsyncResult<String>> fut) {
    if (id == null) {
      fut.handle(new Failure<>(USER, "id missing"));
      return;
    }
    logger.debug("Int: getDiscoveryNode: " + id);
    discoveryManager.getNode(id, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void putDiscoveryNode(ProxyContext pc, String id, String body,
    Handler<ExtendedAsyncResult<String>> fut) {
    if (id == null) {
      fut.handle(new Failure<>(USER, "id missing"));
      return;
    }
    logger.debug("Int: putDiscoveryNode: " + id + " " + body);
    final NodeDescriptor nd = Json.decodeValue(body, NodeDescriptor.class);
    if (!id.equals(nd.getNodeId())) {
      fut.handle(new Failure<>(USER, "Module.id=" + nd.getNodeId() + " id=" + id));
      return;
    }
    discoveryManager.updateNode(id, nd, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void listDiscoveryNodes(ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {
    discoveryManager.getNodes(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void listDiscoveryModules(ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {
    discoveryManager.get(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void discoveryGetSrvcId(ProxyContext pc, String srvcId,
    Handler<ExtendedAsyncResult<String>> fut) {
    if (srvcId == null) {
      fut.handle(new Failure<>(USER, "srvcId missing"));
      return;
    }
    discoveryManager.get(srvcId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      List<DeploymentDescriptor> result = res.result();
      if (result.isEmpty()) {
        fut.handle(new Failure<>(NOT_FOUND, "srvcId " + srvcId + " not found"));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void discoveryGetInstId(ProxyContext pc, String srvcId, String instId,
    Handler<ExtendedAsyncResult<String>> fut) {
    if (srvcId == null || srvcId.isEmpty()) {
      fut.handle(new Failure<>(USER, "srvcId missing"));
      return;
    }
    if (instId == null || instId.isEmpty()) {
      fut.handle(new Failure<>(USER, "instId missing"));
      return;
    }
    discoveryManager.get(srvcId, instId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void discoveryDeploy(ProxyContext pc, String body,
    Handler<ExtendedAsyncResult<String>> fut) {
    try {
      final DeploymentDescriptor pmd = Json.decodeValue(body,
        DeploymentDescriptor.class);
      discoveryManager.addAndDeploy(pmd, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
          return;
        }
        DeploymentDescriptor md = res.result();
        final String s = Json.encodePrettily(md);
        final String baseuri = pc.getCtx().request().uri()
          + "/" + md.getSrvcId();
        String locErr = makeLocation(pc, md.getInstId(), baseuri);
        if (!locErr.isEmpty()) {
          fut.handle(new Failure<>(INTERNAL, locErr));
          return;
        }
        fut.handle(new Success<>(s));
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(USER, ex));
    }
  }

  private void discoveryUndeploy(ProxyContext pc, String srvcId, String instId,
    Handler<ExtendedAsyncResult<String>> fut) {
    if (srvcId == null || srvcId.isEmpty()) {
      fut.handle(new Failure<>(USER, "srvcId missing"));
      return;
    }
    if (instId == null || instId.isEmpty()) {
      fut.handle(new Failure<>(USER, "instId missing"));
      return;
    }
    discoveryManager.removeAndUndeploy(srvcId, instId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      fut.handle(new Success<>(""));
    });
  }

  private void discoveryHealthAll(ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {
    discoveryManager.health(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void discoveryHealthSrvcId(ProxyContext pc, String srvcId,
    Handler<ExtendedAsyncResult<String>> fut) {
    if (srvcId == null || srvcId.isEmpty()) {
      fut.handle(new Failure<>(USER, "srvcId missing"));
      return;
    }
    discoveryManager.health(srvcId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void discoveryHealthOne(ProxyContext pc, String srvcId, String instId,
    Handler<ExtendedAsyncResult<String>> fut) {
    if (srvcId == null || srvcId.isEmpty()) {
      fut.handle(new Failure<>(USER, "srvcId missing"));
      return;
    }
    if (instId == null || instId.isEmpty()) {
      fut.handle(new Failure<>(USER, "instId missing"));
      return;
    }
    discoveryManager.health(srvcId, instId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void listEnv(ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {
    envManager.get(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void getEnv(ProxyContext pc, String id,
    Handler<ExtendedAsyncResult<String>> fut) {
    if (id == null) {
      fut.handle(new Failure<>(USER, "id missing"));
      return;
    }
    envManager.get(id, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void createEnv(ProxyContext pc, String body,
    Handler<ExtendedAsyncResult<String>> fut) {
    try {
      final EnvEntry pmd = Json.decodeValue(body, EnvEntry.class);
      envManager.add(pmd, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
          return;
        }
        final String js = Json.encodePrettily(pmd);
        String locErr = makeLocation(pc, pmd.getName());
        if (!locErr.isEmpty()) {
          fut.handle(new Failure<>(INTERNAL, locErr));
          return;
        }
        fut.handle(new Success<>(js));
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(USER, ex));
    }
  }

  private void deleteEnv(ProxyContext pc, String id,
    Handler<ExtendedAsyncResult<String>> fut) {
    if (id == null) {
      fut.handle(new Failure<>(USER, "id missing"));
      return;
    }
    envManager.remove(id, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      fut.handle(new Success<>(""));
    });
  }

  private void pullModules(ProxyContext pc, String body,
    Handler<ExtendedAsyncResult<String>> fut) {
    try {
      final PullDescriptor pmd = Json.decodeValue(body, PullDescriptor.class);
      pullManager.pull(pmd, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
          return;
        }
        fut.handle(new Success<>(Json.encodePrettily(res.result())));
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(USER, ex));
    }
  }

  /**
   * Pretty simplistic health check.
   */
  private void getHealth(ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {
    fut.handle(new Success<>("[ ]"));
  }

  private void getVersion(ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {
    String v = okapiVersion;
    if (v == null) {
      v = "(null)";
    }
    pc.getCtx().response().putHeader("Content-Type", "text/plain"); // !!
    fut.handle(new Success<>(v));
  }

  private void getRootLogLevel(ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {
    String lev = logHelper.getRootLogLevel();
    LogHelper.LogLevelInfo li = new LogHelper.LogLevelInfo(lev);
    String rj = Json.encode(li);
    fut.handle(new Success<>(rj));
  }

  private void setRootLogLevel(ProxyContext pc, String body,
    Handler<ExtendedAsyncResult<String>> fut) {
    final LogHelper.LogLevelInfo inf = Json.decodeValue(body,
      LogHelper.LogLevelInfo.class);
    logHelper.setRootLogLevel(inf.getLevel());
    fut.handle(new Success<>(body));
    // Should at least return the actual log level, not whatever we post
    // We can post FOOBAR, and nothing changes...
  }

  /**
   * Dispatcher for all the built-in services.
   *
   * @param req The request body
   * @param pc Proxy context, gives a ctx, path, and method
   * @param fut Callback with the response body
   *
   * Note that there are restrictions what we can do with the ctx. We can set a
   * result code (defaults to 200 OK) in successful operations, but be aware
   * that only if this is the last module in the pipeline, will this code be
   * returned to the caller. Often that is the case. We can look at the request,
   * at least the (normalized) path and method, but the previous filters may
   * have done something to them already.
   *
   */
  public void internalService(String req, ProxyContext pc,
    Handler<ExtendedAsyncResult<String>> fut) {
    RoutingContext ctx = pc.getCtx();
    String p = ctx.normalisedPath();
    String[] segments = p.split("/");
    int n = segments.length;
    String[] decodedSegs = new String[n];
    for (int i = 0; i < n; i++) {
      decodedSegs[i] = Utils.urlDecode(segments[i], false);
    }
    HttpMethod m = ctx.request().method();
    pc.debug("internalService '" + ctx.request().method() + "'"
      + " '" + p + "'  nseg=" + n + " :" + Json.encode(decodedSegs));
    if (p.endsWith("/")) {
      n = 0; // force a notfound error for trailing slash
    }
    // default to json replies, error code overrides to text/plain
    pc.getCtx().response().putHeader("Content-Type", "application/json");
    if (n >= 4 && p.startsWith("/_/proxy/")) { // need at least /_/proxy/something
      if (segments[3].equals("modules")
        && moduleManager != null) {
        // /_/proxy/modules
        if (n == 4 && m.equals(GET)) {
          listModules(pc, fut);
          return;
        }
        if (n == 4 && m.equals(POST)) {
          createModule(pc, req, fut);
          return;
        }
        // /_/proxy/modules/:id
        if (n == 5 && m.equals(GET)) {
          getModule(pc, decodedSegs[4], fut);
          return;
        }
        if (n == 5 && m.equals(PUT)) {
          updateModule(pc, decodedSegs[4], req, fut);
          return;
        }
        if (n == 5 && m.equals(DELETE)) {
          deleteModule(pc, decodedSegs[4], fut);
          return;
        }
      } // /_/proxy/modules

      if (segments[3].equals("tenants")
        && tenantManager != null) {
        // /_/proxy/tenants
        if (n == 4 && m.equals(GET)) {
          listTenants(pc, fut);
          return;
        }
        if (n == 4 && m.equals(POST)) {
          createTenant(pc, req, fut);
          return;
        }
        // /_/proxy/tenants/:id
        if (n == 5 && m.equals(GET)) {
          getTenant(pc, decodedSegs[4], fut);
          return;
        }
        if (n == 5 && m.equals(PUT)) {
          updateTenant(pc, decodedSegs[4], req, fut);
          return;
        }
        if (n == 5 && m.equals(DELETE)) {
          deleteTenant(pc, decodedSegs[4], fut);
          return;
        }
        // /_/proxy/tenants/:id/modules
        if (n == 6 && m.equals(GET) && segments[5].equals("modules")) {
          listModulesForTenant(pc, decodedSegs[4], fut);
          return;
        }
        if (n == 6 && m.equals(POST) && segments[5].equals("modules")) {
          enableModuleForTenant(pc, decodedSegs[4], req, fut);
          return;
        }
        // /_/proxy/tenants/:id/modules/:mod
        if (n == 7 && m.equals(GET) && segments[5].equals("modules")) {
          getModuleForTenant(pc, decodedSegs[4], decodedSegs[6], fut);
          return;
        }
        if (n == 7 && m.equals(POST) && segments[5].equals("modules")) {
          upgradeModuleForTenant(pc, decodedSegs[4], decodedSegs[6], req, fut);
          return;
        }
        if (n == 7 && m.equals(DELETE) && segments[5].equals("modules")) {
          disableModuleForTenant(pc, decodedSegs[4], decodedSegs[6], fut);
          return;
        }
        // /_/proxy/tenants/:id/install
        if (n == 6 && m.equals(POST) && segments[5].equals("install")) {
          installModulesForTenant(pc, decodedSegs[4], req, fut);
          return;
        }
        // /_/proxy/tenants/:id/upgrade
        if (n == 6 && m.equals(POST) && segments[5].equals("upgrade")) {
          upgradeModulesForTenant(pc, decodedSegs[4], req, fut);
          return;
        }

        // /_/proxy/tenants/:id/interfaces/:int
        if (n == 7 && m.equals(GET) && segments[5].equals("interfaces")) {
          listModulesFromInterface(pc, decodedSegs[4], decodedSegs[6], fut);
          return;
        }
      } // /_/proxy/tenants

      // /_/proxy/pull/modules
      if (n == 5 && segments[3].equals("pull") && segments[4].equals("modules")
        && m.equals(POST) && pullManager != null) {
        pullModules(pc, req, fut);
        return;
      }
      // /_/proxy/health
      if (n == 4 && segments[3].equals("health") && m.equals(GET)) {
        getHealth(pc, fut);
        return;
      }

    } // _/proxy

    // deployment
    if (n >= 4 && p.startsWith("/_/deployment/")
      && segments[3].equals("modules")
      && deploymentManager != null) {
      // /_/deployment/modules
      if (n == 4 && m.equals(GET)) {
        listDeployments(pc, fut);
        return;
      }
      if (n == 4 && m.equals(POST)) {
        createDeployment(pc, req, fut);
        return;
      }
      // /_/deployment/modules/:id:
      if (n == 5 && m.equals(GET)) {
        getDeployment(pc, decodedSegs[4], fut);
        return;
      }
      if (n == 5 && m.equals(DELETE)) {
        deleteDeployment(pc, decodedSegs[4], fut);
        return;
      }
    } // deployment

    if (n >= 4 && p.startsWith("/_/discovery/")
      && discoveryManager != null) {
      // /_/discovery/nodes
      if (n == 4 && segments[3].equals("nodes") && m.equals(GET)) {
        listDiscoveryNodes(pc, fut);
        return;
      }
      // /_/discovery/nodes/:nodeid
      if (n == 5 && segments[3].equals("nodes") && m.equals(GET)) {
        getDiscoveryNode(pc, decodedSegs[4], fut);
        return;
      }
      // /_/discovery/nodes/:nodeid
      if (n == 5 && segments[3].equals("nodes") && m.equals(PUT)) {
        putDiscoveryNode(pc, decodedSegs[4], req, fut);
        return;
      }

      // /_/discovery/modules
      if (n == 4 && segments[3].equals("modules") && m.equals(GET)) {
        listDiscoveryModules(pc, fut);
        return;
      }
      if (n == 4 && segments[3].equals("modules") && m.equals(POST)) {
        discoveryDeploy(pc, req, fut);
        return;
      }
      // /_/discovery/modules/:srvcid
      if (n == 5 && segments[3].equals("modules") && m.equals(GET)) {
        discoveryGetSrvcId(pc, decodedSegs[4], fut);
        return;
      }
      // /_/discovery/modules/:srvcid/:instid"
      if (n == 6 && segments[3].equals("modules") && m.equals(GET)) {
        discoveryGetInstId(pc, decodedSegs[4], decodedSegs[5], fut);
        return;
      }
      if (n == 6 && segments[3].equals("modules") && m.equals(DELETE)) {
        discoveryUndeploy(pc, decodedSegs[4], decodedSegs[5], fut);
        return;
      }
      // /_/discovery/health
      if (n == 4 && segments[3].equals("health") && m.equals(GET)) {
        discoveryHealthAll(pc, fut);
        return;
      }
      // /_/discovery/health/:srvcId
      if (n == 5 && segments[3].equals("health") && m.equals(GET)) {
        discoveryHealthSrvcId(pc, decodedSegs[4], fut);
        return;
      }
      // /_/discovery/health/:srvcId/:instid
      if (n == 6 && segments[3].equals("health") && m.equals(GET)) {
        discoveryHealthOne(pc, decodedSegs[4], decodedSegs[5], fut);
        return;
      }
    } // discovery

    if (n >= 2 && p.startsWith("/_/env")
      && segments[2].equals("env")) { // not envXX or such

      // /_/env
      if (n == 3 && m.equals(GET)) {
        listEnv(pc, fut);
        return;
      }
      if (n == 3 && m.equals(POST)) {
        createEnv(pc, req, fut);
        return;
      }
      // /_/env/name
      if (n == 4 && m.equals(GET)) {
        getEnv(pc, decodedSegs[3], fut);
        return;
      }
      if (n == 4 && m.equals(DELETE)) {
        deleteEnv(pc, decodedSegs[3], fut);
        return;
      }

    } // env

    if (p.equals("/_/version") && m.equals(GET)) {
      getVersion(pc, fut);
      return;
    }

    if (n >= 2 && p.startsWith("/_/test/")) {
      if (n == 4 && m.equals(GET) && segments[3].equals("loglevel")) {
        getRootLogLevel(pc, fut);
        return;
      }
      if (n == 4 && m.equals(POST) && segments[3].equals("loglevel")) {
        setRootLogLevel(pc, req, fut);
        return;
      }
    }

    // If we get here, nothing matched.
    String slash = "";
    if (p.endsWith("/")) {
      slash = " (try without a trailing slash)";
    }
    fut.handle(new Failure<>(NOT_FOUND, "No internal module found for "
      + m + " " + p + slash));
  }

}
