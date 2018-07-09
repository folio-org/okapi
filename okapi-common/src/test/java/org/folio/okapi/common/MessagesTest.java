package org.folio.okapi.common;

import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author tdolega
 */
public class MessagesTest {

  @Test
  public void test1() {
    Messages my = Messages.getInstance();
    Assert.assertEquals("Invalid parameters:", my.getMessage("en", "10004"));
  }
}
