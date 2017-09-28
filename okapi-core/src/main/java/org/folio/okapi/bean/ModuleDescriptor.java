package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
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
  private ModuleId id;
  private String name;

  private String[] tags;
  private ModuleId moduleId;
  private ModuleInterface[] requires;
  private ModuleInterface[] provides;
  private RoutingEntry[] filters;
  private Permission[] permissionSets;
  private UiModuleDescriptor uiDescriptor;
  private LaunchDescriptor launchDescriptor;

  public ModuleDescriptor() {
    this.moduleId = null;
    this.id = null;
    this.name = null;
    this.tags = null;
    this.filters = null;
    this.requires = null;
    this.provides = null;
    this.permissionSets = null;
    this.uiDescriptor = null;
    this.launchDescriptor = null;
  }

  /**
   * Copy constructor.
   *
   * @param other
   * @param full
   */
  public ModuleDescriptor(ModuleDescriptor other, boolean full) {
    this.moduleId = other.moduleId;
    this.id = other.id;
    this.name = other.name;
    this.tags = other.tags;
    if (full) {
      this.filters = other.filters;
      this.requires = other.requires;
      this.provides = other.provides;
      this.permissionSets = other.permissionSets;
      this.uiDescriptor = other.uiDescriptor;
      this.launchDescriptor = other.launchDescriptor;
    }
  }

  public String getId() {
    return id.getId();
  }

  public void setId(String s) {
    this.id = new ModuleId(s);
    if (!this.id.hasSemVer()) {
      throw new IllegalArgumentException("Missing semantic version for: " + s);
    }
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
   *
   * @param pc
   * @return "" if ok, otherwise an informative error message.
   */
  public String validate(ProxyContext pc) {
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
    return "";
  }

  @Override
  public int compareTo(ModuleDescriptor other) {
    return id.compareTo(other.id);
  }

  @Override
  public boolean equals(Object that) {
    if (this == that) {
      return true;
    }
    if (!(that instanceof ModuleDescriptor)) {
      return false;
    }
    return compareTo((ModuleDescriptor) that) == 0;
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @JsonIgnore
  public String getProduct() {
    return id.getProduct();
  }
}
