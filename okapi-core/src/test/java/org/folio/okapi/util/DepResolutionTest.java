package org.folio.okapi.util;

import io.vertx.core.json.Json;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.TreeSet;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor;
import org.folio.okapi.bean.TenantModuleDescriptor.Action;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.testing.UtilityClassTester;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class DepResolutionTest {

  private final Logger logger = OkapiLogger.get();
  private ModuleDescriptor mdA100;
  private ModuleDescriptor mdB;
  private ModuleDescriptor mdC;
  private ModuleDescriptor mdA110;
  private ModuleDescriptor mdA200;
  private ModuleDescriptor mdD100;
  private ModuleDescriptor mdD110;
  private ModuleDescriptor mdD200;
  private ModuleDescriptor mdDA200;
  private ModuleDescriptor mdE100;
  private ModuleDescriptor mdE110;
  private ModuleDescriptor mdE200;
  private ModuleDescriptor st100;
  private ModuleDescriptor st101;
  private ModuleDescriptor ot100;
  private ModuleDescriptor ot101;

  @Test
  public void isUtilityClass() {
    UtilityClassTester.assertUtilityClass(DepResolution.class);
  }

  private static Map<String, ModuleDescriptor> map(ModuleDescriptor... array) {
    Map<String, ModuleDescriptor> map = new HashMap<>();
    for (ModuleDescriptor md : array) {
      map.put(md.getId(), md);
    }
    return map;
  }

  private static List<TenantModuleDescriptor> createList(Action action, boolean product,
                                                         ModuleDescriptor... array) {
    List<TenantModuleDescriptor> list = new LinkedList<>();
    for (ModuleDescriptor md : array) {
      TenantModuleDescriptor tmd = new TenantModuleDescriptor();
      tmd.setAction(action);
      if (product) {
        tmd.setId(md.getProduct());
      } else {
        tmd.setId(md.getId());
      }
      list.add(tmd);
    }
    return list;
  }

  private static List<TenantModuleDescriptor> createList(Action action, ModuleDescriptor... array) {
    return createList(action, false, array);
  }

  private static List<TenantModuleDescriptor> enableList(ModuleDescriptor... array) {
    return createList(Action.enable, array);
  }

  static void assertAction(TenantModuleDescriptor tm, Action action, ModuleDescriptor id, ModuleDescriptor from) {
    Assert.assertEquals(action, tm.getAction());
    Assert.assertEquals(id.getId(), tm.getId());
    Assert.assertEquals(from != null ? from.getId() : null, tm.getFrom());
  }

  static void assertEnable(TenantModuleDescriptor tm, ModuleDescriptor id) {
    assertAction(tm, Action.enable, id, null);
  }

  static void assertUpgrade(TenantModuleDescriptor tm, ModuleDescriptor id, ModuleDescriptor from) {
    assertAction(tm, Action.enable, id, from);
  }

  static void assertDisable(TenantModuleDescriptor tm, ModuleDescriptor id) {
    assertAction(tm, Action.disable, id, null);
  }

  static void assertUpToDate(TenantModuleDescriptor tm, ModuleDescriptor id) {
    assertAction(tm, Action.uptodate, id, null);
  }

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
    mdA200.setProvides(int20a);

    mdD100 = new ModuleDescriptor();
    mdD100.setId("moduleD-1.0.0");
    mdD100.setOptional(int10a);

    mdD110 = new ModuleDescriptor();
    mdD110.setId("moduleD-1.1.0");
    mdD110.setOptional(int11a);

    mdD200 = new ModuleDescriptor();
    mdD200.setId("moduleD-2.0.0");
    mdD200.setOptional(int20a);

    mdDA200 = new ModuleDescriptor();
    mdDA200.setId("moduleDA-2.0.0");
    mdDA200.setOptional(int20a);
    mdDA200.setRequires(new InterfaceDescriptor[]{new InterfaceDescriptor("unknown-interface", "2.0")});

    mdE100 = new ModuleDescriptor();
    mdE100.setId("moduleE-1.0.0");
    mdE100.setRequires(int10a);

    mdE110 = new ModuleDescriptor();
    mdE110.setId("moduleE-1.1.0");
    mdE110.setRequires(int11a);

    mdE200 = new ModuleDescriptor();
    mdE200.setId("moduleE-2.0.0");
    mdE200.setRequires(int20a);

    st100 = new ModuleDescriptor();
    st100.setId("st-1.0.0");
    st100.setProvides(int10a);

    st101 = new ModuleDescriptor();
    st101.setId("st-1.0.1");
    st101.setProvides(int20a);

    ot100 = new ModuleDescriptor();
    ot100.setId("ot-1.0.0");
    ot100.setRequires(int10a);

    ot101 = new ModuleDescriptor();
    ot101.setId("ot-1.0.1");
    ot101.setRequires(int20a);
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
  public void testUpgradeUpToDate(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdA100);
    DepResolution.installSimulate(map(mdA100, mdB, mdC, mdA110, mdE110), map(mdA100), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
          logger.debug("tml result = " + Json.encodePrettily(tml));
          context.assertEquals(1, tml.size());
          assertUpToDate(tml.get(0), mdA100);
        }));
  }

  @Test
  public void testUpgradeDifferentProduct(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdB);
    DepResolution.installSimulate(map(mdA100, mdB, mdC, mdA110, mdE100), map(mdA100), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
          logger.debug("tml result = " + Json.encodePrettily(tml));
          context.assertEquals(2, tml.size());
          assertDisable(tml.get(0), mdA100);
          assertEnable(tml.get(1), mdB);
        }));
  }

  @Test
  public void testUpgradeSameProduct(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdA110);
    DepResolution.installSimulate(map(mdA100, mdB, mdC, mdA110, mdD100, mdE100), map(mdA100), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(1, tml.size());
      assertUpgrade(tml.get(0), mdA110, mdA100);
    }));
  }

  @Test
  public void testUpgradeWithRequires(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdE100);
    DepResolution.installSimulate(map(mdA100, mdB, mdC, mdA110, mdD100, mdE100), map(mdA100), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(1, tml.size());
      assertEnable(tml.get(0), mdE100);
    }));
  }

  // install optional with no provided interface enabled
  @Test
  public void testInstallOptional1(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdD100);
    DepResolution.installSimulate(map(mdA100, mdA110, mdD100, mdD110, mdE100), map(), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(1, tml.size());
      assertEnable(tml.get(0), mdD100);
    }));
  }

  // install optional with a matched interface provided
  @Test
  public void testInstallOptional2(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdD100);
    DepResolution.installSimulate(map(mdA100, mdA110, mdD100, mdE100), map(mdA100), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(1, tml.size());
      assertEnable(tml.get(0), mdD100);
    }));
  }

  // install optional with existing interface that is too low (error)
  @Test
  public void testInstallOptionalFail(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdD110);
    DepResolution.installSimulate(map(mdA100, mdD100, mdD110, mdE100), map(mdA100), tml)
        .onComplete(context.asyncAssertFailure(cause -> {
      context.assertEquals("interface int required by module moduleD-1.1.0 not found", cause.getMessage());
    }));
  }

  // install optional with existing interface that needs upgrading
  @Test
  public void testInstallMinorLeafOptional(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdD110);
    DepResolution.installSimulate(map(mdA100, mdA110, mdD100, mdD110, mdE100), map(mdA100), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      assertUpgrade(tml.get(0), mdA110, mdA100);
      assertEnable(tml.get(1), mdD110);
    }));
  }

  // upgrade base dependency which is still compatible with optional interface
  @Test
  public void testInstallMinorBaseOptional(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdA110);
    DepResolution.installSimulate(map(mdA100, mdA110, mdD100, mdD110), map(mdA100, mdD100), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(1, tml.size());
      assertUpgrade(tml.get(0), mdA110, mdA100);
    }));
  }

  // upgrade optional dependency which require upgrading base dependency
  @Test
  public void testInstallMinorLeafOptional2(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdD110);
    DepResolution.installSimulate(map(mdA100, mdA110, mdD100, mdD110), map(mdA100, mdD100), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      assertUpgrade(tml.get(0), mdA110, mdA100);
      assertUpgrade(tml.get(1), mdD110, mdD100);
    }));
  }

  // upgrade base dependency which is a major interface bump to optional interface
  @Test
  public void testInstallMajorBaseOptional(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdA200);
    DepResolution.installSimulate(map(mdA100, mdA110, mdA200, mdD100, mdD110, mdD200, mdDA200),
        map(mdA100, mdD100), tml).onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      assertUpgrade(tml.get(0), mdA200, mdA100);
      assertUpgrade(tml.get(1), mdD200, mdD100);
    }));
  }

  // upgrade base dependency and pull in module with unknown interface (results in error)
  @Test
  public void testInstallMajorBaseError(TestContext context) {
    ModuleDescriptor mdD200F = new ModuleDescriptor();
    mdD200F.setId(mdD200.getId());
    mdD200F.setOptional(mdD200.getOptional());
    mdD200F.setRequires(new InterfaceDescriptor[]{new InterfaceDescriptor("unknown", "2.0")});

    List<TenantModuleDescriptor> tml = enableList(mdA200);
    DepResolution.installSimulate(map(mdA100, mdA110, mdA200, mdD100, mdD110, mdD200F),
        map(mdA100, mdD100), tml).onComplete(context.asyncAssertFailure(cause ->
        context.assertEquals("Incompatible version for module moduleD-1.0.0 interface int. Need 1.0. Have 2.0/moduleA-2.0.0",
            cause.getMessage())
    ));
  }

  // upgrade optional dependency which require upgrading base dependency
  @Test
  public void testInstallMajorLeafOptional(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdD200);
    DepResolution.installSimulate(map(mdA100, mdA110, mdA200, mdD100, mdD110, mdD200, mdA200),
        map(mdA100, mdD100), tml).onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      assertUpgrade(tml.get(0), mdA200, mdA100);
      assertUpgrade(tml.get(1), mdD200, mdD100);
    }));
  }

  // install optional with existing interface that needs upgrading, but
  // there are multiple modules providing same interface
  @Test
  public void testInstallOptionalExistingModuleFail(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdD110);
    DepResolution.installSimulate(map(mdA100, mdA110, mdB, mdC, mdD100, mdD110, mdE100),
        map(mdA100), tml).onComplete(context.asyncAssertFailure(cause ->
      context.assertEquals(
          "interface int required by module moduleD-1.1.0 is provided by multiple products: moduleA, moduleC",
          cause.getMessage())
    ));
  }

  // upgrade base dependency which is a major interface bump to required interface
  @Test
  public void testInstallMajorBaseRequired(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdA200);
    DepResolution.installSimulate(map(mdA100, mdA200, mdE100, mdE200),
        map(mdA100, mdE100), tml).onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      assertUpgrade(tml.get(0), mdA200, mdA100);
      assertUpgrade(tml.get(1), mdE200, mdE100);
    }));
  }

  // upgrade both dependency which is a major interface bump to required interface
  @Test
  public void testInstallMajorBaseRequired2(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdA200, mdE200);
    DepResolution.installSimulate(map(mdA100, mdA200, mdE100, mdE200),
        map(mdA100, mdE100), tml).onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      assertUpgrade(tml.get(0), mdA200, mdA100);
      assertUpgrade(tml.get(1), mdE200, mdE100);
    }));
  }

  // upgrade both dependency which is a major interface bump to required interface
  @Test
  public void testInstallMajorBaseRequired3(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdE200, mdA200);
    DepResolution.installSimulate(map(mdA100, mdA200, mdE100, mdE200),
        map(mdA100, mdE100), tml).onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      assertUpgrade(tml.get(0), mdA200, mdA100);
      assertUpgrade(tml.get(1), mdE200, mdE100);
    }));
  }

  // upgrade module with major dependency upgrade
  @Test
  public void testInstallMajorLeafRequired(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdE200);
    DepResolution.installSimulate(map(mdA100, mdA200, mdE100, mdE200), map(mdA100, mdE100), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      assertUpgrade(tml.get(0), mdA200, mdA100);
      assertUpgrade(tml.get(1), mdE200, mdE100);
    }));
  }

  @Test
  public void testInstallNew1(TestContext context) {
    Map<String, ModuleDescriptor> modsAvailable = new HashMap<>();
    modsAvailable.put(mdA100.getId(), mdA100);
    modsAvailable.put(mdB.getId(), mdB);
    modsAvailable.put(mdC.getId(), mdC);
    modsAvailable.put(mdA110.getId(), mdA110);
    modsAvailable.put(mdE100.getId(), mdE100);

    Map<String, ModuleDescriptor> modsEnabled = new HashMap<>();

    List<TenantModuleDescriptor> tml = enableList(mdE100, mdA100);

    DepResolution.installSimulate(modsAvailable, modsEnabled, tml)
        .onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      assertEnable(tml.get(0), mdA100);
      assertEnable(tml.get(1), mdE100);
    }));
  }

  @Test
  public void testInstallNew2(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdE100, mdB);
    DepResolution.installSimulate(map(mdA100, mdB, mdC, mdA110, mdE100), map(), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      assertEnable(tml.get(0), mdB);
      assertEnable(tml.get(1), mdE100);
    }));
  }

  @Test
  public void testInstallNewProduct(TestContext context) {
    List<TenantModuleDescriptor> tml = createList(Action.enable, true, mdE100, mdB);
    DepResolution.installSimulate(map(mdA100, mdB, mdC, mdA110, mdE100), map(), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      assertEnable(tml.get(0), mdB);
      assertEnable(tml.get(1), mdE100);
    }));
  }

  @Test
  public void testInstallNewProductNonExisting(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdB);
    DepResolution.installSimulate(map(mdA100), map(), tml)
        .onComplete(context.asyncAssertFailure(cause ->
            context.assertEquals("Module moduleB-1.0.0 not found", cause.getMessage())
        ));
  }

  @Test
  public void testMultipleInterfacesMatch1(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdE100);
    DepResolution.installSimulate(map(mdA100, mdB, mdE100), map(), tml)
        .onComplete(context.asyncAssertFailure(cause ->
            context.assertEquals(
                "interface int required by module moduleE-1.0.0 is provided by multiple products: moduleA, moduleB",
                cause.getMessage())
        ));
  }

  @Test
  public void testMultipleInterfacesMatch2(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdE100);
    DepResolution.installSimulate(map(mdA100, mdC, mdE100), map(), tml)
        .onComplete(context.asyncAssertFailure(cause ->
      context.assertEquals(
          "interface int required by module moduleE-1.0.0 is provided by multiple products: moduleA, moduleC",
          cause.getMessage())
        ));
  }

  @Test
  public void testMultipleInterfacesMatchReplaces1(TestContext context) {
    mdB.setReplaces(new String[]{mdA100.getProduct()});
    List<TenantModuleDescriptor> tml = enableList(mdE100);
    DepResolution.installSimulate(map(mdA100, mdA110, mdB, mdE100), map(), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      assertEnable(tml.get(0), mdB);
      assertEnable(tml.get(1), mdE100);
    }));
  }

  @Test
  public void testMultipleInterfacesMatchReplaces2(TestContext context) {
    mdB.setReplaces(new String[]{mdA100.getProduct()});
    mdC.setReplaces(new String[]{mdB.getProduct()});

    List<TenantModuleDescriptor> tml = enableList(mdE100);
    DepResolution.installSimulate(map(mdA100, mdA110, mdB, mdC, mdE100), map(), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      assertEnable(tml.get(0), mdC);
      assertEnable(tml.get(1), mdE100);
    }));
  }

  @Test
  public void testDisableNonEnabled(TestContext context) {
    List<TenantModuleDescriptor> tml = createList(Action.disable, mdA100);
    DepResolution.installSimulate(map(mdA100), map(), tml)
        .onComplete(context.asyncAssertFailure(cause ->
      context.assertEquals("Module moduleA-1.0.0 not found", cause.getMessage())
        ));
  }

  @Test
  public void testDisableNonExisting(TestContext context) {
    List<TenantModuleDescriptor> tml = createList(Action.disable, mdA100);
    DepResolution.installSimulate(map(mdB), map(), tml)
        .onComplete(context.asyncAssertFailure(cause ->
      context.assertEquals("Module moduleA-1.0.0 not found", cause.getMessage())
    ));
  }

  @Test
  public void testDisable1(TestContext context) {
    List<TenantModuleDescriptor> tml = createList(Action.disable, mdA100);
    DepResolution.installSimulate(map(mdA100, mdA110, mdE100), map(mdA100, mdE100), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      assertDisable(tml.get(0), mdE100);
      assertDisable(tml.get(1), mdA100);
    }));
  }

  @Test
  public void testDisableProduct(TestContext context) {
    List<TenantModuleDescriptor> tml = createList(Action.disable, true, mdA100);
    DepResolution.installSimulate(map(mdA100, mdA110, mdE100), map(mdA100, mdE100), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      assertDisable(tml.get(0), mdE100);
      assertDisable(tml.get(1), mdA100);
    }));
  }

  @Test
  public void testDisable2(TestContext context) {
    List<TenantModuleDescriptor> tml = createList(Action.disable, mdE100, mdA100);
    DepResolution.installSimulate(map(mdA100, mdA110, mdE100), map(mdA100, mdE100), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      assertDisable(tml.get(0), mdE100);
      assertDisable(tml.get(1), mdA100);
    }));
  }

  @Test
  public void testDisable3(TestContext context) {
    List<TenantModuleDescriptor> tml = createList(Action.disable, mdA100, mdE100);
    DepResolution.installSimulate(map(mdA100, mdA110, mdE100), map(mdA100, mdE100), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(2, tml.size());
      assertDisable(tml.get(0), mdE100);
      assertDisable(tml.get(1), mdA100);
    }));
  }

  @Test
  public void testUpToDate(TestContext context) {
    List<TenantModuleDescriptor> tml = createList(Action.uptodate, mdA100);
    DepResolution.installSimulate(map(mdA100), map(mdA100), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
      logger.debug("tml result = " + Json.encodePrettily(tml));
      context.assertEquals(1, tml.size());
      assertUpToDate(tml.get(0), mdA100);
    }));
  }

  @Test
  public void testConflict(TestContext context) {
    List<TenantModuleDescriptor> tml = createList(Action.conflict, mdA100);
    DepResolution.installSimulate(map(mdB), map(), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
      context.assertEquals(1, tml.size());
      assertAction(tml.get(0), Action.conflict, mdA100, null);
    }));
  }

  @Test
  public void testCheckDependencies() {
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

    Assert.assertEquals("", DepResolution.checkAllDependencies(map(mdA, mdB, mdC, mdD)));

    Assert.assertEquals("Missing dependency: moduleB-1.0.0 requires int: 1.0",
        DepResolution.checkAllDependencies(map(mdB)));

    Map<String, ModuleDescriptor> available = map(mdB, mdC, mdD);

    Assert.assertEquals("Incompatible version for module moduleB-1.0.0 interface int. Need 1.0. Have 2.0/moduleC-1.0.0",
        DepResolution.checkAllDependencies(available));

    Collection<ModuleDescriptor> testList = new TreeSet<>();
    testList.add(mdD);
    Assert.assertEquals("", DepResolution.checkDependencies(available.values(), testList));

    available.put(mdE.getId(), mdE);

    testList = new TreeSet<>();
    testList.add(mdB);
    Assert.assertEquals("Incompatible version for module moduleB-1.0.0 interface int. Need 1.0. Have 2.0/moduleC-1.0.0 3.0/moduleE-1.0.0",
        DepResolution.checkDependencies(available.values(), testList));

    Assert.assertEquals("", DepResolution.checkAllDependencies(map(mdC, mdD)));
  }

  @Test
  public void testUpgradeImpossible(TestContext context) {
    Map<String, ModuleDescriptor> modsAvailable = map(st100, st101, ot100, ot101);

    // patch to higher version with impossible combination 1/4
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = enableList(ot100, st101);
      DepResolution.installSimulate(modsAvailable, map(ot100, st100), tml).onComplete(context.asyncAssertFailure(res -> {
        context.assertEquals("Incompatible version for module ot-1.0.0 interface int. Need 1.0. Have 2.0/st-1.0.1", res.getMessage());
        async.complete();
      }));
    }

    // patch to higher version with impossible combination 2/4
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = enableList(st101, ot100);
      DepResolution.installSimulate(modsAvailable, map(ot100, st100), tml).onComplete(context.asyncAssertFailure(res -> {
        context.assertEquals("Incompatible version for module ot-1.0.0 interface int. Need 1.0. Have 2.0/st-1.0.1", res.getMessage());
        async.complete();
      }));
    }

    // patch to higher version with impossible combination 3/4
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = enableList(ot101, st100);
      DepResolution.installSimulate(modsAvailable, map(ot100, st100), tml).onComplete(context.asyncAssertFailure(res -> {
        context.assertEquals("Cannot remove module st-1.0.0 which is explicitly given", res.getMessage());
        async.complete();
      }));
    }

    // patch to higher version with impossible combination 4/4
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = enableList(st100, ot101);
      DepResolution.installSimulate(modsAvailable, map(ot100, st100), tml).onComplete(context.asyncAssertFailure(res -> {
        context.assertEquals("Cannot remove module st-1.0.0 which is explicitly given", res.getMessage());
        async.complete();
      }));
    }
  }

  @Test
  public void testUpgradeOneGiven(TestContext context) {
    Map<String, ModuleDescriptor> modsAvailable = map(st100, st101, ot100, ot101);

    // patch to higher version with ot given
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = enableList(ot101);
      DepResolution.installSimulate(modsAvailable, map(ot100, st100), tml).onComplete(context.asyncAssertSuccess(res -> {
        context.assertEquals(2, tml.size(), Json.encodePrettily(tml));
        assertUpgrade(tml.get(0), st101, st100);
        assertUpgrade(tml.get(1), ot101, ot100);
        async.complete();
      }));
    }

    // patch to higher version with st given
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = enableList(st101);
      DepResolution.installSimulate(modsAvailable, map(ot100, st100), tml).onComplete(context.asyncAssertSuccess(res -> {
        context.assertEquals(2, tml.size(), Json.encodePrettily(tml));
        assertUpgrade(tml.get(0), st101, st100);
        assertUpgrade(tml.get(1), ot101, ot100);
        async.complete();
      }));
    }
  }

  @Test
  public void testUpgradeBothGiven(TestContext context) {
    Map<String, ModuleDescriptor> modsAvailable = map(st100, st101, ot100, ot101);
    // patch to higher version with both given 1/2
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = enableList(ot101, st101);
      DepResolution.installSimulate(modsAvailable, map(ot100, st100), tml).onComplete(context.asyncAssertSuccess(res -> {
        context.assertEquals(2, tml.size(), Json.encodePrettily(tml));
        assertUpgrade(tml.get(0), st101, st100);
        assertUpgrade(tml.get(1), ot101, ot100);
        async.complete();
      }));
    }

    // patch to higher version with both given 2/2
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = enableList(st101, ot101);
      DepResolution.installSimulate(modsAvailable, map(ot100, st100), tml).onComplete(context.asyncAssertSuccess(res -> {
        context.assertEquals(2, tml.size(), Json.encodePrettily(tml));
        assertUpgrade(tml.get(0), st101, st100);
        assertUpgrade(tml.get(1), ot101, ot100);
        async.complete();
      }));
    }
  }
  @Test
  public void testUpgradeWithProduct(TestContext context) {
    Map<String, ModuleDescriptor> modsAvailable = map(st100, st101, ot100, ot101);

    // patch to higher version with products only
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = createList(Action.enable, true, st101, ot100);
      DepResolution.installSimulate(modsAvailable, map(ot100, st100), tml).onComplete(context.asyncAssertSuccess(res -> {
        context.assertEquals(2, tml.size(), Json.encodePrettily(tml));
        assertUpgrade(tml.get(0), st101, st100);
        assertUpgrade(tml.get(1), ot101, ot100);
        async.complete();
      }));
    }

    // patch to higher version with products for ot
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = createList(Action.enable, true, ot100);
      DepResolution.installSimulate(modsAvailable, map(ot100, st100), tml).onComplete(context.asyncAssertSuccess(res -> {
        context.assertEquals(2, tml.size(), Json.encodePrettily(tml));
        assertUpgrade(tml.get(0), st101, st100);
        assertUpgrade(tml.get(1), ot101, ot100);
        async.complete();
      }));
    }

    // patch to higher version with products for st
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = createList(Action.enable, true, st100);
      DepResolution.installSimulate(modsAvailable, map(ot100, st100), tml).onComplete(context.asyncAssertSuccess(res -> {
        context.assertEquals(2, tml.size(), Json.encodePrettily(tml));
        assertUpgrade(tml.get(0), st101, st100);
        assertUpgrade(tml.get(1), ot101, ot100);
        async.complete();
      }));
    }
  }

  @Test
  public void testDowngradeImpossible(TestContext context) {
    Map<String, ModuleDescriptor> modsAvailable = map(st100, st101, ot100, ot101);
    // patch to lower version with impossible combination
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = enableList(ot100, st101);
      DepResolution.installSimulate(modsAvailable, map(ot101, st101), tml).onComplete(context.asyncAssertFailure(res -> {
        context.assertEquals("Cannot remove module st-1.0.1 which is explicitly given", res.getMessage());
        async.complete();
      }));
    }

    // patch to lower version with impossible combination
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = enableList(ot101, st100);
      DepResolution.installSimulate(modsAvailable, map(ot101, st101), tml).onComplete(context.asyncAssertFailure(res -> {
        context.assertEquals("Incompatible version for module ot-1.0.1 interface int. Need 2.0. Have 1.0/st-1.0.0", res.getMessage());
        async.complete();
      }));
    }
  }

  @Test
  public void testDowngradeGiven(TestContext context) {
    Map<String, ModuleDescriptor> modsAvailable = map(st100, st101, ot100, ot101);

    // patch to lower version with both given
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = enableList(ot100, st100);
      DepResolution.installSimulate(modsAvailable, map(ot101, st101), tml).onComplete(context.asyncAssertSuccess(res -> {
        context.assertEquals(2, tml.size(), Json.encodePrettily(tml));
        assertUpgrade(tml.get(0), st100, st101);
        assertUpgrade(tml.get(1), ot100, ot101);
        async.complete();
      }));
    }

    // patch to lower version with ot given
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = enableList(ot100);
      DepResolution.installSimulate(modsAvailable, map(ot101, st101), tml).onComplete(context.asyncAssertSuccess(res -> {
        context.assertEquals(2, tml.size(), Json.encodePrettily(tml));
        assertUpgrade(tml.get(0), st100, st101);
        assertUpgrade(tml.get(1), ot100, ot101);
        async.complete();
      }));
    }

    // patch to lower version with st given
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = enableList(st100);
      DepResolution.installSimulate(modsAvailable, map(ot101, st101), tml).onComplete(context.asyncAssertSuccess(res -> {
        context.assertEquals(2, tml.size(), Json.encodePrettily(tml));
        assertUpgrade(tml.get(0), st100, st101);
        assertUpgrade(tml.get(1), ot100, ot101);
        async.complete();
      }));
    }

  }

  @Test
  public void testInstallConflicting(TestContext context) {
    Map<String, ModuleDescriptor> modsAvailable = map(st100, st101, ot100, ot101);

    // install conflicting versions 1/2
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = enableList(st100, st101);
      DepResolution.installSimulate(modsAvailable, map(), tml).onComplete(context.asyncAssertFailure(res -> {
        context.assertEquals("Cannot remove module st-1.0.0 which is explicitly given", res.getMessage());
        async.complete();
      }));
    }

    // install conflicting versions for st 1/2
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = enableList(st101, st100);
      DepResolution.installSimulate(modsAvailable, map(), tml).onComplete(context.asyncAssertFailure(res -> {
        context.assertEquals("Cannot remove module st-1.0.1 which is explicitly given", res.getMessage());
        async.complete();
      }));
    }

    // install conflicting versions for st 2/2
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = enableList(ot100, ot101);
      DepResolution.installSimulate(modsAvailable, map(), tml).onComplete(context.asyncAssertFailure(res -> {
        context.assertEquals("Cannot remove module ot-1.0.0 which is explicitly given", res.getMessage());
        async.complete();
      }));
    }

    // install conflicting versions for ot 1/2
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = enableList(ot101, ot100);
      DepResolution.installSimulate(modsAvailable, map(), tml).onComplete(context.asyncAssertFailure(res -> {
        context.assertEquals("Cannot remove module ot-1.0.1 which is explicitly given", res.getMessage());
        async.complete();
      }));
    }

    // install conflicting versions for ot 2/2
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = enableList(ot100, ot101);
      DepResolution.installSimulate(modsAvailable, map(), tml).onComplete(context.asyncAssertFailure(res -> {
        context.assertEquals("Cannot remove module ot-1.0.0 which is explicitly given", res.getMessage());
        async.complete();
      }));
    }

  }

  @Test
  public void testOkapi925(TestContext context) {
    InterfaceDescriptor ont10 = new InterfaceDescriptor("ont", "1.0");
    InterfaceDescriptor int10 = new InterfaceDescriptor("int", "1.0");
    InterfaceDescriptor int20 = new InterfaceDescriptor("int", "2.0");

    ModuleDescriptor st100 = new ModuleDescriptor();
    st100.setId("st-1.0.0");
    st100.setProvides(new InterfaceDescriptor[]{int10});

    ModuleDescriptor st101 = new ModuleDescriptor();
    st101.setId("st-1.0.1");
    st101.setProvides(new InterfaceDescriptor[]{int20});

    ModuleDescriptor ot100 = new ModuleDescriptor();
    ot100.setId("ot-1.0.0");
    ot100.setRequires(new InterfaceDescriptor[] {int10});

    ModuleDescriptor ot101 = new ModuleDescriptor();
    ot101.setId("ot-1.0.1");
    ot101.setRequires(new InterfaceDescriptor[] {int20});

    ModuleDescriptor ot102 = new ModuleDescriptor();
    ot102.setId("ot-1.0.2");
    ot102.setRequires(new InterfaceDescriptor[] {int20, ont10});

    ModuleDescriptor p100 = new ModuleDescriptor();
    p100.setId("p-1.0.0");
    p100.setProvides(new InterfaceDescriptor[]{ont10});

    Map<String, ModuleDescriptor> modsAvailable = map(st100, st101, ot100, ot101, ot102, p100);

    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = enableList(st101, ot101);
      DepResolution.installSimulate(modsAvailable, map(ot100, st100), tml).onComplete(context.asyncAssertSuccess(res -> {
        context.assertEquals(2, tml.size());
        assertUpgrade(tml.get(0), st101, st100);
        assertUpgrade(tml.get(1), ot101, ot100);
        async.complete();
      }));
    }
    {
      Async async = context.async();
      List<TenantModuleDescriptor> tml = enableList(st101);
      DepResolution.installSimulate(modsAvailable, map(ot100, st100), tml).onComplete(context.asyncAssertSuccess(res -> {
        context.assertEquals(3, tml.size());
        assertUpgrade(tml.get(0), st101, st100);
        assertEnable(tml.get(1), p100);
        assertUpgrade(tml.get(2), ot102, ot100);
        async.complete();
      }));
    }
  }

  @Test
  public void testCheckAllConflicts() {
    Assert.assertEquals("", DepResolution.checkAllConflicts(map(mdA100, mdE100)));
    Assert.assertEquals("Interface int is provided by moduleA-1.0.0 and moduleA-1.1.0.",
        DepResolution.checkAllConflicts(map(mdA100, mdA110)));
    Assert.assertEquals("Interface int is provided by moduleA-1.0.0 and moduleA-1.1.0. "
            +"Interface int is provided by moduleA-2.0.0 and moduleA-1.1.0.",
        DepResolution.checkAllConflicts(map(mdA100, mdA110, mdA200)));
  }

  // see that order is right.. mdE requires mdA (so mdA must be enabled first)
  @Test
  public void testDepOrderSwapEnable(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdE100, mdA100);
    DepResolution.installSimulate(map(mdA100, mdA110, mdE100, mdE110), map(), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
          logger.debug("tml result = " + Json.encodePrettily(tml));
          context.assertEquals(2, tml.size());
          assertEnable(tml.get(0), mdA100);
          assertEnable(tml.get(1), mdE100);
        }));
  }

  // see that order is right.. mdE requires mdA (so mdA must be upgraded first)
  @Test
  public void testDepOrderSwapUpgrade(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdE110, mdA110);
    DepResolution.installSimulate(map(mdA100, mdA110, mdE100, mdE110), map(mdE100, mdA100), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
          logger.debug("tml result = " + Json.encodePrettily(tml));
          context.assertEquals(2, tml.size());
          assertUpgrade(tml.get(0), mdA110, mdA100);
          assertUpgrade(tml.get(1), mdE110, mdE100);
        }));
  }

  // see that order is right.. mdE requires mdA (so mdA must be enabled first)
  @Test
  public void testDepOrderAlready(TestContext context) {
    List<TenantModuleDescriptor> tml = enableList(mdA100, mdE100);
    DepResolution.installSimulate(map(mdA100, mdA110, mdE100, mdE110), map(), tml)
        .onComplete(context.asyncAssertSuccess(res -> {
          logger.debug("tml result = " + Json.encodePrettily(tml));
          context.assertEquals(2, tml.size());
          assertEnable(tml.get(0), mdA100);
          assertEnable(tml.get(1), mdE100);
        }));
  }



}
