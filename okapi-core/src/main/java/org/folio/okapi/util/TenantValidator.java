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

  // multi-byte sequences forbidden in pattern, so char length = byte length
  private static final String TENANT_PATTERN_STRING = "^[a-z][a-z0-9]{0,30}$";
  private static final Pattern TENANT_PATTERN = Pattern.compile(TENANT_PATTERN_STRING);
  private static final Pattern STARTS_WITH_DIGIT = Pattern.compile("^[0-9]");
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

    if (tenantId.contains("_")) {
      return failure("11609", tenantId);
    }

    if (tenantId.contains("-")) {
      return failure("11610", tenantId);
    }

    if (tenantId.length() > 31) {
      return failure("11611", tenantId);
    }

    if (STARTS_WITH_DIGIT.matcher(tenantId).matches()) {
      return failure("11612", tenantId);
    }

    return failure("11601", tenantId);
  }

  private static Future<Void> failure(String errorMessage, String tenantId) {
    return Future.failedFuture(new OkapiError(ErrorType.USER,
        MESSAGES.getMessage(errorMessage, tenantId)));
  }
}
