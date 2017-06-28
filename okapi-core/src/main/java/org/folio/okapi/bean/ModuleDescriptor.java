package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.folio.okapi.util.ProxyContext;

/**
 * Description of a module. These are used when creating modules under
 * "/_/proxy/modules" etc.
 *
 */
@JsonInclude(Include.NON_NULL)
public class ModuleDescriptor {
  private final Logger logger = LoggerFactory.getLogger("okapi");

  private String id;
  private String name;

  private String[] tags;
  private EnvEntry[] env;

  private ModuleInterface[] requires;
  private ModuleInterface[] provides;
  private RoutingEntry[] routingEntries; //DEPRECATED
  private RoutingEntry[] filters;
  private Permission[] permissionSets;
  private String[] modulePermissions; // DEPRECATED
  private UiModuleDescriptor uiDescriptor;
  private LaunchDescriptor launchDescriptor;
  private String tenantInterface; // DEPRECATED

  public ModuleDescriptor() {
  }

  /**
   * Copy constructor.
   *
   * @param other
   */
  public ModuleDescriptor(ModuleDescriptor other) {
    this.id = other.id;
    this.name = other.name;
    this.tags = other.tags;
    this.env = other.env;
    this.routingEntries = other.routingEntries;
    this.filters = other.filters;
    this.requires = other.requires;
    this.provides = other.provides;
    this.permissionSets = other.permissionSets;
    this.modulePermissions = other.modulePermissions;
    this.uiDescriptor = other.uiDescriptor;
    this.launchDescriptor = other.launchDescriptor;
    this.tenantInterface = other.tenantInterface;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @JsonIgnore
  public String getNameOrId() {
    if (name != null && !name.isEmpty()) {
      return name;
    }
    return id;
  }

  public String[] getTags() {
    return tags;
  }

  public void setTags(String[] tags) {
    this.tags = tags;
  }

  public EnvEntry[] getEnv() {
    return env;
  }

  public void setEnv(EnvEntry[] env) {
    this.env = env;
  }

  public ModuleInterface[] getRequires() {
    return requires;
  }

  public void setRequires(ModuleInterface[] requires) {
    this.requires = requires;
  }

  public ModuleInterface[] getProvides() {
    return provides;
  }

  public void setProvides(ModuleInterface[] provides) {
    this.provides = provides;
  }

  public RoutingEntry[] getRoutingEntries() {
    return routingEntries;
  }

  public void setRoutingEntries(RoutingEntry[] routingEntries) {
    this.routingEntries = routingEntries;
  }

  /**
   * Get all RoutingEntries that are type proxy. Either from provided
   * interfaces, or from the global level RoutingEntries.
   *
   * @return
   */
  @JsonIgnore
  public List<RoutingEntry> getProxyRoutingEntries() {
    List<RoutingEntry> all = new ArrayList<>();
    if (routingEntries != null) {
      Collections.addAll(all, routingEntries);
    }
    if (filters != null) {
      Collections.addAll(all, filters);
    }
    ModuleInterface[] prov = getProvides();
    if (prov != null) {
      for (ModuleInterface mi : prov) {
        String t = mi.getInterfaceType();
        if (t == null || t.equals("proxy")) {
          all.addAll(mi.getAllRoutingEntries());
        }
      }
    }
    return all;
  }

  @JsonIgnore
  public List<RoutingEntry> getMultiRoutingEntries() {
    List<RoutingEntry> all = new ArrayList<>();
    ModuleInterface[] prov = getProvides();
    if (prov != null) {
      for (ModuleInterface mi : prov) {
        if ("multiple".equals(mi.getInterfaceType())) {
          all.addAll(mi.getAllRoutingEntries());
        }
      }
    }
    return all;
  }

  /**
   * Get the given system interface, if the MD has one.
   *
   * @param interfaceId name of the interface we want
   * @return null if not found, or the interface
   *
   * TODO - Take a version too, check compatibility
   */
  @JsonIgnore
  public ModuleInterface getSystemInterface(String interfaceId) {
    ModuleInterface[] provlist = getProvides();
    if (provlist != null) {
      for (ModuleInterface prov : provlist) {
      if ("system".equals(prov.getInterfaceType())
        && interfaceId.equals(prov.getId())) {
        return prov;
        }
      }
    }
    return null;
  }

  public String[] getModulePermissions() {
    return modulePermissions;
  }

  public void setModulePermissions(String[] modulePermissions) {
    this.modulePermissions = modulePermissions;
  }

  public UiModuleDescriptor getUiDescriptor() {
    return uiDescriptor;
  }

  public void setUiDescriptor(UiModuleDescriptor uiDescriptor) {
    this.uiDescriptor = uiDescriptor;
  }

  public LaunchDescriptor getLaunchDescriptor() {
    return launchDescriptor;
  }

  public void setLaunchDescriptor(LaunchDescriptor launchDescriptor) {
    this.launchDescriptor = launchDescriptor;
  }

  public String getTenantInterface() {
    return tenantInterface;
  }

  public void setTenantInterface(String tenantInterface) {
    this.tenantInterface = tenantInterface;
  }

  public Permission[] getPermissionSets() {
    return permissionSets;
  }

  public void setPermissionSets(Permission[] permissionSets) {
    this.permissionSets = permissionSets;
  }

  public RoutingEntry[] getFilters() {
    return filters;
  }

  public void setFilters(RoutingEntry[] filters) {
    this.filters = filters;
  }

  /**
   * Validate some features of a ModuleDescriptor.
   *
   * In case of Deprecated things, writes warnings in the log.
   * TODO: Turn these into errors when releasing 2.0
   *
   * @param pc
   * @return "" if ok, otherwise an informative error message.
   */
  public String validate(ProxyContext pc) {
    if (getId() == null || getId().isEmpty()) {
      return "No Id in module";
    }
    if (!getId().matches("^[a-zA-Z0-9+._-]+$")) {
      return "Invalid id: " + getId();
    }
    String mod = getNameOrId();
    if (provides != null) {
      for (ModuleInterface pr : provides) {
        String err = pr.validate(pc, "provides", mod);
        if (!err.isEmpty()) {
          return err;
        }
      }
    }
    if (requires != null) {
      for (ModuleInterface pr : requires) {
        String err = pr.validate(pc, "requires", mod);
        if (!err.isEmpty()) {
          return err;
        }
      }
    } else {
      pc.warn("Module '" + this.getNameOrId() + "' "
        + "has no Requires section. If the module really does not require "
        + "any other interfaces, provide an empty array to be explicit about it.");
    }
    if (filters != null) {
      for (RoutingEntry fe : filters) {
        String err = fe.validate(pc, "filters", mod);
        if (!err.isEmpty()) {
          return err;
        }
      }
    }
    if (routingEntries != null) {
      pc.warn("Module '" + this.getNameOrId() + "' "
        + " uses DEPRECATED top-level routingEntries. Use handlers instead");
      for (RoutingEntry re : routingEntries) {
        String err = re.validate(pc, "toplevel", mod);
        if (!err.isEmpty()) {
          return err;
        }
      }
    }
    if (getEnv() != null) {
      pc.warn("Module '" + this.getNameOrId() + "' "
        + " uses DEPRECATED top-level environment settings. Put those "
        + "in the launchDescriptor instead.");
    }
    if (getTenantInterface() != null) {
      pc.warn("Module '" + this.getNameOrId() + "' "
        + "uses DEPRECATED tenantInterface field."
        + " Provide a '_tenant' system interface instead");
    }
    return "";
  }

}
