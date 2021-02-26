package org.folio.okapi.common;

import io.vertx.core.json.JsonObject;
import org.junit.Assert;
import org.junit.Test;

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

  private void assert2(String prop1, String prop2, String json1, String json2, String expected) {
    final String key1 = "foo-bar92304231";
    final String key2 = "foo-bar92304232";
    JsonObject conf = new JsonObject();
    if (json1 != null) {
      conf.put(key1, json1);
    }
    if (json2 != null) {
      conf.put(key2, json2);
    }
    if (prop1 == null) {
      System.clearProperty(key1);
    } else {
      System.setProperty(key1, prop1);
    }
    if (prop2 == null) {
      System.clearProperty(key2);
    } else {
      System.setProperty(key2, prop2);
    }
    Assert.assertEquals(expected, Config.getSysConf(key1, key2, "de", conf));
  }

  @Test
  public void testConfig2Keys() {
    assert2(null, null, null, null, "de");
    assert2("", "", null, null, "de");
    assert2(null, null, null, "j2", "j2");
    assert2(null, null, "j1", "j2", "j1");
    assert2(null, "p2", "j1", "j2", "p2");
    assert2("", "p2", "j1", "j2", "p2");
    assert2("p1", "p2", "j1", "j2", "p1");
    assert2("p1", null, null, null, "p1");
    assert2("p1", "", "", "", "p1");
  }

  @Test
  public void testBoolean() {
    JsonObject conf = new JsonObject();
    final String varName = "foo-bar92304238";

    Assert.assertEquals(true, Config.getSysConfBoolean(varName, true, conf));
    Assert.assertEquals(null, Config.getSysConfBoolean(varName, null, conf));

    conf.put(varName, false);
    Assert.assertEquals(false, Config.getSysConfBoolean(varName, true, conf));

    conf.put(varName, "xx");
    boolean thrown = false;
    try {
      Config.getSysConfBoolean(varName, true, conf);
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

  @Test
  public void testInteger() {
    JsonObject conf = new JsonObject();
    final String varName = "foo-bar92304239";

    Assert.assertEquals((Integer) 42, Config.getSysConfInteger(varName, 42, conf));
    Assert.assertEquals(null, Config.getSysConfInteger(varName, null, conf));

    conf.put(varName, "xx");
    boolean thrown = false;
    try {
      Config.getSysConfInteger(varName, null, conf);
    } catch (ClassCastException ex) {
      thrown = true;
    }
    Assert.assertTrue(thrown);

    conf.put(varName, 41);
    Assert.assertEquals((Integer) 41, Config.getSysConfInteger(varName, null, conf));

    System.setProperty(varName, "");
    Assert.assertEquals((Integer) 41, Config.getSysConfInteger(varName, null, conf));

    System.setProperty(varName, "42");
    Assert.assertEquals((Integer) 42, Config.getSysConfInteger(varName, null, conf));
  }
}
