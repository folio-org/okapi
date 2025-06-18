package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.ModuleId;
import org.folio.okapi.common.XOkapiHeaders;

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

  public ModuleDescriptor(String id) {
    setId(id);
  }

  /**
   * Copy constructor with id and tags from original.
   *
   * @param original where we copy from
   * @param includeName where name is also copied
   */
  public ModuleDescriptor(ModuleDescriptor original, boolean includeName) {
    this.id = original.id;
    if (includeName) {
      this.name = original.name;
    }
    this.tags = original.tags;
  }

  public String getId() {
    return id != null ? id.getId() : null;
  }

  /**
   * set module ID.
   * @param s module ID
   */
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

  /**
   * Set module tags.
   * @param tags tags list
   */
  public void setTags(String[] tags) {
    this.tags = tags;
  }

  /**
   * Get required and optional interfaces.
   */
  @JsonIgnore
  public List<InterfaceDescriptor> getRequiresOptionalList() {
    List<InterfaceDescriptor> l = new ArrayList<>();
    Collections.addAll(l, getRequiresList());
    Collections.addAll(l, getOptionalList());
    return l;
  }

  /**
   * Get required interfaces.
   * @return interfaces list; empty if none is defined
   */
  @JsonIgnore
  public InterfaceDescriptor[] getRequiresList() {
    if (requires == null) {
      return new InterfaceDescriptor[0];
    } else {
      return requires;
    }
  }

  /**
   * Get required interfaces.
   * @return interfaces; null if none is defined
   */
  public InterfaceDescriptor[] getRequires() {
    return requires;
  }

  /**
   * set required interfaces.
   * @param requires required interfaces
   */
  public void setRequires(InterfaceDescriptor[] requires) {
    this.requires = requires;
  }

  /**
   * Set requires utililty.
   * @param id interface ID
   * @param version interface version
   */
  @JsonIgnore
  public void setRequires(String id, String version) {
    InterfaceDescriptor interfaceDescriptor = new InterfaceDescriptor();
    interfaceDescriptor.setId(id);
    interfaceDescriptor.setVersion(version);
    this.requires = new InterfaceDescriptor[1];
    this.requires[0] = interfaceDescriptor;
  }

  /**
   * Get provided interfaces.
   * @return interfaces; empty list if none is defined
   */
  @JsonIgnore
  public InterfaceDescriptor[] getProvidesList() {
    if (provides == null) {
      return new InterfaceDescriptor[0];
    } else {
      return provides;
    }
  }

  /**
   * Get provided interfaces.
   * @return interfaces; null if none is defined
   */
  public InterfaceDescriptor[] getProvides() {
    return provides;
  }

  /**
   * set provided interfaces.
   * @param provides provided interfaces
   */
  public void setProvides(InterfaceDescriptor[] provides) {
    Set<String> p = new TreeSet<>();
    for (InterfaceDescriptor pr : provides) {
      if (p.contains(pr.getId())) {
        throw new IllegalArgumentException("Interface " + pr.getId() + " provided multiple times");
      }
      p.add(pr.getId());
    }
    this.provides = provides;
  }

  /**
   *  Set provided interfacer utility.
   * @param id interface ID
   * @param version interface version
   * @param entries routing entries
   */
  @JsonIgnore
  public void setProvidedHandler(String id, String version, RoutingEntry... entries) {
    InterfaceDescriptor[] interfaceDescriptors = new InterfaceDescriptor[1];
    InterfaceDescriptor interfaceDescriptor = interfaceDescriptors[0] = new InterfaceDescriptor();
    interfaceDescriptor.setId(id);
    interfaceDescriptor.setVersion(version);
    interfaceDescriptor.setHandlers(entries);
    setProvides(interfaceDescriptors);
  }

  public InterfaceDescriptor[] getOptional() {
    return optional;
  }

  public void setOptional(InterfaceDescriptor[] optional) {
    this.optional = optional;
  }

  /**
   * Get optional interfaces.
   * @return interfaces; empty list if none is defined
   */
  @JsonIgnore
  public InterfaceDescriptor[] getOptionalList() {
    if (optional == null) {
      return new InterfaceDescriptor[0];
    } else {
      return optional;
    }
  }

  /**
   * Get filter routing entries.
   */
  @JsonIgnore
  public List<RoutingEntry> getFilterRoutingEntries() {
    List<RoutingEntry> all = new ArrayList<>();
    if (filters != null) {
      Collections.addAll(all, filters);
    }
    return all;
  }

  /**
   * Get auth routing entry if such exists for module.
   * @return entry for auth; null if no such filter exists for module.
   */
  @JsonIgnore
  public RoutingEntry getAuthRoutingEntry() {
    if (filters != null) {
      for (RoutingEntry filt: filters) {
        if (XOkapiHeaders.FILTER_AUTH.equals(filt.getPhase())) {
          return filt;
        }
      }
    }
    return null;
  }

  /**
   * Get all RoutingEntries that are type proxy.
   * Either from provided interfaces, or from the global level RoutingEntries.
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

  /**
   * Get all routing entries that are of type multi.
   */
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

  /**
   * Set permission sets.
   * @param permissionSets permission sets
   * @throws IllegalArgumentException if permissionName is missing
   */
  public void setPermissionSets(Permission[] permissionSets) {
    if (permissionSets != null) {
      for (Permission permission : permissionSets) {
        String permissionName = permission.getPermissionName();
        if (permissionName == null) {
          throw new IllegalArgumentException("Missing permissionName");
        }
      }
    }
    this.permissionSets = permissionSets;
  }

  /**
   * Get existing permission sets plus those generated from modulePermissions.
   *
   * @return array of {@link Permission}
   */
  @JsonIgnore
  public Permission[] getExpandedPermissionSets() {
    List<Permission> perms = new ArrayList<>();
    if (provides != null) {
      for (InterfaceDescriptor idesc : provides) {
        extractModulePermissions(idesc.getHandlers(), perms);
      }
    }
    extractModulePermissions(filters, perms);
    if (perms.isEmpty()) {
      return permissionSets;
    }
    if (permissionSets != null) {
      perms.addAll(0, Arrays.asList(permissionSets));
    }
    Permission[] permissions = new Permission[perms.size()];
    perms.toArray(permissions);
    return permissions;
  }

  private void extractModulePermissions(RoutingEntry[] routingEntries, List<Permission> perms) {
    if (routingEntries == null) {
      return;
    }
    for (RoutingEntry re : routingEntries) {
      if (re.getModulePermissions() == null || re.getModulePermissions().length == 0) {
        continue;
      }
      String permName = re.generateSystemId(id.getId());
      Permission perm = new Permission();
      perm.setPermissionName(permName);
      perm.setDisplayName("System generated: " + permName);
      perm.setDescription("System generated permission set");
      perm.setSubPermissions(re.getModulePermissions());
      perm.setVisible(Boolean.FALSE);
      perms.add(perm);
    }
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

  /**
   * Get list of replaced modules.
   */
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

  /**
   * Set list of replaced modules.
   * @param replaces replaced module
   */
  public void setReplaces(String[] replaces) {
    if (replaces == null || replaces.length == 0) {
      this.replaces = null;
    } else {
      this.replaces = new ModuleId[replaces.length];
      for (int i = 0; i < replaces.length; i++) {
        final ModuleId pId = new ModuleId(replaces[i]);
        if (pId.hasSemVer()) {
          throw new IllegalArgumentException("No semantic version for: " + replaces[i]);
        }
        this.replaces[i] = pId;
      }
    }
  }

  /**
   * Validate some features of a ModuleDescriptor.
   * In case of Deprecated things, writes warnings in the log.
   *
   * @param logger where validate warnings are logged
   * @return "" if ok, otherwise an informative error message.
   */
  public String validate(Logger logger) {
    if (id == null) {
      return "id is missing for module";
    }
    String mod = getId();
    if (provides != null) {
      for (InterfaceDescriptor pr : provides) {
        String err = pr.validate(logger, "provides", mod);
        if (!err.isEmpty()) {
          return err;
        }
      }
    }
    if (requires != null) {
      for (InterfaceDescriptor pr : requires) {
        String err = pr.validate(logger, "requires", mod);
        if (!err.isEmpty()) {
          return err;
        }
      }
    } else {
      logger.warn("Module '{}' has no Requires section. If the module really does not require "
          + "any other interfaces, provide an empty array to be explicit about it.", mod);
    }
    if (filters != null) {
      for (RoutingEntry fe : filters) {
        String err = fe.validateFilters(logger, mod);
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
    if (that == null || that.getClass() != this.getClass()) {
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
