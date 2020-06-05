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
  }

  private final static String KEY1 = "foo-bar92304231";
  private final static String KEY2 = "foo-bar92304232";

  private void testTwoKeys(String sys1, String sys2, String json1, String json2, String expected) {
    if (sys1 == null) {
      System.clearProperty(KEY1);
    } else {
      System.setProperty(KEY1, sys1);
    }
    if (sys2 == null) {
      System.clearProperty(KEY2);
    } else {
      System.setProperty(KEY2, sys2);
    }
    JsonObject json = new JsonObject();
    if (json1 != null) {
      json.put(KEY1, json1);
    }
    if (json2 != null) {
      json.put(KEY2, json2);
    }
    Assert.assertEquals(expected, Config.getSysConf(KEY1, KEY2, "someDefault", json));
  }

  @Test
  public void testTwoKeys() {
    testTwoKeys("a",  "b",  "c",  "d",  "a");
    testTwoKeys("",   "b",  "c",  "d",  "b");
    testTwoKeys(null, null, "c",  "d",  "c");
    testTwoKeys(null, "",   null, "d",  "d");
    testTwoKeys("",   "",   "",   "",   "someDefault");
    testTwoKeys(null, null, null, null, "someDefault");
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

    System.setProperty(varName, "xx"); // treated as false
    Assert.assertEquals(false, Config.getSysConfBoolean(varName, null, conf));

    System.setProperty(varName, "true");
    Assert.assertEquals(true, Config.getSysConfBoolean(varName, false, conf));

    System.setProperty(varName, "false");
    Assert.assertEquals(false, Config.getSysConfBoolean(varName, true, conf));

    conf.put(varName, false);
    System.setProperty(varName, "");
    Assert.assertEquals(false, Config.getSysConfBoolean(varName, true, conf));

    conf.remove(varName);
    Assert.assertEquals(true, Config.getSysConfBoolean(varName, true, conf));
    Assert.assertEquals(null, Config.getSysConfBoolean(varName, null, conf));

  }

}
