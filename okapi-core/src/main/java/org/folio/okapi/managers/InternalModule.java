package org.folio.okapi.managers;

import io.vertx.core.Future;
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
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.UrlDecoder;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.util.GraphDot;
import org.folio.okapi.util.ModuleUtil;
import org.folio.okapi.util.OkapiError;
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
        + "    \"methods\" :  [ \"GET\" ],"
        + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}/install\","
        + "    \"permissionsRequired\" : [ \"okapi.proxy.tenants.install.list\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"POST\" ],"
        + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}/install\","
        + "    \"permissionsRequired\" : [ \"okapi.proxy.tenants.install.post\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"GET\" ],"
        + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}/install/{installId}\","
        + "    \"permissionsRequired\" : [ \"okapi.proxy.tenants.install.get\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"POST\" ],"
        + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}/modules\","
        + "    \"permissionsRequired\" : [ \"okapi.proxy.tenants.modules.post\" ], "
        + "    \"type\" : \"internal\" "
        + "   }, {"
        + "    \"methods\" :  [ \"GET\" ],"
        + "    \"pathPattern\" : \"/_/proxy/tenants/{tenantId}/modules\","
        + "    \"permissionsRequired\" : [ \"okapi.proxy.tenants.modules.list\" ], "
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
        + "   \"permissionName\" : \"okapi.proxy.modules.list\", "
        + "   \"displayName\" : \"Okapi - list modules\", "
        + "   \"description\" : \"List modules\" "
        + " }, { "
        + "   \"permissionName\" : \"okapi.proxy.modules.get\", "
        + "   \"displayName\" : \"Okapi - get a module\", "
        + "   \"description\" : \"Get a module\" "
        + " }, { "
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
        + "   \"permissionName\" : \"okapi.proxy.tenants.list\", "
        + "   \"displayName\" : \"Okapi - list tenants\", "
        + "   \"description\" : \"List tenants\" "
        + " }, { "
        + "   \"permissionName\" : \"okapi.proxy.tenants.get\", "
        + "   \"displayName\" : \"Okapi - get a tenant\", "
        + "   \"description\" : \"Get a tenant\" "
        + " }, { "
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
        + "   \"permissionName\" : \"okapi.proxy.tenants.install.list\", "
        + "   \"displayName\" : \"Okapi - list all install jobs\", "
        + "   \"description\" : \"Retrieve all install jobs\" "
        + " }, { "
        + "   \"permissionName\" : \"okapi.proxy.tenants.upgrade.post\", "
        + "   \"displayName\" : \"Okapi - Upgrade modules\", "
        + "   \"description\" : \"Check if newer versions available, and upgrade\" "
        + " }, { "
        + "   \"permissionName\" : \"okapi.proxy.tenants.install.get\", "
        + "   \"displayName\" : \"Okapi - get install job\", "
        + "   \"description\" : \"Retrieve install job by id\" "
        + " }, { "
        + "   \"permissionName\" : \"okapi.proxy.tenants.install.post\", "
        + "   \"displayName\" : \"Okapi - Enable modules and dependencies\", "
        + "   \"description\" : \"Check dependencies and enable/disable modules as needed\" "
        + " }, { "
        + "   \"permissionName\" : \"okapi.proxy.tenants.modules.list\", "
        + "   \"displayName\" : \"Okapi - List modules enabled for tenant\", "
        + "   \"description\" : \"List modules enabled for tenant\" "
        + " }, { "
        + "   \"permissionName\" : \"okapi.proxy.tenants.modules.enabled.get\", "
        + "   \"displayName\" : \"Okapi - Get module enabled for tenant\", "
        + "   \"description\" : \"Get module enabled for tenant\" "
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
        + "   \"permissionName\" : \"okapi.proxy.tenants.interfaces.list\", "
        + "   \"displayName\" : \"Okapi - list interfacse for tenant\", "
        + "   \"description\" : \"List available interfaces for tenant\" "
        + " }, { "
        + "   \"permissionName\" : \"okapi.proxy.tenants.interfaces.get\", "
        + "   \"displayName\" : \"Okapi - get modules that provides interface\", "
        + "   \"description\" : \"get modules that provide some interface\" "
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
        + " { "
        + "   \"permissionName\" : \"okapi.version.get\", "
        + "   \"displayName\" : \"Okapi - Get version\", "
        + "   \"description\" : \"Get version\" "
        + " }, "
        + " { "
        + "   \"permissionName\" : \"okapi.proxy.health.get\", "
        + "   \"displayName\" : \"Okapi - health\", "
        + "   \"description\" : \"Get health info\" "
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
        + "     \"okapi.proxy.modules.list\", \"okapi.proxy.modules.get\", "
        + "     \"okapi.proxy.modules.post\", \"okapi.proxy.modules.put\", "
        + "     \"okapi.proxy.modules.delete\", \"okapi.proxy.pull.modules.post\""
        + "   ]"
        + " }, "
        + " { "
        + "   \"permissionName\" : \"okapi.tenants\", "
        + "   \"displayName\" : \"Okapi - Manage tenants\", "
        + "   \"description\" : \"Manage tenants known to the system\", "
        + "   \"subPermissions\" : [ "
        + "     \"okapi.proxy.tenants.list\", \"okapi.proxy.tenants.get\", "
        + "     \"okapi.proxy.tenants.post\", \"okapi.proxy.tenants.put\", "
        + "     \"okapi.proxy.tenants.delete\""
        + "   ]"
        + " }, "
        + " { "
        + "   \"permissionName\" : \"okapi.tenantmodules\", "
        + "   \"displayName\" : \"Okapi - Manage modules enabled for a tenant\", "
        + "   \"description\" : \"Enable and disable modules for a tenant\", "
        + "   \"subPermissions\" : [ "
        + "     \"okapi.proxy.tenants.modules.list\", "
        + "     \"okapi.proxy.tenants.modules.post\", "
        + "     \"okapi.proxy.tenants.modules.enabled.get\", "
        + "     \"okapi.proxy.tenants.modules.enabled.post\", "
        + "     \"okapi.proxy.tenants.modules.enabled.delete\", "
        + "     \"okapi.proxy.tenants.upgrade.post\", "
        + "     \"okapi.proxy.tenants.install.list\", "
        + "     \"okapi.proxy.tenants.install.get\", "
        + "     \"okapi.proxy.tenants.install.post\" "
        + "   ]"
        + " }, "
        + " { "
        + "   \"permissionName\" : \"okapi.interfaces\", "
        + "   \"displayName\" : \"Okapi - Module interfaces\", "
        + "   \"description\" : \"Discover modules that provide some interface\", "
        + "   \"subPermissions\" : [ "
        + "     \"okapi.proxy.tenants.interfaces.list\", "
        + "     \"okapi.proxy.tenants.interfaces.get\" "
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
        + " { " // permissions added for Okapi 4
        + "   \"permissionName\" : \"okapi.readonly\", "
        + "   \"displayName\" : \"Okapi - Read only permissions\", "
        + "   \"description\" : \"Permissions with no side effects\", "
        + "   \"subPermissions\" : [ "
        + "     \"okapi.proxy.modules.list\", "
        + "     \"okapi.proxy.modules.get\", "
        + "     \"okapi.proxy.tenants.list\", "
        + "     \"okapi.proxy.tenants.get\", "
        + "     \"okapi.proxy.tenants.modules.list\", "
        + "     \"okapi.proxy.tenants.modules.enabled.get\", "
        + "     \"okapi.interfaces\", "
        + "     \"okapi.proxy.health.get\", "
        + "     \"okapi.version.get\" "
        + "   ]"
        + " }, "
        + " { "
        + "   \"permissionName\" : \"okapi.all\", "
        + "   \"displayName\" : \"Okapi - All permissions\", "
        + "   \"description\" : \"Anything goes\", "
        + "   \"subPermissions\" : [ "
        + "     \"okapi.deploy\",  \"okapi.modules\", "
        + "     \"okapi.tenants\", \"okapi.tenantmodules\", "
        + "     \"okapi.interfaces\", "
        + "     \"okapi.proxy.health.get\", "
        + "     \"okapi.version.get\", "
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
  private Future<String> location(ProxyContext pc, String[] ids, String baseUri, String s) {

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
        return Future.failedFuture(messages.getMessage("11600", id, ex.getMessage()));
      }
    }
    pc.getCtx().response().putHeader("Location", uriEncoded.toString());
    pc.getCtx().response().setStatusCode(201);
    return Future.succeededFuture(s);
  }

  private Future<String> location(ProxyContext pc, String id, String baseUri, String s) {
    String [] ids = new String[1];
    ids[0] = id;
    return location(pc, ids, baseUri, s);
  }

  private Future<String> createTenant(ProxyContext pc, String body) {
    try {
      final TenantDescriptor td = Json.decodeValue(body, TenantDescriptor.class);
      if (td.getId() == null || td.getId().isEmpty()) {
        td.setId(UUID.randomUUID().toString());
      }
      final String tenantId = td.getId();
      if (!tenantId.matches("^[a-z0-9_-]+$")) {
        return Future.failedFuture(
            new OkapiError(ErrorType.USER, messages.getMessage("11601", tenantId)));
      }
      Tenant t = new Tenant(td);
      return tenantManager.insert(t).compose(res ->
        location(pc, tenantId, null, Json.encodePrettily(t.getDescriptor())));
    } catch (DecodeException ex) {
      return Future.failedFuture(new OkapiError(ErrorType.USER, ex.getMessage()));
    }
  }

  private Future<String> updateTenant(String tenantId, String body) {
    try {
      final TenantDescriptor td = Json.decodeValue(body, TenantDescriptor.class);
      if (!tenantId.equals(td.getId())) {
        return Future.failedFuture(new OkapiError(ErrorType.USER,
            messages.getMessage("11602", td.getId(), tenantId)));
      }
      Tenant t = new Tenant(td);
      return tenantManager.updateDescriptor(td).compose(res ->
          Future.succeededFuture(Json.encodePrettily(t.getDescriptor())));
    } catch (DecodeException ex) {
      return Future.failedFuture(new OkapiError(ErrorType.USER, ex.getMessage()));
    }
  }

  private Future<String> listTenants() {
    return tenantManager.list().compose(res ->
        Future.succeededFuture(Json.encodePrettily(res)));
  }

  private Future<String> getTenant(String tenantId) {
    return tenantManager.get(tenantId).compose(res ->
        Future.succeededFuture(Json.encodePrettily(res.getDescriptor())));
  }

  private Future<String> deleteTenant(String tenantId) {
    if (XOkapiHeaders.SUPERTENANT_ID.equals(tenantId)) {
      return Future.failedFuture(new OkapiError(ErrorType.USER,
          messages.getMessage("11603", tenantId)));
      // Change of behavior, used to return 403
    }
    return tenantManager.delete(tenantId).compose(res -> Future.succeededFuture(""));
  }

  private Future<String> enableModuleForTenant(ProxyContext pc, String tenantId, String body) {
    try {
      TenantInstallOptions options = ModuleUtil.createTenantOptions(pc.getCtx().request());

      final TenantModuleDescriptor td = Json.decodeValue(body,
          TenantModuleDescriptor.class);
      return tenantManager.enableAndDisableModule(tenantId, options, null, td, pc)
          .compose(eres -> {
            td.setId(eres);
            return location(pc, td.getId(), null, Json.encodePrettily(td));
          });
    } catch (DecodeException ex) {
      return Future.failedFuture(new OkapiError(ErrorType.USER, ex.getMessage()));
    }
  }

  private Future<String> disableModuleForTenant(ProxyContext pc, String tenantId, String module) {
    TenantInstallOptions options = ModuleUtil.createTenantOptions(pc.getCtx().request());
    return tenantManager.enableAndDisableModule(tenantId, options, module, null, pc).map("");
  }

  private Future<String> installTenantModulesPost(ProxyContext pc, String tenantId, String body) {

    try {
      TenantInstallOptions options = ModuleUtil.createTenantOptions(pc.getCtx().request());

      final TenantModuleDescriptor[] tml = Json.decodeValue(body,
          TenantModuleDescriptor[].class);
      List<TenantModuleDescriptor> tm = new LinkedList<>();
      Collections.addAll(tm, tml);
      UUID installId = UUID.randomUUID();
      return tenantManager.installUpgradeCreate(tenantId, installId.toString(), pc, options, tm)
          .compose(res -> {
            String jsonResponse = Json.encodePrettily(res);
            logger.info("installTenantModulesPost returns: {}", jsonResponse);
            if (options.getAsync()) {
              return location(pc, installId.toString(), null, jsonResponse);
            } else {
              return Future.succeededFuture(jsonResponse);
            }
          });
    } catch (DecodeException ex) {
      return Future.failedFuture(new OkapiError(ErrorType.USER, ex.getMessage()));
    }
  }

  private Future<String> installTenantModulesGetList(String tenantId) {
    return tenantManager.installUpgradeGetList(tenantId)
        .compose(installJobList -> Future.succeededFuture(Json.encodePrettily(installJobList)));
  }

  private Future<String> installTenantModulesGet(String tenantId, String installId) {
    return tenantManager.installUpgradeGet(tenantId, installId)
        .compose(installJob -> Future.succeededFuture(Json.encodePrettily(installJob)));
  }

  private Future<String> upgradeModulesForTenant(ProxyContext pc, String tenantId) {

    TenantInstallOptions options = ModuleUtil.createTenantOptions(pc.getCtx().request());
    UUID installId = UUID.randomUUID();
    return tenantManager.installUpgradeCreate(tenantId, installId.toString(), pc, options, null)
        .compose(res -> {
          String jsonResponse = Json.encodePrettily(res);
          if (options.getAsync()) {
            // using same location as install
            String baseUri = pc.getCtx().request().uri().replace("/upgrade", "/install");
            return location(pc, installId.toString(), baseUri, jsonResponse);
          } else {
            return Future.succeededFuture(jsonResponse);
          }
        });
  }

  private Future<String> upgradeModuleForTenant(ProxyContext pc, String tenantId,
                                                String mod, String body) {
    TenantInstallOptions options = ModuleUtil.createTenantOptions(pc.getCtx().request());
    try {
      final String module_from = mod;
      final TenantModuleDescriptor td = Json.decodeValue(body,
          TenantModuleDescriptor.class);
      return tenantManager.enableAndDisableModule(tenantId, options, module_from, td, pc)
          .compose(res -> {
            td.setId(res);
            final String uri = pc.getCtx().request().uri();
            final String regex = "^(.*)/" + module_from + "$";
            final String newuri = uri.replaceAll(regex, "$1");
            return location(pc, td.getId(), newuri, Json.encodePrettily(td));
          });
    } catch (DecodeException ex) {
      return Future.failedFuture(new OkapiError(ErrorType.USER, ex.getMessage()));
    }
  }

  private Future<String> listModulesForTenant(ProxyContext pc, String tenantId) {

    try {
      return tenantManager.listModules(tenantId).compose(mdl -> {
        final boolean dot = ModuleUtil.getParamBoolean(pc.getCtx().request(), "dot", false);
        mdl = ModuleUtil.filter(pc.getCtx().request(), mdl, dot, false);
        if (dot) {
          String s = GraphDot.report(mdl);
          pc.getCtx().response().putHeader("Content-Type", "text/plain");
          return Future.succeededFuture(s);
        }
        return Future.succeededFuture(Json.encodePrettily(mdl));
      });
    } catch (DecodeException ex) {
      return Future.failedFuture(new OkapiError(ErrorType.USER, ex.getMessage()));
    }
  }

  private Future<String> disableModulesForTenant(ProxyContext pc, String tenantId) {

    TenantInstallOptions options = ModuleUtil.createTenantOptions(pc.getCtx().request());
    return tenantManager.disableModules(tenantId, options, pc)
        .compose(res -> Future.succeededFuture(""));
  }

  private Future<String> getModuleForTenant(String tenantId, String mod) {

    return tenantManager.get(tenantId).compose(tenant -> {
      Set<String> ml = tenant.listModules();  // Convert the list of module names
      if (!ml.contains(mod)) {
        return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND, mod));
      }
      TenantModuleDescriptor tmd = new TenantModuleDescriptor();
      tmd.setId(mod);
      return Future.succeededFuture(Json.encodePrettily(tmd));
    });
  }

  private Future<String> listInterfaces(ProxyContext pc, String tenantId) {

    final boolean full = ModuleUtil.getParamBoolean(pc.getCtx().request(), "full", false);
    final String type = pc.getCtx().request().getParam("type");
    return tenantManager.listInterfaces(tenantId, full, type)
        .compose(res -> Future.succeededFuture(Json.encodePrettily(res)));
  }

  private Future<String> listModulesFromInterface(ProxyContext pc, String tenantId, String intId) {

    final String type = pc.getCtx().request().getParam("type");
    return tenantManager.listModulesFromInterface(tenantId, intId, type).compose(modules -> {
      ArrayList<TenantModuleDescriptor> ta = new ArrayList<>();
      for (ModuleDescriptor md : modules) {
        TenantModuleDescriptor tmd = new TenantModuleDescriptor();
        tmd.setId(md.getId());
        ta.add(tmd);
      }
      return Future.succeededFuture(Json.encodePrettily(ta));
    });
  }

  private Future<String> createModule(ProxyContext pc, String body) {
    try {
      final ModuleDescriptor md = Json.decodeValue(body, ModuleDescriptor.class);
      HttpServerRequest req = pc.getCtx().request();
      final boolean check = ModuleUtil.getParamBoolean(req, "check", true);
      final boolean preRelease = ModuleUtil.getParamBoolean(req, "preRelease", true);
      final boolean npmSnapshot = ModuleUtil.getParamBoolean(req, "npmSnapshot", true);

      String validerr = md.validate(pc);
      if (!validerr.isEmpty()) {
        logger.info("createModule validate failed: {}", validerr);
        return Future.failedFuture(new OkapiError(ErrorType.USER, validerr));
      }
      return moduleManager.create(md, check, preRelease, npmSnapshot)
          .compose(res -> location(pc, md.getId(), null, Json.encodePrettily(md)));
    } catch (DecodeException ex) {
      return Future.failedFuture(new OkapiError(ErrorType.USER, ex.getMessage()));
    }
  }

  private Future<String> getModule(String id) {
    return moduleManager.get(id).compose(res -> Future.succeededFuture(Json.encodePrettily(res)));
  }

  private Future<String> listModules(ProxyContext pc, String body) {
    String [] skipModules = new String [0];
    if (!body.isEmpty()) {
      skipModules = Json.decodeValue(body, skipModules.getClass());
    }
    return moduleManager.getModulesWithFilter(true, true, Arrays.asList(skipModules))
        .compose(mdl -> {
          try {
            final boolean dot = ModuleUtil.getParamBoolean(pc.getCtx().request(), "dot", false);
            mdl = ModuleUtil.filter(pc.getCtx().request(), mdl, dot, true);
            if (dot) {
              String s = GraphDot.report(mdl);
              pc.getCtx().response().putHeader("Content-Type", "text/plain");
              return Future.succeededFuture(s);
            } else {
              String s = Json.encodePrettily(mdl);
              return Future.succeededFuture(s);
            }
          } catch (DecodeException ex) {
            return Future.failedFuture(new OkapiError(ErrorType.USER, ex.getMessage()));
          }
        });
  }

  private Future<String> deleteModule(String id) {
    return moduleManager.delete(id).compose(res -> Future.succeededFuture(""));
  }

  private Future<String> getDeployment(String id) {

    return deploymentManager.get(id)
        .compose(res -> Future.succeededFuture(Json.encodePrettily(res)));
  }

  private Future<String> listDeployments() {
    return deploymentManager.list()
        .compose(res -> Future.succeededFuture(Json.encodePrettily(res)));
  }

  private Future<String> createDeployment(ProxyContext pc, String body) {
    try {
      final DeploymentDescriptor pmd = Json.decodeValue(body,
          DeploymentDescriptor.class);
      return deploymentManager.deploy(pmd).compose(res -> {
        final String s = Json.encodePrettily(res);
        return location(pc, res.getInstId(), null, s);
      });
    } catch (DecodeException ex) {
      return Future.failedFuture(new OkapiError(ErrorType.USER, ex.getMessage()));
    }
  }

  private Future<String> deleteDeployment(String id) {
    return deploymentManager.undeploy(id).compose(res -> Future.succeededFuture(""));
  }

  private Future<String> getDiscoveryNode(String id) {
    return discoveryManager.getNode(id)
        .compose(node -> Future.succeededFuture(Json.encodePrettily(node)));
  }

  private Future<String> putDiscoveryNode(String id, String body) {
    try {
      final NodeDescriptor nd = Json.decodeValue(body, NodeDescriptor.class);
      return discoveryManager.updateNode(id, nd)
          .compose(res -> Future.succeededFuture(Json.encodePrettily(res)));
    } catch (DecodeException ex) {
      return Future.failedFuture(new OkapiError(ErrorType.USER, ex.getMessage()));
    }
  }

  private Future<String> listDiscoveryNodes() {
    return discoveryManager.getNodes()
        .compose(res -> Future.succeededFuture(Json.encodePrettily(res)));
  }

  private Future<String> listDiscoveryModules() {
    return discoveryManager.get()
        .compose(res -> Future.succeededFuture(Json.encodePrettily(res)));
  }

  private Future<String> discoveryGetSrvcId(String srvcId) {

    return discoveryManager.getNonEmpty(srvcId)
        .compose(res -> Future.succeededFuture(Json.encodePrettily(res)));
  }

  private Future<String> discoveryGetInstId(String srvcId, String instId) {
    return discoveryManager.get(srvcId, instId)
        .compose(res -> Future.succeededFuture(Json.encodePrettily(res)));
  }

  private Future<String> discoveryDeploy(ProxyContext pc, String body) {
    try {
      final DeploymentDescriptor pmd = Json.decodeValue(body,
          DeploymentDescriptor.class);
      return discoveryManager.addAndDeploy(pmd).compose(md -> {
        final String s = Json.encodePrettily(md);
        final String baseuri = pc.getCtx().request().uri();
        String[] ids = new String [2];
        ids[0] = md.getSrvcId();
        ids[1] = md.getInstId();
        return location(pc, ids, baseuri, s);
      });
    } catch (DecodeException ex) {
      return Future.failedFuture(new OkapiError(ErrorType.USER, ex.getMessage()));
    }
  }

  private Future<String> discoveryUndeploy(String srvcId, String instId) {
    return discoveryManager.removeAndUndeploy(srvcId, instId)
        .compose(res -> Future.succeededFuture(""));
  }

  private Future<String> discoveryUndeploy(String srvcId) {
    return discoveryManager.removeAndUndeploy(srvcId)
        .compose(res -> Future.succeededFuture(""));
  }

  private Future<String> discoveryUndeploy() {
    return discoveryManager.removeAndUndeploy()
        .compose(res -> Future.succeededFuture(""));
  }

  private Future<String> discoveryHealthAll() {
    return discoveryManager.health()
        .compose(res -> Future.succeededFuture(Json.encodePrettily(res)));
  }

  private Future<String> discoveryHealthSrvcId(String srvcId) {
    return discoveryManager.health(srvcId)
        .compose(res -> Future.succeededFuture(Json.encodePrettily(res)));
  }

  private Future<String> discoveryHealthOne(String srvcId, String instId) {
    return discoveryManager.health(srvcId, instId)
        .compose(res -> Future.succeededFuture(Json.encodePrettily(res)));
  }

  private Future<String> listEnv() {
    return envManager.get().compose(res -> Future.succeededFuture(Json.encodePrettily(res)));
  }

  private Future<String> getEnv(String id) {
    return envManager.get(id).compose(res -> Future.succeededFuture(Json.encodePrettily(res)));
  }

  private Future<String> createEnv(ProxyContext pc, String body) {
    try {
      final EnvEntry pmd = Json.decodeValue(body, EnvEntry.class);
      return envManager.add(pmd).compose(res -> {
        final String js = Json.encodePrettily(pmd);
        return location(pc, pmd.getName(), null, js);
      });
    } catch (DecodeException ex) {
      return Future.failedFuture(new OkapiError(ErrorType.USER, ex.getMessage()));
    }
  }

  private Future<String> deleteEnv(String id) {
    return envManager.remove(id).compose(res -> Future.succeededFuture(""));
  }

  private Future<String> pullModules(String body) {

    try {
      final PullDescriptor pmd = Json.decodeValue(body, PullDescriptor.class);
      return pullManager.pull(pmd)
          .compose(res -> Future.succeededFuture(Json.encodePrettily(res)));
    } catch (DecodeException ex) {
      return Future.failedFuture(new OkapiError(ErrorType.USER, ex.getMessage()));
    }
  }

  /**
   * Pretty simplistic health check.
   */
  private Future<String> getHealth() {
    return Future.succeededFuture("[ ]");
  }

  private Future<String> getVersion(ProxyContext pc) {
    String v = okapiVersion;
    if (v == null) {
      v = "0.0.0";
    }
    pc.getCtx().response().putHeader("Content-Type", "text/plain"); // !!
    return Future.succeededFuture(v);
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
   * @return Callback with the response body
   */
  // Cognitive Complexity of methods should not be too high
  // but this function is really just a big switch
  @java.lang.SuppressWarnings({"squid:S3776"})
  public Future<String> internalService(String req, ProxyContext pc) {

    RoutingContext ctx = pc.getCtx();
    String p = ctx.normalizedPath();
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
          return listModules(pc, req);
        }
        if (n == 4 && m.equals(HttpMethod.POST)) {
          return createModule(pc, req);
        }
        // /_/proxy/modules/:id
        if (n == 5 && m.equals(HttpMethod.GET)) {
          return getModule(decodedSegs[4]);
        }
        if (n == 5 && m.equals(HttpMethod.DELETE)) {
          return deleteModule(decodedSegs[4]);
        }
      } // /_/proxy/modules

      if (segments[3].equals("tenants")
          && tenantManager != null) {
        // /_/proxy/tenants
        if (n == 4 && m.equals(HttpMethod.GET)) {
          return listTenants();
        }
        if (n == 4 && m.equals(HttpMethod.POST)) {
          return createTenant(pc, req);
        }
        // /_/proxy/tenants/:id
        if (n == 5 && m.equals(HttpMethod.GET)) {
          return getTenant(decodedSegs[4]);
        }
        if (n == 5 && m.equals(HttpMethod.PUT)) {
          return updateTenant(decodedSegs[4], req);
        }
        if (n == 5 && m.equals(HttpMethod.DELETE)) {
          return deleteTenant(decodedSegs[4]);
        }
        // /_/proxy/tenants/:id/modules
        if (n == 6 && m.equals(HttpMethod.GET) && segments[5].equals("modules")) {
          return listModulesForTenant(pc, decodedSegs[4]);
        }
        if (n == 6 && m.equals(HttpMethod.POST) && segments[5].equals("modules")) {
          return enableModuleForTenant(pc, decodedSegs[4], req);
        }
        if (n == 6 && m.equals(HttpMethod.DELETE) && segments[5].equals("modules")) {
          return disableModulesForTenant(pc, decodedSegs[4]);
        }
        // /_/proxy/tenants/:id/modules/:mod
        if (n == 7 && m.equals(HttpMethod.GET) && segments[5].equals("modules")) {
          return getModuleForTenant(decodedSegs[4], decodedSegs[6]);
        }
        if (n == 7 && m.equals(HttpMethod.POST) && segments[5].equals("modules")) {
          return upgradeModuleForTenant(pc, decodedSegs[4], decodedSegs[6], req);
        }
        if (n == 7 && m.equals(HttpMethod.DELETE) && segments[5].equals("modules")) {
          return disableModuleForTenant(pc, decodedSegs[4], decodedSegs[6]);
        }
        // /_/proxy/tenants/:id/install
        if (n == 6 && m.equals(HttpMethod.GET) && segments[5].equals("install")) {
          return installTenantModulesGetList(decodedSegs[4]);
        }
        // /_/proxy/tenants/:id/install
        if (n == 6 && m.equals(HttpMethod.POST) && segments[5].equals("install")) {
          return installTenantModulesPost(pc, decodedSegs[4], req);
        }
        // /_/proxy/tenants/:tid/install/:rid
        if (n == 7 && m.equals(HttpMethod.GET) && segments[5].equals("install")) {
          return installTenantModulesGet(decodedSegs[4], decodedSegs[6]);
        }
        // /_/proxy/tenants/:id/upgrade
        if (n == 6 && m.equals(HttpMethod.POST) && segments[5].equals("upgrade")) {
          return upgradeModulesForTenant(pc, decodedSegs[4]);
        }
        // /_/proxy/tenants/:id/interfaces
        if (n == 6 && m.equals(HttpMethod.GET) && segments[5].equals("interfaces")) {
          return listInterfaces(pc, decodedSegs[4]);
        }

        // /_/proxy/tenants/:id/interfaces/:int
        if (n == 7 && m.equals(HttpMethod.GET) && segments[5].equals("interfaces")) {
          return listModulesFromInterface(pc, decodedSegs[4], decodedSegs[6]);
        }
      } // /_/proxy/tenants

      // /_/proxy/pull/modules
      if (n == 5 && segments[3].equals("pull") && segments[4].equals("modules")
          && m.equals(HttpMethod.POST) && pullManager != null) {
        return pullModules(req);
      }
      // /_/proxy/health
      if (n == 4 && segments[3].equals("health") && m.equals(HttpMethod.GET)) {
        return getHealth();
      }

    } // _/proxy

    // deployment
    if (n >= 4 && p.startsWith("/_/deployment/")
        && segments[3].equals("modules")
        && deploymentManager != null) {
      // /_/deployment/modules
      if (n == 4 && m.equals(HttpMethod.GET)) {
        return listDeployments();
      }
      if (n == 4 && m.equals(HttpMethod.POST)) {
        return createDeployment(pc, req);
      }
      // /_/deployment/modules/:id:
      if (n == 5 && m.equals(HttpMethod.GET)) {
        return getDeployment(decodedSegs[4]);
      }
      if (n == 5 && m.equals(HttpMethod.DELETE)) {
        return deleteDeployment(decodedSegs[4]);
      }
    } // deployment

    if (n >= 4 && p.startsWith("/_/discovery/")
        && discoveryManager != null) {
      // /_/discovery/nodes
      if (n == 4 && segments[3].equals("nodes") && m.equals(HttpMethod.GET)) {
        return listDiscoveryNodes();
      }
      // /_/discovery/nodes/:nodeid
      if (n == 5 && segments[3].equals("nodes") && m.equals(HttpMethod.GET)) {
        return getDiscoveryNode(decodedSegs[4]);
      }
      // /_/discovery/nodes/:nodeid
      if (n == 5 && segments[3].equals("nodes") && m.equals(HttpMethod.PUT)) {
        return putDiscoveryNode(decodedSegs[4], req);
      }

      // /_/discovery/modules
      if (n == 4 && segments[3].equals("modules") && m.equals(HttpMethod.GET)) {
        return listDiscoveryModules();
      }
      if (n == 4 && segments[3].equals("modules") && m.equals(HttpMethod.POST)) {
        return discoveryDeploy(pc, req);
      }
      // /_/discovery/modules/:srvcid
      if (n == 5 && segments[3].equals("modules") && m.equals(HttpMethod.GET)) {
        return discoveryGetSrvcId(decodedSegs[4]);
      }
      // /_/discovery/modules/:srvcid/:instid"
      if (n == 6 && segments[3].equals("modules") && m.equals(HttpMethod.GET)) {
        return discoveryGetInstId(decodedSegs[4], decodedSegs[5]);
      }
      if (n == 6 && segments[3].equals("modules") && m.equals(HttpMethod.DELETE)) {
        return discoveryUndeploy(decodedSegs[4], decodedSegs[5]);
      }
      if (n == 5 && segments[3].equals("modules") && m.equals(HttpMethod.DELETE)) {
        return discoveryUndeploy(decodedSegs[4]);
      }
      if (n == 4 && segments[3].equals("modules") && m.equals(HttpMethod.DELETE)) {
        return discoveryUndeploy();
      }
      // /_/discovery/health
      if (n == 4 && segments[3].equals("health") && m.equals(HttpMethod.GET)) {
        return discoveryHealthAll();
      }
      // /_/discovery/health/:srvcId
      if (n == 5 && segments[3].equals("health") && m.equals(HttpMethod.GET)) {
        return discoveryHealthSrvcId(decodedSegs[4]);
      }
      // /_/discovery/health/:srvcId/:instid
      if (n == 6 && segments[3].equals("health") && m.equals(HttpMethod.GET)) {
        return discoveryHealthOne(decodedSegs[4], decodedSegs[5]);
      }
    } // discovery

    if (n >= 2 && p.startsWith("/_/env")
        && segments[2].equals("env")) { // not envXX or such

      // /_/env
      if (n == 3 && m.equals(HttpMethod.GET)) {
        return listEnv();
      }
      if (n == 3 && m.equals(HttpMethod.POST)) {
        return createEnv(pc, req);
      }
      // /_/env/name
      if (n == 4 && m.equals(HttpMethod.GET)) {
        return getEnv(decodedSegs[3]);
      }
      if (n == 4 && m.equals(HttpMethod.DELETE)) {
        return deleteEnv(decodedSegs[3]);
      }

    } // env

    if (p.equals("/_/version") && m.equals(HttpMethod.GET)) {
      return getVersion(pc);
    }
    return Future.failedFuture(messages.getMessage("11607", p));
  }

}
