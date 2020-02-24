package org.folio.okapi.util;

import io.vertx.core.json.Json;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.junit.Test;

import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.Collection;
import java.util.TreeSet;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor;
import org.folio.okapi.common.OkapiLogger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class DepResolutionTest {

  private static final String LS = System.lineSeparator();

  private final Logger logger = OkapiLogger.get();
  private ModuleDescriptor mdA100;
  private ModuleDescriptor mdB;
  private ModuleDescriptor mdC;
  private ModuleDescriptor mdA110;
  private ModuleDescriptor mdA200;
  private ModuleDescriptor mdD100;
  private ModuleDescriptor mdD110;
  private ModuleDescriptor mdE100;
  private ModuleDescriptor mdE110;

  @Before
  public void setUp() {
    InterfaceDescriptor int10 = new InterfaceDescriptor("int", "1.0");
    InterfaceDescriptor[] int10a = {int10};
    InterfaceDescriptor int11 = new InterfaceDescriptor("int", "1.1");
    InterfaceDescriptor[] int11a = {int11};

    mdA100 = new ModuleDescriptor();
    mdA100.setId("moduleA-1.0.0");
    mdA100.setProvides(int10a);

    mdB = new ModuleDescriptor();
    mdB.setId("moduleB-1.0.0");
    mdB.setProvides(int10a);

    mdC = new ModuleDescriptor();
    mdC.setId("moduleC-1.0.0");
    mdC.setProvides(int11a);

    mdA110 = new ModuleDescriptor();
    mdA110.setId("moduleA-1.1.0");
    mdA110.setProvides(int11a);

    mdA200 = new ModuleDescriptor();
    mdA200.setId("moduleA-2.0.0");
    mdA200.setProvides(int11a);

    mdD100 = new ModuleDescriptor();
    mdD100.setId("moduleD-1.0.0");
    mdD100.setOptional(int10a);

    mdD110 = new ModuleDescriptor();
    mdD110.setId("moduleD-1.1.0");
    mdD110.setOptional(int11a);

    mdE100 = new ModuleDescriptor();
    mdE100.setId("moduleE-1.0.0");
    mdE100.setRequires(int10a);

    mdE110 = new ModuleDescriptor();
    mdE110.setId("moduleE-1.1.0");
    mdE110.setRequires(int11a);
  }

  @Test
  public void testLatest(TestContext context) {
    List<ModuleDescriptor> mdl = new LinkedList<>();

    mdl.add(mdA200);
    mdl.add(mdA100);
    mdl.add(mdB);
    mdl.add(mdC);
    mdl.add(mdA110);
    mdl.add(mdE100);

    DepResolution.getLatestProducts(2, mdl);

    context.assertEquals(5, mdl.size());
    context.assertEquals(mdE100, mdl.get(0));
    context.assertEquals(mdC, mdl.get(1));
    context.assertEquals(mdB, mdl.get(2));
    context.assertEquals(mdA200, mdl.get(3));
    context.assertEquals(mdA110, mdl.get(4));

    DepResolution.getLatestProducts(1, mdl);
    context.assertEquals(4, mdl.size());
    context.assertEquals(mdA200, mdl.get(3));
  }

  @Test
  public void testUpgradeUptodate(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdB.getId(), mdB);
    modsAvailable.put(mdC.getId(), mdC);
    modsAvailable.put(mdA110.getId(), mdA110);
    modsAvailable.put(mdE100.getId(), mdE100);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();
    modsEnabled.put(mdA100.getId(), mdA100);

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdA100.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.succeeded());
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(1, tml.size());
      context.assertEquals("moduleA-1.0.0", tml.get(0).getId());
      context.assertEquals("uptodate", tml.get(0).getAction().name());
      context.assertEquals(null, tml.get(0).getFrom());
      async.complete();
    });
  }

  @Test
  public void testUpgradeDifferentProduct(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdB.getId(), mdB);
    modsAvailable.put(mdC.getId(), mdC);
    modsAvailable.put(mdA110.getId(), mdA110);
    modsAvailable.put(mdE100.getId(), mdE100);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();
    modsEnabled.put(mdA100.getId(), mdA100);

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdB.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.succeeded());
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      context.assertEquals("moduleA-1.0.0", tml.get(0).getId());
      context.assertEquals("disable", tml.get(0).getAction().name());
      context.assertEquals(null, tml.get(0).getFrom());
      context.assertEquals("moduleB-1.0.0", tml.get(1).getId());
      context.assertEquals("enable", tml.get(1).getAction().name());
      context.assertEquals(null, tml.get(1).getFrom());
      async.complete();
    });
  }

  @Test
  public void testUpgradeSameProduct(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdB.getId(), mdB);
    modsAvailable.put(mdC.getId(), mdC);
    modsAvailable.put(mdA110.getId(), mdA110);
    modsAvailable.put(mdD100.getId(), mdD100);
    modsAvailable.put(mdE100.getId(), mdE100);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();
    modsEnabled.put(mdA100.getId(), mdA100);

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdA110.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.succeeded());
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(1, tml.size());
      context.assertEquals("moduleA-1.1.0", tml.get(0).getId());
      context.assertEquals("moduleA-1.0.0", tml.get(0).getFrom());
      context.assertEquals("enable", tml.get(0).getAction().name());
      async.complete();
    });
  }

  @Test
  public void testUpgradeWithRequires(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdB.getId(), mdB);
    modsAvailable.put(mdC.getId(), mdC);
    modsAvailable.put(mdA110.getId(), mdA110);
    modsAvailable.put(mdD100.getId(), mdD100);
    modsAvailable.put(mdE100.getId(), mdE100);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();
    modsEnabled.put(mdA100.getId(), mdA100);

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdE100.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.succeeded());
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(1, tml.size());
      context.assertEquals("moduleE-1.0.0", tml.get(0).getId());
      context.assertEquals(null, tml.get(0).getFrom());
      context.assertEquals("enable", tml.get(0).getAction().name());
      async.complete();
    });
  }

  // install optional with no provided ingerface enabled
  @Test
  public void testInstallOptional1(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdA110.getId(), mdA110);
    modsAvailable.put(mdD100.getId(), mdD100);
    modsAvailable.put(mdD110.getId(), mdD110);
    modsAvailable.put(mdE100.getId(), mdE100);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdD100.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.succeeded());
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(1, tml.size());
      context.assertEquals("moduleD-1.0.0", tml.get(0).getId());
      context.assertEquals(null, tml.get(0).getFrom());
      context.assertEquals("enable", tml.get(0).getAction().name());
      async.complete();
    });
  }

  // install optional with a matched interface provided
  @Test
  public void testInstallOptional2(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdA110.getId(), mdA110);
    modsAvailable.put(mdD100.getId(), mdD100);
    modsAvailable.put(mdE100.getId(), mdE100);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();
    modsEnabled.put(mdA100.getId(), mdA100);

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdD100.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.succeeded());
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(1, tml.size());
      context.assertEquals("moduleD-1.0.0", tml.get(0).getId());
      context.assertEquals(null, tml.get(0).getFrom());
      context.assertEquals("enable", tml.get(0).getAction().name());
      async.complete();
    });
  }

  // install optional with existing interface that is too low (error)
  @Test
  public void testInstallOptionalFail(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdD100.getId(), mdD100);
    modsAvailable.put(mdD110.getId(), mdD110);
    modsAvailable.put(mdE100.getId(), mdE100);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();
    modsEnabled.put(mdA100.getId(), mdA100);

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdD110.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.failed());
      context.assertEquals("enable moduleD-1.1.0 failed: interface int required by module moduleD-1.1.0 not found", res.cause().getMessage());
      async.complete();
    });
  }

  // install optional with existing interface that needs upgrading
  @Test
  public void testInstallOptionalExistingModule(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdA110.getId(), mdA110);
    modsAvailable.put(mdD100.getId(), mdD100);
    modsAvailable.put(mdD110.getId(), mdD110);
    modsAvailable.put(mdE100.getId(), mdE100);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();
    modsEnabled.put(mdA100.getId(), mdA100);

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdD110.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.succeeded());
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      context.assertEquals("moduleA-1.1.0", tml.get(0).getId());
      context.assertEquals("moduleA-1.0.0", tml.get(0).getFrom());
      context.assertEquals("enable", tml.get(0).getAction().name());
      context.assertEquals("moduleD-1.1.0", tml.get(1).getId());
      context.assertEquals(null, tml.get(1).getFrom());
      context.assertEquals("enable", tml.get(1).getAction().name());
      async.complete();
    });
  }

  // upgrade base dependency which is still compatible with optional interface
  @Test
  public void testInstallOptionalExistingModule2(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdA110.getId(), mdA110);
    modsAvailable.put(mdD100.getId(), mdD100);
    modsAvailable.put(mdD110.getId(), mdD110);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();
    modsEnabled.put(mdA100.getId(), mdA100);
    modsEnabled.put(mdD100.getId(), mdD100);

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdA110.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.succeeded());
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(1, tml.size());
      context.assertEquals("moduleA-1.1.0", tml.get(0).getId());
      context.assertEquals("moduleA-1.0.0", tml.get(0).getFrom());
      context.assertEquals("enable", tml.get(0).getAction().name());
      async.complete();
    });
  }

  // upgrade optional dependency which require upgrading base dependency
  @Test
  public void testInstallOptionalExistingModule3(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdA110.getId(), mdA110);
    modsAvailable.put(mdD100.getId(), mdD100);
    modsAvailable.put(mdD110.getId(), mdD110);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();
    modsEnabled.put(mdA100.getId(), mdA100);
    modsEnabled.put(mdD100.getId(), mdD100);

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdD110.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.succeeded());
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      context.assertEquals("moduleA-1.1.0", tml.get(0).getId());
      context.assertEquals("moduleA-1.0.0", tml.get(0).getFrom());
      context.assertEquals("enable", tml.get(0).getAction().name());
      context.assertEquals("moduleD-1.1.0", tml.get(1).getId());
      context.assertEquals("moduleD-1.0.0", tml.get(1).getFrom());
      context.assertEquals("enable", tml.get(1).getAction().name());
      async.complete();
    });
  }

  // install optional with existing interface that needs upgrading, but
  // there are multiple modules providing same interface
  @Test
  public void testInstallOptionalExistingModuleFail(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdA110.getId(), mdA110);
    modsAvailable.put(mdB.getId(), mdB);
    modsAvailable.put(mdC.getId(), mdC);
    modsAvailable.put(mdD100.getId(), mdD100);
    modsAvailable.put(mdD110.getId(), mdD110);
    modsAvailable.put(mdE100.getId(), mdE100);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();
    modsEnabled.put(mdA100.getId(), mdA100);

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdD110.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.failed());
      context.assertEquals(
        "enable moduleD-1.1.0 failed: interface int required by module moduleD-1.1.0 is provided by multiple products: moduleA, moduleC"
        , res.cause().getMessage());
      async.complete();
    });
  }

  @Test
  public void testInstallNew1(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdB.getId(), mdB);
    modsAvailable.put(mdC.getId(), mdC);
    modsAvailable.put(mdA110.getId(), mdA110);
    modsAvailable.put(mdE100.getId(), mdE100);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdE100.getId());
    tml.add(tm);
    TenantModuleDescriptor tm1 = new TenantModuleDescriptor();
    tm1.setAction(TenantModuleDescriptor.Action.enable);
    tm1.setId(mdA100.getId());
    tml.add(tm1);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.succeeded());
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      context.assertEquals("moduleA-1.0.0", tml.get(0).getId());
      context.assertEquals(null, tml.get(0).getFrom());
      context.assertEquals("enable", tml.get(0).getAction().name());
      context.assertEquals("moduleE-1.0.0", tml.get(1).getId());
      context.assertEquals(null, tml.get(1).getFrom());
      context.assertEquals("enable", tml.get(1).getAction().name());
      async.complete();
    });
  }

  @Test
  public void testInstallNew2(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdB.getId(), mdB);
    modsAvailable.put(mdC.getId(), mdC);
    modsAvailable.put(mdA110.getId(), mdA110);
    modsAvailable.put(mdE100.getId(), mdE100);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdE100.getId());
    tml.add(tm);
    TenantModuleDescriptor tm1 = new TenantModuleDescriptor();
    tm1.setAction(TenantModuleDescriptor.Action.enable);
    tm1.setId(mdB.getId());
    tml.add(tm1);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.succeeded());
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      context.assertEquals("moduleB-1.0.0", tml.get(0).getId());
      context.assertEquals(null, tml.get(0).getFrom());
      context.assertEquals("enable", tml.get(0).getAction().name());
      context.assertEquals("moduleE-1.0.0", tml.get(1).getId());
      context.assertEquals(null, tml.get(1).getFrom());
      context.assertEquals("enable", tml.get(1).getAction().name());
      async.complete();
    });
  }

  @Test
  public void testMultipleInterfacesMatch1(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdB.getId(), mdB);
    modsAvailable.put(mdE100.getId(), mdE100);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdE100.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.failed());
      context.assertEquals(
        "enable moduleE-1.0.0 failed: interface int required by module moduleE-1.0.0 is provided by multiple products: moduleA, moduleB"
        , res.cause().getMessage());
      async.complete();
    });
  }

  @Test
  public void testMultipleInterfacesMatch2(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdC.getId(), mdC);
    modsAvailable.put(mdE100.getId(), mdE100);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdE100.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.failed());
      context.assertEquals(
        "enable moduleE-1.0.0 failed: interface int required by module moduleE-1.0.0 is provided by multiple products: moduleA, moduleC"
        , res.cause().getMessage());
      async.complete();
    });
  }

  @Test
  public void testMultipleInterfacesMatchReplaces1(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdB.getId(), mdB);
    modsAvailable.put(mdE100.getId(), mdE100);
    mdB.setReplaces(new String[]{mdA100.getProduct()});
    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdE100.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.succeeded());
      logger.debug("tml result = " + Json.encodePrettily(tml));
      async.complete();
    });
  }

  @Test
  public void testMultipleInterfacesMatchReplaces2(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdB.getId(), mdB);
    modsAvailable.put(mdC.getId(), mdC);
    modsAvailable.put(mdE100.getId(), mdE100);
    mdB.setReplaces(new String[]{mdA100.getProduct()});
    mdC.setReplaces(new String[]{mdB.getProduct()});
    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdE100.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.succeeded());
      logger.debug("tml result = " + Json.encodePrettily(tml));
      async.complete();
    });
  }

  @Test
  public void testRemoveNonEnabled(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.disable);
    tm.setId(mdA100.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.failed());
      async.complete();
    });
  }

  @Test
  public void testRemoveNonExisting(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdB.getId(), mdB);
    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.disable);
    tm.setId(mdA100.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.failed());
      async.complete();
    });
  }

  @Test
  public void testConflict(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdB.getId(), mdB);
    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.conflict);
    tm.setId(mdA100.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.succeeded());
      async.complete();
    });
  }

  @Test
  public void testCheckDependenices() {
    InterfaceDescriptor inu10 = new InterfaceDescriptor("inu", "1.0");
    InterfaceDescriptor[] inu10a = {inu10};

    InterfaceDescriptor int10 = new InterfaceDescriptor("int", "1.0");
    InterfaceDescriptor[] int10a = {int10};

    ModuleDescriptor mdA = new ModuleDescriptor();
    mdA.setId("moduleA-1.0.0");
    mdA.setProvides(int10a);

    ModuleDescriptor mdB = new ModuleDescriptor();
    mdB.setId("moduleB-1.0.0");
    mdB.setRequires(int10a);
    mdB.setProvides(inu10a);

    InterfaceDescriptor int20 = new InterfaceDescriptor("int", "2.0");
    InterfaceDescriptor[] int20a = {int20};

    ModuleDescriptor mdC = new ModuleDescriptor();
    mdC.setId("moduleC-1.0.0");
    mdC.setProvides(int20a);

    ModuleDescriptor mdD = new ModuleDescriptor();
    mdD.setId("moduleD-1.0.0");
    mdD.setRequires(int20a);
    mdD.setProvides(inu10a);

    InterfaceDescriptor int30 = new InterfaceDescriptor("int", "3.0");
    InterfaceDescriptor[] int30a = {int30};

    ModuleDescriptor mdE = new ModuleDescriptor();
    mdE.setId("moduleE-1.0.0");
    mdE.setProvides(int30a);

    {
      Map<String, ModuleDescriptor> available = new HashMap<>();
      available.put(mdA.getId(), mdA);
      available.put(mdB.getId(), mdB);
      available.put(mdC.getId(), mdC);
      available.put(mdD.getId(), mdD);

      Assert.assertEquals("", DepResolution.checkAllDependencies(available));
    }

    {
      Map<String, ModuleDescriptor> available = new HashMap<>();
      available.put(mdB.getId(), mdB);

      Assert.assertEquals("Missing dependency: moduleB-1.0.0 requires int: 1.0",
        DepResolution.checkAllDependencies(available));
    }

    {
      Map<String, ModuleDescriptor> available = new HashMap<>();
      available.put(mdB.getId(), mdB);
      available.put(mdC.getId(), mdC);
      available.put(mdD.getId(), mdD);

      Assert.assertEquals("Incompatible version for module moduleB-1.0.0 interface int. Need 1.0. Have 2.0/moduleC-1.0.0",
        DepResolution.checkAllDependencies(available));

      Collection<ModuleDescriptor> testList = new TreeSet<>();
      testList.add(mdD);
      Assert.assertEquals("", DepResolution.checkDependencies(available, testList));

      available.put(mdE.getId(), mdE);

      testList = new TreeSet<>();
      testList.add(mdB);
      Assert.assertEquals("Incompatible version for module moduleB-1.0.0 interface int. Need 1.0. Have 2.0/moduleC-1.0.0 3.0/moduleE-1.0.0",
        DepResolution.checkDependencies(available, testList));
    }

    {
      Map<String, ModuleDescriptor> available = new HashMap<>();
      available.put(mdC.getId(), mdC);
      available.put(mdD.getId(), mdD);

      Assert.assertEquals("", DepResolution.checkAllDependencies(available));
    }
  }

}
