package org.folio.okapi.common.refreshtoken.client;

import org.folio.okapi.common.refreshtoken.client.impl.LoginClient;
import org.folio.okapi.common.refreshtoken.client.impl.RefreshClient;

/**
 * Exception without stacktrace from {@link LoginClient} or {@link RefreshClient}.
 */
public class ClientException extends RuntimeException {

  public ClientException(String msg) {
    super(msg, null, false, false);
  }
}
