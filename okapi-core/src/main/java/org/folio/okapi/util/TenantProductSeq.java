package org.folio.okapi.util;

import java.util.Collection;

import org.folio.okapi.bean.TimerDescriptor;

/**
 * [tenant]_[product]_[seq] (like test_tenant_mod-foo_2) or [product]_[seq] (like mod-foo_2).
 *
 * <p>Used as {@link TimerDescriptor} id.
 */
public class TenantProductSeq {
  private static final String TIMER_ENTRY_SEP = "_";
  private final String tenantId;
  private final String product;
  private final int seq;

  /**
   * Constructor using the three components.
   */
  public TenantProductSeq(String tenantId, String product, int seq) {
    this.tenantId = tenantId;
    this.product = product;
    this.seq = seq;
  }

  /**
   * Like {@link #TenantProductSeq(String tenantProductSeq)} but the parameter
   * tenantId replaces the value from tenantProductSeq String.
   *
   * @param tenantId the replacement value; if null the value from
   *     tenantProductSeq String is taken
   */
  public TenantProductSeq(String tenantId, String tenantProductSeq) {
    int pos2 = tenantProductSeq.lastIndexOf(TIMER_ENTRY_SEP);
    seq = Integer.parseInt(tenantProductSeq.substring(pos2 + 1));
    int pos1 = tenantProductSeq.lastIndexOf(TIMER_ENTRY_SEP, pos2 - 1);
    product = tenantProductSeq.substring(pos1 + 1, pos2);
    if (tenantId != null) {
      this.tenantId = tenantId;
      return;
    }
    if (pos1 == -1) {
      this.tenantId = null;
    } else {
      this.tenantId = tenantProductSeq.substring(0, pos1);
    }
  }

  /**
   * Parse a String [tenant]_[product]_[seq] like test_tenant_mod-foo_2
   * or [product]_[seq] like mod-foo_2.
   */
  public TenantProductSeq(String tenantProductSeq) {
    this(null, tenantProductSeq);
  }

  /**
   * The tenant id.
   */
  public String getTenantId() {
    return tenantId;
  }

  /**
   * The product, like mod-foo.
   */
  public String getProduct() {
    return product;
  }

  /**
   * The timer number in the timer array in the module descriptor, starting with 0.
   */
  public int getSeq() {
    return seq;
  }

  /**
   * Concatenation of the components, like mod-foo_2 or test_tenant_mod-foo_2.
   */
  public String toString() {
    if (tenantId == null) {
      return product + TIMER_ENTRY_SEP + seq;
    } else {
      return tenantId + TIMER_ENTRY_SEP + product + TIMER_ENTRY_SEP + seq;
    }
  }

  /**
   * For each TimerDescriptor alter the id by removing the tenant id from the String.
   *
   * <p>For example test_tenant_mod-foo_2 becomes mod-foo_2.
   */
  public static Collection<TimerDescriptor> stripTenantIdFromTimerId(
      Collection<TimerDescriptor> collection) {

    collection.forEach(timerDescriptor -> {
      var old = new TenantProductSeq(timerDescriptor.getId());
      timerDescriptor.setId(old.getProduct() + TIMER_ENTRY_SEP + old.getSeq());
    });
    return collection;
  }
}
