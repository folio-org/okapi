package org.folio.okapi.common;
import io.vertx.core.json.JsonObject;
import org.junit.Test;
import org.junit.Assert;

public class ConfigTest {
  @Test
  public void testConfig() {
    JsonObject conf = new JsonObject();
    final String varName = "foo-bar92304239";

    Assert.assertEquals("123", Config.getSysConf(varName, "123", conf));
    Assert.assertEquals(null, Config.getSysConf(varName, null, conf));

    conf.put(varName, "124");
    Assert.assertEquals("124", Config.getSysConf(varName, "123", conf));

    System.setProperty(varName, "129");
    Assert.assertEquals("129", Config.getSysConf(varName, "123", conf));

    System.setProperty(varName, "");
    Assert.assertEquals("124", Config.getSysConf(varName, "123", conf));

    System.setProperty(varName, "");
    Assert.assertEquals("", Config.getSysConf(varName, "", conf));
  }

  @Test
  public void testBoolean() {
    JsonObject conf = new JsonObject();
    final String varName = "foo-bar92304238";

    Assert.assertEquals(true, Config.getSysConfBoolean(varName, true, conf));
    Assert.assertEquals(null, Config.getSysConfBoolean(varName, null, conf));

    conf.put(varName, false);
    Assert.assertEquals(false, Config.getSysConfBoolean(varName, true, conf));

    boolean thrown = false;
    try {
      conf.put(varName, "xx");
      Assert.assertEquals(false, Config.getSysConfBoolean(varName, true, conf));
    } catch (ClassCastException ex) {
      thrown = true;
    }
    Assert.assertTrue(thrown);

    conf.remove(varName);
    thrown = false;
    try {
      System.setProperty(varName, "xx");
      Assert.assertEquals(null, Config.getSysConfBoolean(varName, null, conf));
    } catch (ClassCastException ex) {
      thrown = true;
    }
    Assert.assertTrue(thrown);

    conf.put(varName, false);

    System.setProperty(varName, "true");
    Assert.assertEquals(true, Config.getSysConfBoolean(varName, false, conf));

    System.setProperty(varName, "");
    Assert.assertEquals(false, Config.getSysConfBoolean(varName, true, conf));

    conf.remove(varName);
    System.setProperty(varName, "");
    Assert.assertEquals(true, Config.getSysConfBoolean(varName, true, conf));
    Assert.assertEquals(null, Config.getSysConfBoolean(varName, null, conf));

  }
  
}
