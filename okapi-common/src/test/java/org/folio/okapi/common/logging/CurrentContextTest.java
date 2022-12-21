package org.folio.okapi.common.logging;

import io.vertx.core.Vertx;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * OKAPI-1149 test case.
 *
 * <p>Test with
 * <pre><code>
 *  cd okapi-common
 *  mvn -Dtest=CurrentContextTest test
 * </code></pre>
 * It does not test in junit terms. See the log!
 */
public class CurrentContextTest {

  @Test
  public void checkCurrentContext() {
    assertThat(Vertx.currentContext(), is(nullValue()));
  }
}
