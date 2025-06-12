package org.folio.okapi;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.apache.logging.log4j.core.util.Constants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class MainClusterTest {

  @AfterAll
  static void reset() {
    System.clearProperty(Constants.LOG4J_CONTEXT_SELECTOR);
  }

  @Test
  void defaultContextSelector() {
    System.clearProperty(Constants.LOG4J_CONTEXT_SELECTOR);
    MainCluster.setLog4jContextSelector();
    assertThat(System.getProperty(Constants.LOG4J_CONTEXT_SELECTOR),
        is("org.apache.logging.log4j.core.async.AsyncLoggerContextSelector"));
  }

  @Test
  void configuredContextSelector() {
    System.setProperty(Constants.LOG4J_CONTEXT_SELECTOR, "foo.bar");
    MainCluster.setLog4jContextSelector();
    assertThat(System.getProperty(Constants.LOG4J_CONTEXT_SELECTOR), is("foo.bar"));
  }

}
