package org.folio.okapi.common;

import org.junit.Assert;
import org.junit.Test;

public class MessagesTest {

  @Test
  public void test1() {
    Messages my = Messages.getInstance();
    my.setLanguage("pl");
    Assert.assertEquals("Nie można przetworzyć żądania", my.getMessage("10003"));
    Assert.assertEquals("Only English", my.getMessage("10006"));
    my.setLanguage("en");
    Assert.assertEquals("During verticle deployment, init failed, exiting....... foo", my.getMessage("10000", "foo"));
    Assert.assertEquals("During verticle deployment, init failed, exiting....... {0}", my.getMessage("10000"));
    Assert.assertEquals("Unable to process request", my.getMessage("10003"));
    Assert.assertEquals("Unable to process request", my.getMessage("10003", "foo"));
    Assert.assertEquals("Error message not found: en 10005", my.getMessage("10005", "foo"));
    Assert.assertEquals("Error message not found: en 10005", my.getMessage("10005"));
    Assert.assertEquals("Only English", my.getMessage("10006"));
    my.setLanguage("dk");
    Assert.assertEquals("Unable to process request", my.getMessage("10003"));
    Assert.assertEquals("Error message not found: dk 10005", my.getMessage("10005"));
    Assert.assertEquals("Only English", my.getMessage("10006"));
  }
}
