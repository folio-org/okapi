package org.folio.okapi.managers;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.NodeDescriptor;
import org.folio.okapi.bean.PullDescriptor;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;
import org.folio.okapi.common.UrlDecoder;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.util.GraphDot;
import org.folio.okapi.util.ModuleUtil;
import org.folio.okapi.util.ProxyContext;
import org.folio.okapi.util.TenantInstallOptions;

/**
 * Okapi's built-in module. Managing /_/ endpoints.
 * /_/proxy/modules /_/proxy/tenants /_/proxy/health /_/proxy/pull
 * /_/deployment /_/discovery /_/env /_/version etc
 * Note that the endpoint /_/invoke/ can not be handled here, as the proxy must
 * read the request body before invoking this built-in module, and /_/invoke
 * uses ctx.reroute(), which assumes the body has not been read.
 */
@java.lang.SuppressWarnings({"squid:S1192"})
public class InternalModule {

  private final Logger logger = OkapiLogger.get();

  private final ModuleManager moduleManager;
  private final TenantManager tenantManager;
  private final DeploymentManager deploymentManager;
  private final DiscoveryManager discoveryManager;
  private final EnvManager envManager;
  private final PullManager pullManager;
  private final String okapiVersion;
  private static final String INTERFACE_VERSION = "1.9";
  private final Messages messages = Messages.getInstance();

  /**
   * Construct internal module.
   * @param modules module manager
   * @param tenantManager tenant manager
   * @param deploymentManager deployment manager
   * @param discoveryManager discovery manager
   * @param envManager event manager
   * @param pullManager pull manager
   * @param okapiVersion Okapi version
   */
  public InternalModule(ModuleManager modules, TenantManager tenantManager,
                        DeploymentManager deploymentManager, DiscoveryManager discoveryManager,
                        EnvManager envManager, PullManager pullManager, String okapiVersion) {
    this.moduleManager = modules;
    this.tenantManager = tenantManager;
    this.deploymentManager = deploymentManager;
    this.discoveryManager = discoveryManager;
    this.envManager = envManager;
    this.pullManager = pullManager;
    this.okapiVersion = okapiVersion;
    logger.info("InternalModule starting okapiversion={}", okapiVersion);
  }

