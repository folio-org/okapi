package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.folio.okapi.util.ProxyContext;
import org.folio.okapi.common.ModuleId;

/**
 * Description of a module. These are used when creating modules under
 * "/_/proxy/modules" etc.
 *
 */
// S1168: Empty arrays and collections should be returned instead of null
@java.lang.SuppressWarnings({"squid:S1168"})
@JsonInclude(Include.NON_NULL)
public class ModuleDescriptor implements Comparable<ModuleDescriptor> {

  private ModuleId id;
  private String name;

  private String[] tags;
  private InterfaceDescriptor[] requires;
  private InterfaceDescriptor[] provides;
  private InterfaceDescriptor[] optional;
  private RoutingEntry[] filters;
  private Permission[] permissionSets;
  private EnvEntry[] env;
  private AnyDescriptor metadata;
  private UiModuleDescriptor uiDescriptor;
  private LaunchDescriptor launchDescriptor;
  private ModuleId[] replaces;

  public ModuleDescriptor() {
  }

  /**
   * Copy constructor.
   *
   * @param other
   * @param full
   */
  public ModuleDescriptor(ModuleDescriptor other, boolean includeName) {
    this.id = other.id;
    if (includeName) {
      this.name = other.name;
    }
    this.tags = other.tags;
  }

  public String getId() {
    return id != null ? id.getId() : null;
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
  public List<InterfaceDescriptor> getRequiresOptionalList() {
    List<InterfaceDescriptor> l = new ArrayList<>();
    Collections.addAll(l, getRequiresList());
    Collections.addAll(l, getOptionalList());
    return l;
  }

  @JsonIgnore
  public InterfaceDescriptor[] getRequiresList() {
    if (requires == null) {
      return new InterfaceDescriptor[0];
    } else {
      return requires;
    }
  }

  public InterfaceDescriptor[] getRequires() {
    return requires;
  }

  public void setRequires(InterfaceDescriptor[] requires) {
    this.requires = requires;
  }

  @JsonIgnore
  public InterfaceDescriptor[] getProvidesList() {
    if (provides == null) {
      return new InterfaceDescriptor[0];
    } else {
      return provides;
    }
  }

  public InterfaceDescriptor[] getProvides() {
    return provides;
  }

  public void setProvides(InterfaceDescriptor[] provides) {
    Set<String> pList = new TreeSet<>();
    for (int i = 0; i < provides.length; i++) {
      InterfaceDescriptor pr = provides[i];
      if (pList.contains(pr.getId())) {
        throw new IllegalArgumentException("Interface " + pr.getId() + " provided multiple times");
      }
      pList.add(pr.getId());
    }
    this.provides = provides;
  }

  public InterfaceDescriptor[] getOptional() {
    return optional;
  }

  public void setOptional(InterfaceDescriptor[] optional) {
    this.optional = optional;
  }

  @JsonIgnore
  public InterfaceDescriptor[] getOptionalList() {
    if (optional == null) {
      return new InterfaceDescriptor[0];
    } else {
      return optional;
    }
  }

  @JsonIgnore
  public List<RoutingEntry> getFilterRoutingEntries() {
    List<RoutingEntry> all = new ArrayList<>();
    if (filters != null) {
      Collections.addAll(all, filters);
    }
    return all;
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
    for (InterfaceDescriptor mi : getProvidesList()) {
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
    for (InterfaceDescriptor mi : getProvidesList()) {
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
  public InterfaceDescriptor getSystemInterface(String interfaceId) {
    for (InterfaceDescriptor prov : getProvidesList()) {
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

  public EnvEntry[] getEnv() {
    return env;
  }

  public void setEnv(EnvEntry[] env) {
    this.env = env;
  }

  public AnyDescriptor getMetadata() {
    return metadata;
  }

  public void setMetadata(AnyDescriptor metadata) {
    this.metadata = metadata;
  }

  public RoutingEntry[] getFilters() {
    return filters;
  }

  public void setFilters(RoutingEntry[] filters) {
    this.filters = filters;
  }

  public String[] getReplaces() {
    if (replaces == null) {
      return null;
    }
    String[] a = new String[replaces.length];
    for (int i = 0; i < replaces.length; i++) {
      a[i] = replaces[i].getProduct();
    }
    return a;
  }

  public void setReplaces(String[] replaces) {
    if (replaces == null || replaces.length == 0) {
      this.replaces = null;
    } else {
      this.replaces = new ModuleId[replaces.length];
      for (int i = 0; i < replaces.length; i++)
      {
        final ModuleId pId =new ModuleId(replaces[i]);
        if (pId.hasSemVer()) {
          throw new IllegalArgumentException("No semantic version for: " + replaces[i]);
        }
        this.replaces[i] = pId;
      }
    }
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
    if (id == null) {
      return "id is missing for module";
    }
    String mod = getId();
    if (provides != null) {
      for (InterfaceDescriptor pr : provides) {
        String err = pr.validate(pc, "provides", mod);
        if (!err.isEmpty()) {
          return err;
        }
      }
    }
    if (requires != null) {
      for (InterfaceDescriptor pr : requires) {
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
        String err = fe.validateFilters(pc, mod);
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
