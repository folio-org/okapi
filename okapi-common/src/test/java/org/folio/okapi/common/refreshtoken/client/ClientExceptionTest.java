package org.folio.okapi.common.refreshtoken.client;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

class ClientExceptionTest {

  @Test
  void test() {
    var e = new ClientException("foo");
    assertThat(e.getStackTrace(), arrayWithSize(0));
    assertThat(e.getMessage(), is("foo"));
  }

}