  /**
   * Return module descriptor for okapi itself.
   * @param okapiVersion Okapi version; null and "0.0.0" will be assumed
   * @return module descriptor as string
   */
  public static ModuleDescriptor moduleDescriptor(String okapiVersion) {
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
        + " \"name\" : \"" + "Okapi" + "\","
        + " \"provides\" : [ {"
        + "   \"id\" : \"okapi-proxy\","
        + "   \"version\" : \"" + INTERFACE_VERSION + "\","
        + "   \"interfaceType\" : \"internal\"," // actually, null.
        + "   \"handlers\" : [ ]"
        + " }, {"
        + "   \"id\" : \"okapi\","
        + "   \"version\" : \"" + INTERFACE_VERSION + "\","
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
        + "    \"permissionsRequired\" : [ \"okapi.deployment.get\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"GET\" ],"
        + "    \"pathPattern\" : \"/_/deployment/modules/{instanceId}\","
        + "    \"permissionsRequired\" : [ \"okapi.deployment.get\" ], "
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
        + "    \"permissionsRequired\" : [ \"okapi.discovery.get\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"GET\" ],"
        + "    \"pathPattern\" : \"/_/discovery/modules/{serviceId}\","
        + "    \"permissionsRequired\" : [ \"okapi.discovery.get\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"GET\" ],"
        + "    \"pathPattern\" : \"/_/discovery/modules/{serviceId}/{instanceId}\","
        + "    \"permissionsRequired\" : [ \"okapi.discovery.get\"], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"PUT\" ],"
        + "    \"pathPattern\" : \"/_/discovery/modules/{serviceId}/{instanceId}\","
        + "    \"permissionsRequired\" : [ \"okapi.discovery.put\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"DELETE\" ],"
        + "    \"pathPattern\" : \"/_/discovery/modules\","
        + "    \"permissionsRequired\" : [ \"okapi.discovery.delete\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"DELETE\" ],"
        + "    \"pathPattern\" : \"/_/discovery/modules/{serviceId}\","
        + "    \"permissionsRequired\" : [ \"okapi.discovery.delete\" ], "
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
        + "    \"permissionsRequired\" : [ \"okapi.discovery.health.get\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"GET\" ],"
        + "    \"pathPattern\" : \"/_/discovery/health/{serviceId}\","
        + "    \"permissionsRequired\" : [ \"okapi.discovery.health.get\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"GET\" ],"
        + "    \"pathPattern\" : \"/_/discovery/health/{serviceId}/{instanceId}\","
        + "    \"permissionsRequired\" : [ \"okapi.discovery.health.get\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, "
        + "   {" // discovery, nodes
        + "    \"methods\" :  [ \"GET\" ],"
        + "    \"pathPattern\" : \"/_/discovery/nodes\","
        + "    \"permissionsRequired\" : [ \"okapi.discovery.nodes.get\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"PUT\" ],"
        + "    \"pathPattern\" : \"/_/discovery/nodes/{nodeId}\","
        + "    \"permissionsRequired\" : [ \"okapi.discovery.nodes.put\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"GET\" ],"
        + "    \"pathPattern\" : \"/_/discovery/nodes/{nodeId}\","
        + "    \"permissionsRequired\" : [ \"okapi.discovery.nodes.get\"  ], "
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
        + "    \"permissionsRequired\" : [ \"okapi.proxy.modules.list\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"GET\" ],"
        + "    \"pathPattern\" : \"/_/proxy/modules/{moduleId}\","
        + "    \"permissionsRequired\" : [ \"okapi.proxy.modules.get\" ], "
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
        + "    \"permissionsRequired\" : [ \"okapi.proxy.tenants.list\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"GET\" ],"
        + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}\","
        + "    \"permissionsRequired\" : [ \"okapi.proxy.tenants.get\" ], "
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
        + "    \"permissionsRequired\" : [ \"okapi.proxy.tenants.modules.get\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"DELETE\" ],"
        + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}/modules\","
        + "    \"permissionsRequired\" : [ \"okapi.proxy.tenants.modules.delete\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"POST\" ],"
        + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}/modules/{moduleId}\","
        + "    \"permissionsRequired\" : [ \"okapi.proxy.tenants.modules.enabled.post\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"GET\" ],"
        + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}/modules/{moduleId}\","
        + "    \"permissionsRequired\" : [ \"okapi.proxy.tenants.modules.enabled.get\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"DELETE\" ],"
        + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}/modules/{moduleId}\","
        + "    \"permissionsRequired\" : [ \"okapi.proxy.tenants.modules.enabled.delete\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"GET\" ],"
        + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}/interfaces\","
        + "    \"permissionsRequired\" : [  \"okapi.proxy.tenants.interfaces.list\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"GET\" ],"
        + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}/interfaces/{interfaceId}\","
        + "    \"permissionsRequired\" : [  \"okapi.proxy.tenants.interfaces.get\" ], "
        + "    \"type\" : \"internal\" "
        + "   },"
        + "   {" // proxy, health
        + "    \"methods\" :  [ \"GET\" ],"
        + "    \"pathPattern\" : \"/_/proxy/health\","
        + "    \"permissionsRequired\" : [  \"okapi.proxy.health.get\" ], "
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
        + "   {"
        + "    \"methods\" :  [ \"GET\" ],"
        + "    \"pathPattern\" : \"/_/version\","
        + "    \"permissionsRequired\" : [ \"okapi.version.get\" ], "
        + "    \"type\" : \"internal\" "
        + "   } ]"
        + " } ],"
        + "\"permissionSets\" : [ "
        // Permission bit names
        + " { "
        + "   \"permissionName\" : \"okapi.deployment.get\", "
        + "   \"displayName\" : \"Okapi - get deployment info\", "
        + "   \"description\" : \"Get deployment info for module on 'this' node\" "
        + " }, { "
        + "   \"permissionName\" : \"okapi.deployment.post\", "
        + "   \"displayName\" : \"Okapi - deploy locally\", "
        + "   \"description\" : \"Deploy a module on 'this' node\" "
        + " }, { "
        + "   \"permissionName\" : \"okapi.deployment.delete\", "
        + "   \"displayName\" : \"Okapi - undeploy locally\", "
        + "   \"description\" : \"Undeploy a module on 'this' node\" "
        + " }, { "
        + "   \"permissionName\" : \"okapi.discovery.get\", "
        + "   \"displayName\" : \"Okapi - get discovery info\", "
        + "   \"description\" : \"Get discovery info for module\" "
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
        + "   \"permissionName\" : \"okapi.discovery.health.get\", "
        + "   \"displayName\" : \"Okapi - Get a health for module/node\", "
        + "   \"description\" : \"Get health info\" "
        + " }, { "
        + "   \"permissionName\" : \"okapi.discovery.nodes.get\", "
        + "   \"displayName\" : \"Okapi - Get a node descriptor\", "
        + "   \"description\" : \"Get a node descriptor\" "
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
        + "   \"displayName\" : \"Okapi - Enable modules and dependencies\", "
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
        + "   \"permissionName\" : \"okapi.proxy.tenants.modules.enabled.delete\", "
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
        + " { "
        + "   \"permissionName\" : \"okapi.deploy\", "
        + "   \"displayName\" : \"Okapi - Manage deployments\", "
        + "   \"description\" : \"Deploy and undeploy modules\", "
        + "   \"subPermissions\" : [ "
        + "     \"okapi.deployment.post\", "
        + "     \"okapi.deployment.get\", \"okapi.deployment.delete\", "
        + "     \"okapi.discovery.post\", "
        + "     \"okapi.discovery.get\", \"okapi.discovery.put\", "
        + "     \"okapi.discovery.delete\", \"okapi.discovery.nodes.put\", "
        + "     \"okapi.discovery.health.get\", \"okapi.discovery.nodes.get\" "
        + "   ]"
        + " }, "
        + " { "
        + "   \"permissionName\" : \"okapi.modules\", "
        + "   \"displayName\" : \"Okapi - Manage modules\", "
        + "   \"description\" : \"Manage ModuleDescriptors known to the system\", "
        + "   \"subPermissions\" : [ "
        + "     \"okapi.proxy.modules.post\", \"okapi.proxy.modules.put\", "
        + "     \"okapi.proxy.modules.delete\", \"okapi.proxy.pull.modules.post\""
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
        + "     \"okapi.proxy.tenants.upgrade.post\", "
        + "     \"okapi.proxy.tenants.install.post\" "
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
    return Json.decodeValue(doc, ModuleDescriptor.class);
  }

  /*
   * Helper to make a Location header. Takes the request path, and appends /id
   * to it, and puts it in the Location header in pc.response. Also sets the
   * return code to 201-Created. You can overwrite it after, if needed.
   */
  private void location(ProxyContext pc, String[] ids, String baseUri,
                        String s, Handler<ExtendedAsyncResult<String>> fut) {

    String uri;
    if (baseUri == null) {
      uri = pc.getCtx().request().uri();
    } else {
      uri = baseUri;
    }
    int idx = uri.indexOf('?');
    if (idx != -1) {
      uri = uri.substring(0, idx);
    }
    StringBuilder uriEncoded = new StringBuilder(uri);
    for (String id : ids) {
      try {
        uriEncoded.append("/" + URLEncoder.encode(id, "UTF-8"));
      } catch (UnsupportedEncodingException ex) {
        fut.handle(new Failure<>(ErrorType.INTERNAL,
            messages.getMessage("11600", id, ex.getMessage())));
      }
    }
    pc.getCtx().response().putHeader("Location", uriEncoded.toString());
    pc.getCtx().response().setStatusCode(201);
    fut.handle(new Success<>(s));
  }

  private void location(ProxyContext pc, String id, String baseUri,
                        String s, Handler<ExtendedAsyncResult<String>> fut) {
    String [] ids = new String[1];
    ids[0] = id;
    location(pc, ids, baseUri, s, fut);
  }

  private void createTenant(ProxyContext pc, String body,
                            Handler<ExtendedAsyncResult<String>> fut) {
    try {
      final TenantDescriptor td = Json.decodeValue(body, TenantDescriptor.class);
      if (td.getId() == null || td.getId().isEmpty()) {
        td.setId(UUID.randomUUID().toString());
      }
      final String id = td.getId();
      if (!id.matches("^[a-z0-9_-]+$")) {
        fut.handle(new Failure<>(ErrorType.USER, messages.getMessage("11601", id)));
        return;
      }
      Tenant t = new Tenant(td);
      tenantManager.insert(t, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
          return;
        }
        location(pc, id, null, Json.encodePrettily(t.getDescriptor()), fut);
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(ErrorType.USER, ex));
    }
  }

  private void updateTenant(String id, String body,
                            Handler<ExtendedAsyncResult<String>> fut) {
    try {
      final TenantDescriptor td = Json.decodeValue(body, TenantDescriptor.class);
      if (!id.equals(td.getId())) {
        fut.handle(new Failure<>(ErrorType.USER, messages.getMessage("11602", td.getId(), id)));
        return;
      }
      Tenant t = new Tenant(td);
      tenantManager.updateDescriptor(td, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
          return;
        }
        final String s = Json.encodePrettily(t.getDescriptor());
        fut.handle(new Success<>(s));
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(ErrorType.USER, ex));
    }
  }

  private void listTenants(Handler<ExtendedAsyncResult<String>> fut) {
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

  private void getTenant(String id, Handler<ExtendedAsyncResult<String>> fut) {
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

  private void deleteTenant(String id, Handler<ExtendedAsyncResult<String>> fut) {
    if (XOkapiHeaders.SUPERTENANT_ID.equals(id)) {
      fut.handle(new Failure<>(ErrorType.USER, messages.getMessage("11603", id)));
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
      TenantInstallOptions options = ModuleUtil.createTenantOptions(pc.getCtx().request());

      final TenantModuleDescriptor td = Json.decodeValue(body,
          TenantModuleDescriptor.class);
      tenantManager.enableAndDisableModule(id, options, null, td, pc, eres -> {
        if (eres.failed()) {
          fut.handle(new Failure<>(eres.getType(), eres.cause()));
          return;
        }
        td.setId(eres.result());
        location(pc, td.getId(), null, Json.encodePrettily(td), fut);
      });

    } catch (DecodeException ex) {
      fut.handle(new Failure<>(ErrorType.USER, ex));
    }
  }

  private void disableModuleForTenant(ProxyContext pc, String id, String module,
                                      Handler<ExtendedAsyncResult<String>> fut) {
    pc.debug("disablemodule t=" + id + " m=" + module);
    TenantInstallOptions options = ModuleUtil.createTenantOptions(pc.getCtx().request());
    tenantManager.enableAndDisableModule(id, options, module, null, pc, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      fut.handle(new Success<>(""));
    });
  }

  private void installModulesForTenant(ProxyContext pc, String id,
                                       String body, Handler<ExtendedAsyncResult<String>> fut) {

    try {
      TenantInstallOptions options = ModuleUtil.createTenantOptions(pc.getCtx().request());

      final TenantModuleDescriptor[] tml = Json.decodeValue(body,
          TenantModuleDescriptor[].class);
      List<TenantModuleDescriptor> tm = new LinkedList<>();
      Collections.addAll(tm, tml);
      tenantManager.installUpgradeModules(id, pc, options, tm, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
        } else {
          logger.info("installUpgradeModules returns: {}", Json.encodePrettily(res.result()));
          fut.handle(new Success<>(Json.encodePrettily(res.result())));
        }
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(ErrorType.USER, ex));
    }
  }

  private void upgradeModulesForTenant(ProxyContext pc, String id,
                                       Handler<ExtendedAsyncResult<String>> fut) {

    TenantInstallOptions options = ModuleUtil.createTenantOptions(pc.getCtx().request());
    tenantManager.installUpgradeModules(id, pc, options, null, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        logger.info("installUpgradeModules returns: {}", Json.encodePrettily(res.result()));
        fut.handle(new Success<>(Json.encodePrettily(res.result())));
      }
    });
  }

  private void upgradeModuleForTenant(ProxyContext pc, String id, String mod,
                                      String body, Handler<ExtendedAsyncResult<String>> fut) {
    TenantInstallOptions options = ModuleUtil.createTenantOptions(pc.getCtx().request());
    try {
      final String module_from = mod;
      final TenantModuleDescriptor td = Json.decodeValue(body,
          TenantModuleDescriptor.class);
      tenantManager.enableAndDisableModule(id, options, module_from, td, pc, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
          return;
        }
        td.setId(res.result());
        final String uri = pc.getCtx().request().uri();
        final String regex = "^(.*)/" + module_from + "$";
        final String newuri = uri.replaceAll(regex, "$1");
        location(pc, td.getId(), newuri, Json.encodePrettily(td), fut);
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(ErrorType.USER, ex));
    }
  }

  private void listModulesForTenant(ProxyContext pc, String id,
                                    Handler<ExtendedAsyncResult<String>> fut) {

    try {
      tenantManager.listModules(id, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(res.getType(), res.cause()));
          return;
        }
        List<ModuleDescriptor> mdl = res.result();
        final boolean dot = ModuleUtil.getParamBoolean(pc.getCtx().request(), "dot", false);
        mdl = ModuleUtil.filter(pc.getCtx().request(), mdl, dot, false);
        if (dot) {
          String s = GraphDot.report(mdl);
          pc.getCtx().response().putHeader("Content-Type", "text/plain");
          fut.handle(new Success<>(s));
        } else {
          String s = Json.encodePrettily(mdl);
          fut.handle(new Success<>(s));
        }
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(ErrorType.USER, ex));
    }
  }

  private void disableModulesForTenant(ProxyContext pc, String id,
                                       Handler<ExtendedAsyncResult<String>> fut) {

    TenantInstallOptions options = ModuleUtil.createTenantOptions(pc.getCtx().request());
    tenantManager.disableModules(id, options, pc).onComplete(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(ErrorType.USER, res.cause()));
        return;
      }
      fut.handle(new Success<>(""));
    });
  }

  private void getModuleForTenant(String id, String mod,
                                  Handler<ExtendedAsyncResult<String>> fut) {

    tenantManager.get(id, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      Tenant t = res.result();
      Set<String> ml = t.listModules();  // Convert the list of module names
      if (!ml.contains(mod)) {
        fut.handle(new Failure<>(ErrorType.NOT_FOUND, mod));
        return;
      }
      TenantModuleDescriptor tmd = new TenantModuleDescriptor();
      tmd.setId(mod);
      String s = Json.encodePrettily(tmd);
      fut.handle(new Success<>(s));
    });
  }

  private void listInterfaces(ProxyContext pc, String id,
                              Handler<ExtendedAsyncResult<String>> fut) {

    final boolean full = ModuleUtil.getParamBoolean(pc.getCtx().request(), "full", false);
    final String type = pc.getCtx().request().getParam("type");
    tenantManager.listInterfaces(id, full, type, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        String s = Json.encodePrettily(res.result());
        fut.handle(new Success<>(s));
      }
    });
  }

  private void listModulesFromInterface(ProxyContext pc, String id, String intId,
                                        Handler<ExtendedAsyncResult<String>> fut) {

    final String type = pc.getCtx().request().getParam("type");
    tenantManager.listModulesFromInterface(id, intId, type, res -> {
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
      HttpServerRequest req = pc.getCtx().request();
      final boolean check = ModuleUtil.getParamBoolean(req, "check", true);
      final boolean preRelease = ModuleUtil.getParamBoolean(req, "preRelease", true);
      final boolean npmSnapshot = ModuleUtil.getParamBoolean(req, "npmSnapshot", true);

      String validerr = md.validate(pc);
      if (!validerr.isEmpty()) {
        logger.info("createModule validate failed: {}", validerr);
        fut.handle(new Failure<>(ErrorType.USER, validerr));
        return;
      }
      moduleManager.create(md, check, preRelease, npmSnapshot, cres -> {
        if (cres.failed()) {
          fut.handle(new Failure<>(cres.getType(), cres.cause()));
          return;
        }
        location(pc, md.getId(), null, Json.encodePrettily(md), fut);
      });
    } catch (DecodeException ex) {
      pc.debug("Failed to decode md: " + pc.getCtx().getBodyAsString());
      fut.handle(new Failure<>(ErrorType.USER, ex));
    }
  }

  private void getModule(String id, Handler<ExtendedAsyncResult<String>> fut) {
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
                           String body,
                           Handler<ExtendedAsyncResult<String>> fut) {

    String [] skipModules = new String [0];
    if (!body.isEmpty()) {
      skipModules = Json.decodeValue(body, skipModules.getClass());
    }
    moduleManager.getModulesWithFilter(true, true, Arrays.asList(skipModules), res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      try {
        List<ModuleDescriptor> mdl = res.result();
        final boolean dot = ModuleUtil.getParamBoolean(pc.getCtx().request(), "dot", false);
        mdl = ModuleUtil.filter(pc.getCtx().request(), mdl, dot, true);
        if (dot) {
          String s = GraphDot.report(mdl);
          pc.getCtx().response().putHeader("Content-Type", "text/plain");
          fut.handle(new Success<>(s));
        } else {
          String s = Json.encodePrettily(mdl);
          fut.handle(new Success<>(s));
        }
      } catch (DecodeException ex) {
        fut.handle(new Failure<>(ErrorType.USER, ex));
      }
    });
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

  private void getDeployment(String id,
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

  private void listDeployments(Handler<ExtendedAsyncResult<String>> fut) {
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
        location(pc, res.result().getInstId(), null, s, fut);
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(ErrorType.USER, ex));
    }
  }

  private void deleteDeployment(String id,
                                Handler<ExtendedAsyncResult<String>> fut) {

    deploymentManager.undeploy(id, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      fut.handle(new Success<>(""));
    });
  }

  private void getDiscoveryNode(String id,
                                Handler<ExtendedAsyncResult<String>> fut) {
    logger.debug("Int: getDiscoveryNode: {}", id);
    discoveryManager.getNode(id, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void putDiscoveryNode(String id, String body,
                                Handler<ExtendedAsyncResult<String>> fut) {
    logger.debug("Int: putDiscoveryNode: {} {}", id, body);
    final NodeDescriptor nd = Json.decodeValue(body, NodeDescriptor.class);
    discoveryManager.updateNode(id, nd, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void listDiscoveryNodes(Handler<ExtendedAsyncResult<String>> fut) {
    discoveryManager.getNodes(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void listDiscoveryModules(Handler<ExtendedAsyncResult<String>> fut) {
    discoveryManager.get(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void discoveryGetSrvcId(String srvcId,
                                  Handler<ExtendedAsyncResult<String>> fut) {

    discoveryManager.getNonEmpty(srvcId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void discoveryGetInstId(String srvcId, String instId,
                                  Handler<ExtendedAsyncResult<String>> fut) {

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
        final String baseuri = pc.getCtx().request().uri();
        String[] ids = new String [2];
        ids[0] = md.getSrvcId();
        ids[1] = md.getInstId();
        location(pc, ids, baseuri, s, fut);
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(ErrorType.USER, ex));
    }
  }

  private void discoveryUndeploy(String srvcId, String instId,
                                 Handler<ExtendedAsyncResult<String>> fut) {

    discoveryManager.removeAndUndeploy(srvcId, instId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      fut.handle(new Success<>(""));
    });
  }

  private void discoveryUndeploy(String srvcId,
                                 Handler<ExtendedAsyncResult<String>> fut) {

    discoveryManager.removeAndUndeploy(srvcId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      fut.handle(new Success<>(""));
    });
  }

  private void discoveryUndeploy(Handler<ExtendedAsyncResult<String>> fut) {

    discoveryManager.removeAndUndeploy(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      fut.handle(new Success<>(""));
    });
  }


  private void discoveryHealthAll(Handler<ExtendedAsyncResult<String>> fut) {
    discoveryManager.health(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void discoveryHealthSrvcId(String srvcId,
                                     Handler<ExtendedAsyncResult<String>> fut) {

    discoveryManager.health(srvcId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void discoveryHealthOne(String srvcId, String instId,
                                  Handler<ExtendedAsyncResult<String>> fut) {

    discoveryManager.health(srvcId, instId, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void listEnv(Handler<ExtendedAsyncResult<String>> fut) {
    envManager.get(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      final String s = Json.encodePrettily(res.result());
      fut.handle(new Success<>(s));
    });
  }

  private void getEnv(String id,
                      Handler<ExtendedAsyncResult<String>> fut) {

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
        location(pc, pmd.getName(), null, js, fut);
      });
    } catch (DecodeException ex) {
      fut.handle(new Failure<>(ErrorType.USER, ex));
    }
  }

  private void deleteEnv(String id, Handler<ExtendedAsyncResult<String>> fut) {

    envManager.remove(id, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
        return;
      }
      fut.handle(new Success<>(""));
    });
  }

  private void pullModules(String body,
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
      fut.handle(new Failure<>(ErrorType.USER, ex));
    }
  }

  /**
   * Pretty simplistic health check.
   */
  private void getHealth(Handler<ExtendedAsyncResult<String>> fut) {
    fut.handle(new Success<>("[ ]"));
  }

  private void getVersion(ProxyContext pc,
                          Handler<ExtendedAsyncResult<String>> fut) {
    String v = okapiVersion;
    if (v == null) {
      v = "0.0.0";
    }
    pc.getCtx().response().putHeader("Content-Type", "text/plain"); // !!
    fut.handle(new Success<>(v));
  }

  /**
   * Dispatcher for all the built-in services.
   * Note that there are restrictions what we can do with the ctx. We can set a
   * result code (defaults to 200 OK) in successful operations, but be aware
   * that only if this is the last module in the pipeline, will this code be
   * returned to the caller. Often that is the case. We can look at the request,
   * at least the (normalized) path and method, but the previous filters may
   * have done something to them already.
   *
   * @param req The request body
   * @param pc Proxy context, gives a ctx, path, and method
   * @param fut Callback with the response body
   */
  // Cognitive Complexity of methods should not be too high
  // but this function is really just a big switch
  @java.lang.SuppressWarnings({"squid:S3776"})
  public void internalService(String req, ProxyContext pc,
                              Handler<ExtendedAsyncResult<String>> fut) {

    RoutingContext ctx = pc.getCtx();
    String p = ctx.normalisedPath();
    String[] segments = p.split("/");
    int n = segments.length;
    String[] decodedSegs = new String[n];
    logger.debug("segment path={}", p);
    for (int i = 0; i < n; i++) {
      decodedSegs[i] = UrlDecoder.decode(segments[i], false);
      logger.debug("segment {} {}->{}", i, segments[i], decodedSegs[i]);
    }
    HttpMethod m = ctx.request().method();
    // default to json replies, error code overrides to text/plain
    pc.getCtx().response().putHeader("Content-Type", "application/json");
    if (n >= 4 && p.startsWith("/_/proxy/")) { // need at least /_/proxy/something
      if (segments[3].equals("modules")
          && moduleManager != null) {
        // /_/proxy/modules
        if (n == 4 && m.equals(HttpMethod.GET)) {
          listModules(pc, req, fut);
          return;
        }
        if (n == 4 && m.equals(HttpMethod.POST)) {
          createModule(pc, req, fut);
          return;
        }
        // /_/proxy/modules/:id
        if (n == 5 && m.equals(HttpMethod.GET)) {
          getModule(decodedSegs[4], fut);
          return;
        }
        if (n == 5 && m.equals(HttpMethod.DELETE)) {
          deleteModule(pc, decodedSegs[4], fut);
          return;
        }
      } // /_/proxy/modules

      if (segments[3].equals("tenants")
          && tenantManager != null) {
        // /_/proxy/tenants
        if (n == 4 && m.equals(HttpMethod.GET)) {
          listTenants(fut);
          return;
        }
        if (n == 4 && m.equals(HttpMethod.POST)) {
          createTenant(pc, req, fut);
          return;
        }
        // /_/proxy/tenants/:id
        if (n == 5 && m.equals(HttpMethod.GET)) {
          getTenant(decodedSegs[4], fut);
          return;
        }
        if (n == 5 && m.equals(HttpMethod.PUT)) {
          updateTenant(decodedSegs[4], req, fut);
          return;
        }
        if (n == 5 && m.equals(HttpMethod.DELETE)) {
          deleteTenant(decodedSegs[4], fut);
          return;
        }
        // /_/proxy/tenants/:id/modules
        if (n == 6 && m.equals(HttpMethod.GET) && segments[5].equals("modules")) {
          listModulesForTenant(pc, decodedSegs[4], fut);
          return;
        }
        if (n == 6 && m.equals(HttpMethod.POST) && segments[5].equals("modules")) {
          enableModuleForTenant(pc, decodedSegs[4], req, fut);
          return;
        }
        if (n == 6 && m.equals(HttpMethod.DELETE) && segments[5].equals("modules")) {
          disableModulesForTenant(pc, decodedSegs[4], fut);
          return;
        }
        // /_/proxy/tenants/:id/modules/:mod
        if (n == 7 && m.equals(HttpMethod.GET) && segments[5].equals("modules")) {
          getModuleForTenant(decodedSegs[4], decodedSegs[6], fut);
          return;
        }
        if (n == 7 && m.equals(HttpMethod.POST) && segments[5].equals("modules")) {
          upgradeModuleForTenant(pc, decodedSegs[4], decodedSegs[6], req, fut);
          return;
        }
        if (n == 7 && m.equals(HttpMethod.DELETE) && segments[5].equals("modules")) {
          disableModuleForTenant(pc, decodedSegs[4], decodedSegs[6], fut);
          return;
        }
        // /_/proxy/tenants/:id/install
        if (n == 6 && m.equals(HttpMethod.POST) && segments[5].equals("install")) {
          installModulesForTenant(pc, decodedSegs[4], req, fut);
          return;
        }
        // /_/proxy/tenants/:id/upgrade
        if (n == 6 && m.equals(HttpMethod.POST) && segments[5].equals("upgrade")) {
          upgradeModulesForTenant(pc, decodedSegs[4], fut);
          return;
        }
        // /_/proxy/tenants/:id/interfaces
        if (n == 6 && m.equals(HttpMethod.GET) && segments[5].equals("interfaces")) {
          listInterfaces(pc, decodedSegs[4], fut);
          return;
        }

        // /_/proxy/tenants/:id/interfaces/:int
        if (n == 7 && m.equals(HttpMethod.GET) && segments[5].equals("interfaces")) {
          listModulesFromInterface(pc, decodedSegs[4], decodedSegs[6], fut);
          return;
        }
      } // /_/proxy/tenants

      // /_/proxy/pull/modules
      if (n == 5 && segments[3].equals("pull") && segments[4].equals("modules")
          && m.equals(HttpMethod.POST) && pullManager != null) {
        pullModules(req, fut);
        return;
      }
      // /_/proxy/health
      if (n == 4 && segments[3].equals("health") && m.equals(HttpMethod.GET)) {
        getHealth(fut);
        return;
      }

    } // _/proxy

    // deployment
    if (n >= 4 && p.startsWith("/_/deployment/")
        && segments[3].equals("modules")
        && deploymentManager != null) {
      // /_/deployment/modules
      if (n == 4 && m.equals(HttpMethod.GET)) {
        listDeployments(fut);
        return;
      }
      if (n == 4 && m.equals(HttpMethod.POST)) {
        createDeployment(pc, req, fut);
        return;
      }
      // /_/deployment/modules/:id:
      if (n == 5 && m.equals(HttpMethod.GET)) {
        getDeployment(decodedSegs[4], fut);
        return;
      }
      if (n == 5 && m.equals(HttpMethod.DELETE)) {
        deleteDeployment(decodedSegs[4], fut);
        return;
      }
    } // deployment

    if (n >= 4 && p.startsWith("/_/discovery/")
        && discoveryManager != null) {
      // /_/discovery/nodes
      if (n == 4 && segments[3].equals("nodes") && m.equals(HttpMethod.GET)) {
        listDiscoveryNodes(fut);
        return;
      }
      // /_/discovery/nodes/:nodeid
      if (n == 5 && segments[3].equals("nodes") && m.equals(HttpMethod.GET)) {
        getDiscoveryNode(decodedSegs[4], fut);
        return;
      }
      // /_/discovery/nodes/:nodeid
      if (n == 5 && segments[3].equals("nodes") && m.equals(HttpMethod.PUT)) {
        putDiscoveryNode(decodedSegs[4], req, fut);
        return;
      }

      // /_/discovery/modules
      if (n == 4 && segments[3].equals("modules") && m.equals(HttpMethod.GET)) {
        listDiscoveryModules(fut);
        return;
      }
      if (n == 4 && segments[3].equals("modules") && m.equals(HttpMethod.POST)) {
        discoveryDeploy(pc, req, fut);
        return;
      }
      // /_/discovery/modules/:srvcid
      if (n == 5 && segments[3].equals("modules") && m.equals(HttpMethod.GET)) {
        discoveryGetSrvcId(decodedSegs[4], fut);
        return;
      }
      // /_/discovery/modules/:srvcid/:instid"
      if (n == 6 && segments[3].equals("modules") && m.equals(HttpMethod.GET)) {
        discoveryGetInstId(decodedSegs[4], decodedSegs[5], fut);
        return;
      }
      if (n == 6 && segments[3].equals("modules") && m.equals(HttpMethod.DELETE)) {
        discoveryUndeploy(decodedSegs[4], decodedSegs[5], fut);
        return;
      }
      if (n == 5 && segments[3].equals("modules") && m.equals(HttpMethod.DELETE)) {
        discoveryUndeploy(decodedSegs[4], fut);
        return;
      }
      if (n == 4 && segments[3].equals("modules") && m.equals(HttpMethod.DELETE)) {
        discoveryUndeploy(fut);
        return;
      }
      // /_/discovery/health
      if (n == 4 && segments[3].equals("health") && m.equals(HttpMethod.GET)) {
        discoveryHealthAll(fut);
        return;
      }
      // /_/discovery/health/:srvcId
      if (n == 5 && segments[3].equals("health") && m.equals(HttpMethod.GET)) {
        discoveryHealthSrvcId(decodedSegs[4], fut);
        return;
      }
      // /_/discovery/health/:srvcId/:instid
      if (n == 6 && segments[3].equals("health") && m.equals(HttpMethod.GET)) {
        discoveryHealthOne(decodedSegs[4], decodedSegs[5], fut);
        return;
      }
    } // discovery

    if (n >= 2 && p.startsWith("/_/env")
        && segments[2].equals("env")) { // not envXX or such

      // /_/env
      if (n == 3 && m.equals(HttpMethod.GET)) {
        listEnv(fut);
        return;
      }
      if (n == 3 && m.equals(HttpMethod.POST)) {
        createEnv(pc, req, fut);
        return;
      }
      // /_/env/name
      if (n == 4 && m.equals(HttpMethod.GET)) {
        getEnv(decodedSegs[3], fut);
        return;
      }
      if (n == 4 && m.equals(HttpMethod.DELETE)) {
        deleteEnv(decodedSegs[3], fut);
        return;
      }

    } // env

    if (p.equals("/_/version") && m.equals(HttpMethod.GET)) {
      getVersion(pc, fut);
      return;
    }
    fut.handle(new Failure<>(ErrorType.INTERNAL, messages.getMessage("11607", p)));
  }

}
