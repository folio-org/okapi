package org.folio.okapi.service.impl;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class StorageTest {
  
  @Test(expected = IllegalArgumentException.class)
  public void test1() {
    Storage s = new Storage(Vertx.vertx(), "foo", new JsonObject());
  }

  public void test2() {
    Vertx vertx = Vertx.vertx();
    Storage s = new Storage(vertx, "inmemory", new JsonObject());
    Assert.assertNotNull(s.getEnvStore());
    Assert.assertNotNull(s.getTenantStore());
    Assert.assertNotNull(s.getDeploymentStore());
    Assert.assertNull(s.getModuleStore());
  }
  
}
