package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;
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
  // no-multi byte sequences allowed below in pattern, so char length = byte length
  private static final String TENANT_PATTERN_STRING = "^[a-z][a-z0-9]{0,30}$";
  private static final Pattern TENANT_PATTERN = Pattern.compile(TENANT_PATTERN_STRING);
  private static final List<String> TENANT_RESERVED = List.of("pg");

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
    if (!TENANT_PATTERN.matcher(id).find()) {
      throw new OkapiError(ErrorType.USER, messages.getMessage("11601", id,
              TENANT_PATTERN_STRING));
    }
    if (TENANT_RESERVED.contains(id)) {
      throw new OkapiError(ErrorType.USER, messages.getMessage("11606", id));
    }
    this.id = id;
  }
}
