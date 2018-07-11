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
    my.setLanguage("pl");
    Assert.assertEquals("Nie można przetworzyć żądania", my.getMessage("10003"));
    my.setLanguage("en");
    Assert.assertEquals("Unable to process request", my.getMessage("10003"));
  }
}
