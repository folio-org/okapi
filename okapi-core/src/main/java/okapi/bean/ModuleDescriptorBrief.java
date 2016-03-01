/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.bean;

/**
 * A brief view of a ModuleDescriptor
 * 
 */
public class ModuleDescriptorBrief {
  private String id="";
  private String name="";
  private String url="";

 public ModuleDescriptorBrief(ModuleDescriptor m) {
   this.id = m.getId();
   this.name = m.getName();
   this.url = m.getUrl();
 }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getUrl() {
    return url;
  }
 

}
