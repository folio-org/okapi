package org.folio.okapi.util;

import io.vertx.core.Future;
import java.util.regex.Pattern;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.Messages;

/**
 * Validate a tenant ID to match ^[a-z][a-z0-9]{0,30}$ as required by
 * <a href="https://folio-org.atlassian.net/wiki/display/TC/ADR-000002+-+Tenant+Id+and+Module+Name+Restrictions">
 * https://folio-org.atlassian.net/wiki/display/TC/ADR-000002+-+Tenant+Id+and+Module+Name+Restrictions</a>
 * Technical Council decision.
 */
public final class TenantValidator {
  /** Maximum length of a tenant ID. */
  public static final int MAX_LENGTH = 31;

  // multi-byte sequences forbidden in pattern, so char length = byte length
  @SuppressWarnings("java:S5867")  // we want to forbid Unicode characters, therefore we
  // suppress warning "Unicode-aware versions of character classes should be preferred"
  private static final String TENANT_PATTERN_STRING = "^[a-z][a-z0-9]{0,30}$";
  private static final Pattern TENANT_PATTERN = Pattern.compile(TENANT_PATTERN_STRING);
  private static final Pattern STARTS_WITH_DIGIT = Pattern.compile("^\\d");
  private static final Messages MESSAGES = Messages.getInstance();

  private TenantValidator() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  /**
   * Validate tenantId against ^[a-z][a-z0-9]{0,30}$ and return a failed Future with
   * clear violation explanation message on validation failure.
   */
  public static Future<Void> validate(String tenantId) {
    if (TENANT_PATTERN.matcher(tenantId).matches()) {
      return Future.succeededFuture();
    }

    String message = "11601";

    if (tenantId.contains("_")) {
      message = "11609";
    } else if (tenantId.contains("-")) {
      message = "11610";
    } else if (tenantId.length() > MAX_LENGTH) {
      message = "11611";
    } else if (STARTS_WITH_DIGIT.matcher(tenantId).matches()) {
      message = "11612";
    }

    return Future.failedFuture(new OkapiError(ErrorType.USER,
        MESSAGES.getMessage(message, tenantId)));
  }
}
