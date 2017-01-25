package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Description of a module. These are used when creating modules under
 * "/_/proxy/modules" etc.
 *
 */
@JsonInclude(Include.NON_NULL)
public class ModuleDescriptor {

  private String id;
  private String name;

  private String[] tags;
  private ModuleInterface[] provides;
  private ModuleInterface[] requires;
  private RoutingEntry[] routingEntries;
  private String[] modulePermissions;
  private UiModuleDescriptor uiDescriptor;
  private LaunchDescriptor launchDescriptor;

  private String tenantInterface;

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
    this.routingEntries = other.routingEntries;
    this.provides = other.provides;
    this.requires = other.requires;
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

  public String[] getTags() {
    return tags;
  }

  public void setTags(String[] tags) {
    this.tags = tags;
  }

  public ModuleInterface[] getProvides() {
    return provides;
  }

  public void setProvides(ModuleInterface[] provides) {
    this.provides = provides;
  }

  public ModuleInterface[] getRequires() {
    return requires;
  }

  public void setRequires(ModuleInterface[] requires) {
    this.requires = requires;
  }

  public RoutingEntry[] getRoutingEntries() {
    return routingEntries;
  }

  public void setRoutingEntries(RoutingEntry[] routingEntries) {
    this.routingEntries = routingEntries;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
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


}
