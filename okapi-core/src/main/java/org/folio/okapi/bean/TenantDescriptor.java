package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.regex.Pattern;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.Messages;
import org.folio.okapi.util.OkapiError;

/**
 * Description of a Tenant. This is what gets POSTed to "/_/proxy/tenants" to
 * create new tenants, etc. Carries an id, and some human-readable info about
 * the tenant.
 *
 */

@JsonInclude(Include.NON_NULL)
public class TenantDescriptor {
  private static final String TENANT_PATTERN_STRING = "^[a-z][a-z0-9]*(_[0-9]+)*$";
  private static final Pattern TENANT_PATTERN = Pattern.compile(TENANT_PATTERN_STRING);
  private static final String TENANT_RESERVED = "pg";
  private static final int TENANT_MAX_LENGTH = 31;

  private String id;
  private String name;
  private String description;
  private static final Messages messages = Messages.getInstance();

  public void setName(String name) {
    this.name = name;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getId() {
    return id;
  }

  /**
   * Set tenant id.
   * @param id tenant identifier
   */
  public void setId(String id) {
    if (id == null || id.isEmpty()) {
      throw new OkapiError(ErrorType.USER, messages.getMessage("11600"));
    }
    if (id.length() > TENANT_MAX_LENGTH) {
      throw new OkapiError(ErrorType.USER, messages.getMessage("11606", id,
              TENANT_MAX_LENGTH));
    }
    if (!TENANT_PATTERN.matcher(id).find()) {
      throw new OkapiError(ErrorType.USER, messages.getMessage("11601", id,
              TENANT_PATTERN_STRING));
    }
    if (TENANT_RESERVED.equals(id)) {
      throw new OkapiError(ErrorType.USER, messages.getMessage("11609", id));
    }
    this.id = id;
  }
}
