package org.folio.okapi.util;

import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.junit.Test;

import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor;
import org.folio.okapi.common.OkapiLogger;
import org.junit.Before;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class DepResolutionTest {

  private static final String LS = System.lineSeparator();

  private final Logger logger = OkapiLogger.get();
  private ModuleDescriptor mdA100 = new ModuleDescriptor();
  private ModuleDescriptor mdB = new ModuleDescriptor();
  private ModuleDescriptor mdC = new ModuleDescriptor();
  private ModuleDescriptor mdA110 = new ModuleDescriptor();
  private ModuleDescriptor mdA200 = new ModuleDescriptor();
  private ModuleDescriptor mdE = new ModuleDescriptor();

  @Before
  public void setUp() {
    InterfaceDescriptor int10 = new InterfaceDescriptor("int", "1.0");
    InterfaceDescriptor[] int10a = {int10};
    InterfaceDescriptor int11 = new InterfaceDescriptor("int", "1.1");
    InterfaceDescriptor[] int11a = {int11};
    InterfaceDescriptor int20 = new InterfaceDescriptor("int", "2.0");
    InterfaceDescriptor[] int20a = {int20};

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

    mdE = new ModuleDescriptor();
    mdE.setId("moduleE-1.0.0");
    mdE.setRequires(int10a);
  }

  @Test
  public void testLatest(TestContext context) {
    List<ModuleDescriptor> mdl = new LinkedList<>();

    mdl.add(mdA200);
    mdl.add(mdA100);
    mdl.add(mdB);
    mdl.add(mdC);
    mdl.add(mdA110);
    mdl.add(mdE);

    DepResolution.getLatestProducts(2, mdl);

    context.assertEquals(5, mdl.size());
    context.assertEquals(mdE, mdl.get(0));
    context.assertEquals(mdC, mdl.get(1));
    context.assertEquals(mdB, mdl.get(2));
    context.assertEquals(mdA200, mdl.get(3));
    context.assertEquals(mdA110, mdl.get(4));

    DepResolution.getLatestProducts(1, mdl);
    context.assertEquals(4, mdl.size());
    context.assertEquals(mdA200, mdl.get(3));
  }

  @Test
  public void test1(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdB.getId(), mdB);
    modsAvailable.put(mdC.getId(), mdC);
    modsAvailable.put(mdA110.getId(), mdA110);
    modsAvailable.put(mdE.getId(), mdE);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();
    modsEnabled.put(mdA100.getId(), mdA100);

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdA100.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.succeeded());
      logger.info("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(1, tml.size());
      context.assertEquals("moduleA-1.0.0", tml.get(0).getId());
      context.assertEquals("uptodate", tml.get(0).getAction().name());
      context.assertEquals(null, tml.get(0).getFrom());
      async.complete();
    });
  }

  @Test
  public void test2(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdB.getId(), mdB);
    modsAvailable.put(mdC.getId(), mdC);
    modsAvailable.put(mdA110.getId(), mdA110);
    modsAvailable.put(mdE.getId(), mdE);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();
    modsEnabled.put(mdA100.getId(), mdA100);

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdB.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.succeeded());
      logger.info("tml result = " + Json.encodePrettily(tml));
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
  public void test3(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdB.getId(), mdB);
    modsAvailable.put(mdC.getId(), mdC);
    modsAvailable.put(mdA110.getId(), mdA110);
    modsAvailable.put(mdE.getId(), mdE);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();
    modsEnabled.put(mdA100.getId(), mdA100);

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdA110.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.succeeded());
      logger.info("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(1, tml.size());
      context.assertEquals("moduleA-1.1.0", tml.get(0).getId());
      context.assertEquals("moduleA-1.0.0", tml.get(0).getFrom());
      context.assertEquals("enable", tml.get(0).getAction().name());
      async.complete();
    });
  }

  @Test
  public void test4(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdB.getId(), mdB);
    modsAvailable.put(mdC.getId(), mdC);
    modsAvailable.put(mdA110.getId(), mdA110);
    modsAvailable.put(mdE.getId(), mdE);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();
    modsEnabled.put(mdA100.getId(), mdA100);

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdE.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.succeeded());
      logger.info("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(1, tml.size());
      context.assertEquals("moduleE-1.0.0", tml.get(0).getId());
      context.assertEquals(null, tml.get(0).getFrom());
      context.assertEquals("enable", tml.get(0).getAction().name());
      async.complete();
    });
  }

  @Test
  public void test5(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdB.getId(), mdB);
    modsAvailable.put(mdC.getId(), mdC);
    modsAvailable.put(mdA110.getId(), mdA110);
    modsAvailable.put(mdE.getId(), mdE);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdE.getId());
    tml.add(tm);
    TenantModuleDescriptor tm1 = new TenantModuleDescriptor();
    tm1.setAction(TenantModuleDescriptor.Action.enable);
    tm1.setId(mdA100.getId());
    tml.add(tm1);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.succeeded());
      logger.info("tml result = " + Json.encodePrettily(tml));
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
  public void test6(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdB.getId(), mdB);
    modsAvailable.put(mdC.getId(), mdC);
    modsAvailable.put(mdA110.getId(), mdA110);
    modsAvailable.put(mdE.getId(), mdE);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdE.getId());
    tml.add(tm);
    TenantModuleDescriptor tm1 = new TenantModuleDescriptor();
    tm1.setAction(TenantModuleDescriptor.Action.enable);
    tm1.setId(mdB.getId());
    tml.add(tm1);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.succeeded());
      logger.info("tml result = " + Json.encodePrettily(tml));
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
  public void testMultipleInterfacesMatch(TestContext context) {
    Async async = context.async();

    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdB.getId(), mdB);
    modsAvailable.put(mdC.getId(), mdC);
    modsAvailable.put(mdA110.getId(), mdA110);
    modsAvailable.put(mdE.getId(), mdE);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();

    List<TenantModuleDescriptor> tml = new LinkedList<>();
    TenantModuleDescriptor tm = new TenantModuleDescriptor();
    tm.setAction(TenantModuleDescriptor.Action.enable);
    tm.setId(mdE.getId());
    tml.add(tm);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml, res -> {
      context.assertTrue(res.failed());
      async.complete();
    });
  }

}
