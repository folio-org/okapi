package org.folio.okapi.common;

import io.vertx.core.json.JsonObject;
import org.junit.Assert;
import org.junit.Test;

public class ConfigTest {
  private static final String VAR_NAME = "OkapiConfigTest1";
  private static final String VAR_NAME2 = "OkapiConfigTest2";

  @Test
  public void testConfig() {
    JsonObject conf = new JsonObject();

    System.clearProperty(VAR_NAME);
    Assert.assertNull(System.getProperty(VAR_NAME));

    Assert.assertEquals("123", Config.getSysConf(VAR_NAME, "123", conf));
    Assert.assertEquals(null, Config.getSysConf(VAR_NAME, null, conf));

    conf.put(VAR_NAME, "124");
    Assert.assertEquals("124", Config.getSysConf(VAR_NAME, "123", conf));

    System.setProperty(VAR_NAME, "129");
    Assert.assertEquals("129", Config.getSysConf(VAR_NAME, "123", conf));

    System.setProperty(VAR_NAME, "");
    Assert.assertEquals("124", Config.getSysConf(VAR_NAME, "123", conf));
  }

  private void assert2(String prop1, String prop2, String json1, String json2, String expected) {
    final String key1 = VAR_NAME;
    final String key2 = VAR_NAME2;
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

    System.clearProperty(VAR_NAME);
    Assert.assertNull(System.getProperty(VAR_NAME));
    Assert.assertEquals(true, Config.getSysConfBoolean(VAR_NAME, true, conf));
    Assert.assertEquals(null, Config.getSysConfBoolean(VAR_NAME, null, conf));

    conf.put(VAR_NAME, false);
    Assert.assertEquals(false, Config.getSysConfBoolean(VAR_NAME, true, conf));

    conf.put(VAR_NAME, "xx");
    boolean thrown = false;
    try {
      Config.getSysConfBoolean(VAR_NAME, true, conf);
    } catch (ClassCastException ex) {
      thrown = true;
    }
    Assert.assertTrue(thrown);

    System.setProperty(VAR_NAME, "xx"); // treated as false
    Assert.assertEquals(false, Config.getSysConfBoolean(VAR_NAME, null, conf));

    System.setProperty(VAR_NAME, "true");
    Assert.assertEquals(true, Config.getSysConfBoolean(VAR_NAME, false, conf));

    System.setProperty(VAR_NAME, "false");
    Assert.assertEquals(false, Config.getSysConfBoolean(VAR_NAME, true, conf));

    conf.put(VAR_NAME, false);
    System.setProperty(VAR_NAME, "");
    Assert.assertEquals(false, Config.getSysConfBoolean(VAR_NAME, true, conf));

    conf.remove(VAR_NAME);
    Assert.assertEquals(true, Config.getSysConfBoolean(VAR_NAME, true, conf));
    Assert.assertEquals(null, Config.getSysConfBoolean(VAR_NAME, null, conf));
  }

  @Test
  public void testInteger() {
    JsonObject conf = new JsonObject();

    System.clearProperty(VAR_NAME);
    Assert.assertNull(System.getProperty(VAR_NAME));
    Assert.assertEquals((Integer) 42, Config.getSysConfInteger(VAR_NAME, 42, conf));
    Assert.assertEquals(null, Config.getSysConfInteger(VAR_NAME, null, conf));

    conf.put(VAR_NAME, "xx");
    boolean thrown = false;
    try {
      Config.getSysConfInteger(VAR_NAME, null, conf);
    } catch (ClassCastException ex) {
      thrown = true;
    }
    Assert.assertTrue(thrown);

    conf.put(VAR_NAME, 41);
    Assert.assertEquals((Integer) 41, Config.getSysConfInteger(VAR_NAME, null, conf));

    System.setProperty(VAR_NAME, "42");
    Assert.assertEquals((Integer) 42, Config.getSysConfInteger(VAR_NAME, null, conf));

    System.setProperty(VAR_NAME, "");
    Assert.assertTrue(System.getProperty(VAR_NAME).isEmpty());
    Assert.assertEquals((Integer) 41, Config.getSysConfInteger(VAR_NAME, null, conf));
  }
}
