package org.folio.okapi.util;

import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor.Action;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

public class TenantModuleDescriptorMatcher extends TypeSafeMatcher<TenantModuleDescriptor> {

  private final Action action;
  private final ModuleDescriptor md;
  private final ModuleDescriptor from;

  public TenantModuleDescriptorMatcher(Action action, ModuleDescriptor md, ModuleDescriptor from) {
    this.action = action;
    this.md = md;
    this.from = from;
  }

  @Override
  public void describeTo(final Description description) {
    description.appendText("matches TenantModuleDescriptor(action = " + action + ", id = " + md.getId()
        + ", from = " + (from == null ? null : from.getId()) + ")");
  }

  @Override
  public boolean matchesSafely(final TenantModuleDescriptor tm) {
    return action == tm.getAction() && md.getId() == tm.getId()
        && (from == null ? tm.getFrom() == null : tm.getFrom() == from.getId());
  }

  public static TenantModuleDescriptorMatcher enable(ModuleDescriptor md) {
    return upgrade(md, null);
  }

  public static TenantModuleDescriptorMatcher upgrade(ModuleDescriptor md, ModuleDescriptor from) {
    return new TenantModuleDescriptorMatcher(Action.enable, md, from);
  }

  public static TenantModuleDescriptorMatcher disable(ModuleDescriptor md) {
    return new TenantModuleDescriptorMatcher(Action.disable, md, null);
  }

  public static TenantModuleDescriptorMatcher upToDate(ModuleDescriptor md) {
    return new TenantModuleDescriptorMatcher(Action.uptodate, md, null);
  }
}
