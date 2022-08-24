package org.folio.okapi.util;

import org.apache.logging.log4j.message.MapMessage;

public class OkapiMapMessage extends MapMessage<OkapiMapMessage, String> {

  /**
   * Create log4j message for Okapi.
   * @param requestid request identifier
   * @param tenantid tenant identifier
   * @param userid user identifier / possibly null
   * @param moduleid module identifier
   * @param message log message
   */
  public OkapiMapMessage(String requestid, String tenantid, String userid,
      String moduleid, String message) {
    put("requestid", emptyIfNull(requestid));
    put("tenantid", emptyIfNull(tenantid));
    put("userid", emptyIfNull(userid));
    put("moduleid", emptyIfNull(moduleid));
    put("message", emptyIfNull(message));
  }

  private String emptyIfNull(String v) {
    return v != null ? v : "";
  }

  protected void appendMap(final StringBuilder sb) {
    sb.append(get("message"));
  }
}
