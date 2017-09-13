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
import org.folio.okapi.util.ModuleId;

/**
 * Description of a module. These are used when creating modules under
 * "/_/proxy/modules" etc.
 *
 */
@JsonInclude(Include.NON_NULL)
public class ModuleDescriptor implements Comparable<ModuleDescriptor> {
  private final Logger logger = LoggerFactory.getLogger("okapi");

  private String id;
  private String name;

  private String[] tags;
  private EnvEntry[] env;

  private ModuleId moduleId;
  private ModuleInterface[] requires;
  private ModuleInterface[] provides;
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
    this.moduleId = other.moduleId;
    this.id = other.id;
    this.name = other.name;
    this.tags = other.tags;
    this.env = other.env;
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
    try {
      moduleId = new ModuleId(id);
    } catch (IllegalArgumentException e) {
      logger.warn("ModuleId exception: " + id + " msg: " + e.getMessage());
      moduleId = null;
    }
    this.id = id;
  }
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
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

  @JsonIgnore
  public ModuleInterface[] getRequiresList() {
    if (requires == null) {
      return new ModuleInterface[0];
    } else {
      return requires;
    }
  }

  public ModuleInterface[] getRequires() {
    return requires;
  }

  public void setRequires(ModuleInterface[] requires) {
    this.requires = requires;
  }

  @JsonIgnore
  public ModuleInterface[] getProvidesList() {
    if (provides == null) {
      return new ModuleInterface[0];
    } else {
      return provides;
    }
  }

  public ModuleInterface[] getProvides() {
    return provides;
  }

  public void setProvides(ModuleInterface[] provides) {
    this.provides = provides;
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
    if (filters != null) {
      Collections.addAll(all, filters);
    }
    for (ModuleInterface mi : getProvidesList()) {
      String t = mi.getInterfaceType();
      if (t == null || t.equals("proxy") || t.equals("internal")) {
        all.addAll(mi.getAllRoutingEntries());
      }
    }
    return all;
  }

  @JsonIgnore
  public List<RoutingEntry> getMultiRoutingEntries() {
    List<RoutingEntry> all = new ArrayList<>();
    for (ModuleInterface mi : getProvidesList()) {
      if ("multiple".equals(mi.getInterfaceType())) {
        all.addAll(mi.getAllRoutingEntries());
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
    for (ModuleInterface prov : getProvidesList()) {
      if ("system".equals(prov.getInterfaceType())
        && interfaceId.equals(prov.getId())) {
        return prov;
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
    if (moduleId == null) {
      pc.warn("Invalid semantic version for module Id: " + getId());
    } else if (!moduleId.hasSemVer()) {
      pc.warn("Missing semantic version for module Id: " + getId());
    }
    String mod = getId();
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
      pc.warn("Module '" + mod + "' "
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
    if (getEnv() != null) {
      pc.warn("Module '" + mod + "' "
        + " uses DEPRECATED top-level environment settings. Put those "
        + "in the launchDescriptor instead.");
    }
    if (getTenantInterface() != null) {
      pc.warn("Module '" + mod + "' "
        + "uses DEPRECATED tenantInterface field."
        + " Provide a '_tenant' system interface instead");
    }
    return "";
  }

  public int compareTo(ModuleDescriptor other) {
    if (this.moduleId != null && other.moduleId != null) {
      return this.moduleId.compareTo(other.moduleId);
    } else if (this.moduleId != null && other.moduleId == null) {
      return 1;
    } else if (this.moduleId == null && other.moduleId != null) {
      return -1;
    } else {
      return 0;
    }
  }

  @JsonIgnore
  public String getProduct() {
    if (this.moduleId != null) {
      return this.moduleId.getProduct();
    } else {
      return id;
    }
  }
}
