package org.folio.okapi.util;

import io.vertx.core.json.Json;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.Set;
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

  private static List<TenantModuleDescriptor> createList(
      Action action, boolean product,
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

  static void assertEnable(List<TenantModuleDescriptor> tml, int idx, ModuleDescriptor md) {
    assertUpgrade(tml, idx, md, null);
  }

  static void assertUpgrade(List<TenantModuleDescriptor> tml, int idx, ModuleDescriptor md, ModuleDescriptor from) {
    assertAction(tml, idx, Action.enable, md, from);
  }

  static void assertDisable(List<TenantModuleDescriptor> tml, int idx, ModuleDescriptor md) {
    assertAction(tml, idx, Action.disable, md, null);
  }

  static void assertUpToDate(List<TenantModuleDescriptor> tml, int idx, ModuleDescriptor md) {
    assertAction(tml, idx, Action.uptodate, md, null);
  }

  static void assertAction(List<TenantModuleDescriptor> tml, int idx, Action action, ModuleDescriptor md, ModuleDescriptor from) {
    TenantModuleDescriptor tm = tml.get(idx);
    Assert.assertEquals(Json.encodePrettily(tml), action, tm.getAction());
    Assert.assertEquals(Json.encodePrettily(tml), md.getId(), tm.getId());
    Assert.assertEquals(Json.encodePrettily(tml), from != null ? from.getId() : null, tm.getFrom());
  }

  @Before
  public void setUp() {
    InterfaceDescriptor int10 = new InterfaceDescriptor("int", "1.0");
    InterfaceDescriptor[] int10a = {int10};
    InterfaceDescriptor int11 = new InterfaceDescriptor("int", "1.1");
    InterfaceDescriptor[] int11a = {int11};
    InterfaceDescriptor int20 = new InterfaceDescriptor("int", "2.0");
    InterfaceDescriptor[] int20a = {int20};

    mdA100 = new ModuleDescriptor("moduleA-1.0.0");
    mdA100.setProvides(int10a);

    mdB = new ModuleDescriptor("moduleB-1.0.0");
    mdB.setProvides(int10a);

    mdC = new ModuleDescriptor("moduleC-1.0.0");
    mdC.setProvides(int11a);

    mdA110 = new ModuleDescriptor("moduleA-1.1.0");
    mdA110.setProvides(int11a);

    mdA200 = new ModuleDescriptor("moduleA-2.0.0");
    mdA200.setProvides(int20a);

    mdD100 = new ModuleDescriptor("moduleD-1.0.0");
    mdD100.setOptional(int10a);

    mdD110 = new ModuleDescriptor("moduleD-1.1.0");
    mdD110.setOptional(int11a);

    mdD200 = new ModuleDescriptor("moduleD-2.0.0");
    mdD200.setOptional(int20a);

    mdDA200 = new ModuleDescriptor("moduleDA-2.0.0");
    mdDA200.setOptional(int20a);
    mdDA200.setRequires(new InterfaceDescriptor[]{new InterfaceDescriptor("unknown-interface", "2.0")});

    mdE100 = new ModuleDescriptor("moduleE-1.0.0");
    mdE100.setRequires(int10a);

    mdE110 = new ModuleDescriptor("moduleE-1.1.0");
    mdE110.setRequires(int11a);

    mdE200 = new ModuleDescriptor("moduleE-2.0.0");
    mdE200.setRequires(int20a);

    st100 = new ModuleDescriptor("st-1.0.0");
    st100.setProvides(int10a);

    st101 = new ModuleDescriptor("st-1.0.1");
    st101.setProvides(int20a);

    ot100 = new ModuleDescriptor("ot-1.0.0");
    ot100.setRequires(int10a);

    ot101 = new ModuleDescriptor("ot-1.0.1");
    ot101.setRequires(int20a);
  }

  @Test
  public void testLatest() {
    List<ModuleDescriptor> mdl = new LinkedList<>();

    mdl.add(mdA200);
    mdl.add(mdA100);
    mdl.add(mdB);
    mdl.add(mdC);
    mdl.add(mdA110);
    mdl.add(mdE100);

    DepResolution.getLatestProducts(2, mdl);

    Assert.assertEquals(5, mdl.size());
    Assert.assertEquals(mdE100, mdl.get(0));
    Assert.assertEquals(mdC, mdl.get(1));
    Assert.assertEquals(mdB, mdl.get(2));
    Assert.assertEquals(mdA200, mdl.get(3));
    Assert.assertEquals(mdA110, mdl.get(4));

    DepResolution.getLatestProducts(1, mdl);
    Assert.assertEquals(4, mdl.size());
    Assert.assertEquals(mdA200, mdl.get(3));
  }

  @Test
  public void testUpgradeUpToDate() {
    List<TenantModuleDescriptor> tml = enableList(mdA100);
    DepResolution.install(map(mdA100, mdB, mdC, mdA110, mdE110), map(mdA100), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 1, tml.size());
    assertUpToDate(tml, 0, mdA100);
  }

  @Test
  public void testUpgradeReinstall() {
    List<TenantModuleDescriptor> tml = enableList(mdA100);
    DepResolution.install(map(mdA100, mdB, mdC, mdA110, mdE110), map(mdA100), tml, true);
    Assert.assertEquals(Json.encodePrettily(tml), 1, tml.size());
    assertAction(tml, 0, Action.enable, mdA100, mdA100);
  }

  @Test
  public void testUpgradeDifferentProduct() {
    List<TenantModuleDescriptor> tml = enableList(mdB);
    DepResolution.install(map(mdA100, mdB, mdC, mdA110, mdE100), map(mdA100), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertDisable(tml, 0, mdA100);
    assertEnable(tml, 1, mdB);
  }

  @Test
  public void testUpgradeDifferentProductNoFixup() {
    List<TenantModuleDescriptor> tml = enableList(mdB);
    OkapiError error = Assert.assertThrows(OkapiError.class,
        () -> DepResolution.installMaxIterations(map(mdA100, mdB, mdC, mdA110, mdE100), map(mdA100), tml, false, 0));
    Assert.assertEquals("Multiple modules moduleB-1.0.0, moduleA-1.0.0 provide interface int", error.getMessage());
  }

  @Test
  public void testMultipleInterfaces() {
    List<TenantModuleDescriptor> tml = enableList(mdB,mdA100);

    OkapiError error = Assert.assertThrows(OkapiError.class,
        () -> DepResolution.install(map(mdA100, mdB, mdC, mdA110, mdE100), map(mdA100), tml, false));
    Assert.assertEquals("Multiple modules moduleB-1.0.0, moduleA-1.0.0 provide interface int", error.getMessage());
  }

  @Test
  public void testUpgradeSameProduct() {
    List<TenantModuleDescriptor> tml = enableList(mdA110);
    DepResolution.install(map(mdA100, mdB, mdC, mdA110, mdD100), map(mdA100), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 1, tml.size());
    assertUpgrade(tml, 0, mdA110, mdA100);
  }

  @Test
  public void testUpgrade1() {
    List<TenantModuleDescriptor> tml = enableList(mdD200);
    DepResolution.install(map(mdA100, mdB, mdC, mdA110, mdA200, mdD100, mdD200), map(mdB), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 3, tml.size());
    assertDisable(tml, 0, mdB);
    assertEnable(tml, 1, mdA200);
    assertEnable(tml, 2, mdD200);
  }

  @Test
  public void testUpgradeWithRequires() {
    List<TenantModuleDescriptor> tml = enableList(mdE100);
    DepResolution.install(map(mdA100, mdB, mdC, mdA110, mdD100, mdE100), map(mdA100), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 1, tml.size());
    assertEnable(tml, 0, mdE100);
  }

  // install optional with no provided interface enabled
  @Test
  public void testInstallOptional1() {
    List<TenantModuleDescriptor> tml = enableList(mdD100);
    DepResolution.install(map(mdA100, mdA110, mdD100, mdD110, mdE100), map(), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 1, tml.size());
    assertEnable(tml, 0, mdD100);
  }

  // install optional with a matched interface provided
  @Test
  public void testInstallOptional2() {
    List<TenantModuleDescriptor> tml = enableList(mdD100);
    DepResolution.install(map(mdA100, mdA110, mdD100, mdE100), map(mdA100), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 1, tml.size());
    assertEnable(tml, 0, mdD100);
  }

  // install optional with existing interface that is too low (error)
  @Test
  public void testInstallOptionalFail() {
    List<TenantModuleDescriptor> tml = enableList(mdD110);
    OkapiError error = Assert.assertThrows(OkapiError.class,
        () -> DepResolution.install(map(mdA100, mdD100, mdD110, mdE100), map(mdA100), tml, false));
    Assert.assertEquals("Incompatible version for module moduleD-1.1.0 interface int. Need 1.1. Have 1.0/moduleA-1.0.0",
        error.getMessage());
  }

  // install optional with existing interface that needs upgrading
  @Test
  public void testInstallMinorLeafOptional() {
    List<TenantModuleDescriptor> tml = enableList(mdD110);
    DepResolution.install(map(mdA100, mdA110, mdD100, mdD110, mdE100), map(mdA100), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertUpgrade(tml, 0, mdA110, mdA100);
    assertEnable(tml, 1, mdD110);
  }

  // upgrade base dependency which is still compatible with optional interface
  @Test
  public void testInstallMinorBaseOptional() {
    List<TenantModuleDescriptor> tml = enableList(mdA110);
    DepResolution.install(map(mdA100, mdA110, mdD100, mdD110), map(mdA100, mdD100), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 1, tml.size());
    assertUpgrade(tml, 0, mdA110, mdA100);
  }

  // upgrade optional dependency which require upgrading base dependency
  @Test
  public void testInstallMinorLeafOptional2() {
    List<TenantModuleDescriptor> tml = enableList(mdD110);
    DepResolution.install(map(mdA100, mdA110, mdD100, mdD110), map(mdA100, mdD100), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertUpgrade(tml, 0, mdA110, mdA100);
    assertUpgrade(tml, 1, mdD110, mdD100);
  }

  // upgrade base dependency which is a major interface bump to optional interface
  @Test
  public void testInstallMajorBaseOptional() {
    List<TenantModuleDescriptor> tml = enableList(mdA200);
    DepResolution.install(map(mdA100, mdA110, mdA200, mdD100, mdD110, mdD200),
        map(mdA100, mdD100), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertUpgrade(tml, 0, mdA200, mdA100);
    assertUpgrade(tml, 1, mdD200, mdD100);
  }

  // upgrade base dependency and pull in module with unknown interface (results in error)
  @Test
  public void testInstallMajorBaseError() {
    ModuleDescriptor mdD200F = new ModuleDescriptor(mdD200.getId());
    mdD200F.setOptional(mdD200.getOptional());
    mdD200F.setRequires(new InterfaceDescriptor[]{new InterfaceDescriptor("unknown", "2.0")});

    List<TenantModuleDescriptor> tml = enableList(mdA200);
    OkapiError error = Assert.assertThrows(OkapiError.class,
        () ->  DepResolution.install(map(mdA100, mdA110, mdA200, mdD100, mdD110, mdD200F),
           map(mdA100, mdD100), tml, false));
    Assert.assertEquals("interface unknown required by module moduleD-2.0.0 not found", error.getMessage());
  }

  // upgrade optional dependency which require upgrading base dependency
  @Test
  public void testInstallMajorLeafOptional() {
    List<TenantModuleDescriptor> tml = enableList(mdD200);
    DepResolution.install(map(mdA100, mdA110, mdA200, mdD100, mdD110, mdD200, mdA200),
        map(mdA100, mdD100), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertUpgrade(tml, 0, mdA200, mdA100);
    assertUpgrade(tml, 1, mdD200, mdD100);
  }

  // install optional with existing interface that needs upgrading, but
  // there are multiple modules providing same interface
  @Test
  public void testInstallOptionalExistingModuleFail() {
    List<TenantModuleDescriptor> tml = enableList(mdD110);
    OkapiError error = Assert.assertThrows(OkapiError.class,
        () -> DepResolution.install(map(mdA100, mdA110, mdB, mdC, mdD100, mdD110, mdE100),
            map(mdA100), tml, false));
    Assert.assertEquals(
        "interface int required by module moduleD-1.1.0 is provided by multiple products: moduleA, moduleC",
        error.getMessage());
  }

  // upgrade base dependency which is a major interface bump to required interface
  @Test
  public void testInstallMajorBaseRequired() {
    List<TenantModuleDescriptor> tml = enableList(mdA200);
    DepResolution.install(map(mdA100, mdA200, mdE100, mdE200),
        map(mdA100, mdE100), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertUpgrade(tml, 0, mdA200, mdA100);
    assertUpgrade(tml, 1, mdE200, mdE100);
  }

  // upgrade both dependency which is a major interface bump to required interface
  @Test
  public void testInstallMajorBaseRequired2() {
    List<TenantModuleDescriptor> tml = enableList(mdA200, mdE200);
    DepResolution.install(map(mdA100, mdA200, mdE100, mdE200),
        map(mdA100, mdE100), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertUpgrade(tml, 0, mdA200, mdA100);
    assertUpgrade(tml, 1, mdE200, mdE100);
  }

  // upgrade both dependency which is a major interface bump to required interface
  @Test
  public void testInstallMajorBaseRequired3() {
    List<TenantModuleDescriptor> tml = enableList(mdE200, mdA200);
    DepResolution.install(map(mdA100, mdA200, mdE100, mdE200),
        map(mdA100, mdE100), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertUpgrade(tml, 0, mdA200, mdA100);
    assertUpgrade(tml, 1, mdE200, mdE100);
  }

  // upgrade module with major dependency upgrade
  @Test
  public void testInstallMajorLeafRequired() {
    List<TenantModuleDescriptor> tml = enableList(mdE200);
    DepResolution.install(map(mdA100, mdA200, mdE100, mdE200), map(mdA100, mdE100), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertUpgrade(tml, 0, mdA200, mdA100);
    assertUpgrade(tml, 1, mdE200, mdE100);
  }

  @Test
  public void testInstallNew1() {
    List<TenantModuleDescriptor> tml = enableList(mdE100, mdA100);
    DepResolution.install(map(mdA100, mdB, mdC, mdA110, mdE100), map(), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertEnable(tml, 0, mdA100);
    assertEnable(tml, 1, mdE100);
  }

  @Test
  public void testInstallNew2() {
    List<TenantModuleDescriptor> tml = enableList(mdE100, mdB);
    DepResolution.install(map(mdA100, mdB, mdC, mdA110, mdE100), map(), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertEnable(tml, 0, mdB);
    assertEnable(tml, 1, mdE100);
  }

  @Test
  public void testInstallNewProduct() {
    List<TenantModuleDescriptor> tml = createList(Action.enable, true, mdE100, mdB);
    DepResolution.install(map(mdA100, mdB, mdC, mdA110, mdE100), map(), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertEnable(tml, 0, mdB);
    assertEnable(tml, 1, mdE100);
  }

  @Test
  public void testInstallNewProductNonExisting() {
    List<TenantModuleDescriptor> tml = enableList(mdB);
    OkapiError error = Assert.assertThrows(OkapiError.class,
        () -> DepResolution.install(map(mdA100), map(), tml, false));
    Assert.assertEquals("Module moduleB-1.0.0 not found", error.getMessage());
  }

  @Test
  public void testMultipleInterfacesMatch1() {
    List<TenantModuleDescriptor> tml = enableList(mdE100);
    OkapiError error = Assert.assertThrows(OkapiError.class,
        () -> DepResolution.install(map(mdA100, mdB, mdE100), map(), tml, false));
    Assert.assertEquals(
        "interface int required by module moduleE-1.0.0 is provided by multiple products: moduleA, moduleB",
        error.getMessage());
  }

  @Test
  public void testMultipleInterfacesMatch2() {
    List<TenantModuleDescriptor> tml = enableList(mdE100);
    OkapiError error = Assert.assertThrows(OkapiError.class,
        () -> DepResolution.install(map(mdA100, mdC, mdE100), map(), tml, false));
    Assert.assertEquals(
        "interface int required by module moduleE-1.0.0 is provided by multiple products: moduleA, moduleC",
        error.getMessage());
  }

  @Test
  public void testMultipleInterfacesMatchReplaces1() {
    mdB.setReplaces(new String[]{mdA100.getProduct()});
    List<TenantModuleDescriptor> tml = enableList(mdE100);
    DepResolution.install(map(mdA100, mdA110, mdB, mdE100), map(), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertEnable(tml, 0, mdB);
    assertEnable(tml, 1, mdE100);
  }

  @Test
  public void testMultipleInterfacesMatchReplaces2() {
    mdB.setReplaces(new String[]{mdA100.getProduct()});
    mdC.setReplaces(new String[]{mdB.getProduct()});

    List<TenantModuleDescriptor> tml = enableList(mdE100);
    DepResolution.install(map(mdA100, mdA110, mdB, mdC, mdE100), map(), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertEnable(tml, 0, mdC);
    assertEnable(tml, 1, mdE100);
  }

  @Test
  public void testDisableNonEnabled() {
    List<TenantModuleDescriptor> tml = createList(Action.disable, mdA100);
    OkapiError error = Assert.assertThrows(OkapiError.class,
        () -> DepResolution.install(map(mdA100), map(), tml, false));
    Assert.assertEquals("Module moduleA-1.0.0 not found", error.getMessage());
  }

  @Test
  public void testDisableNonExisting() {
    List<TenantModuleDescriptor> tml = createList(Action.disable, mdA100);
    OkapiError error = Assert.assertThrows(OkapiError.class,
        () -> DepResolution.install(map(mdB), map(), tml, false));
    Assert.assertEquals("Module moduleA-1.0.0 not found", error.getMessage());
  }

  @Test
  public void testDisable1() {
    List<TenantModuleDescriptor> tml = createList(Action.disable, mdA100);
    DepResolution.install(map(mdA100, mdA110, mdE100), map(mdA100, mdE100), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertDisable(tml, 0, mdE100);
    assertDisable(tml, 1, mdA100);
  }

  @Test
  public void testDisableProduct() {
    List<TenantModuleDescriptor> tml = createList(Action.disable, true, mdA100);
    DepResolution.install(map(mdA100, mdA110, mdE100), map(mdA100, mdE100), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertDisable(tml, 0, mdE100);
    assertDisable(tml, 1, mdA100);
  }

  @Test
  public void testDisable2() {
    List<TenantModuleDescriptor> tml = createList(Action.disable, mdE100, mdA100);
    DepResolution.install(map(mdA100, mdA110, mdE100), map(mdA100, mdE100), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertDisable(tml, 0, mdE100);
    assertDisable(tml, 1, mdA100);
  }

  @Test
  public void testDisable3() {
    List<TenantModuleDescriptor> tml = createList(Action.disable, mdA100, mdE100);
    DepResolution.install(map(mdA100, mdA110, mdE100), map(mdA100, mdE100), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertDisable(tml, 0, mdE100);
    assertDisable(tml, 1, mdA100);
  }

  @Test
  public void testUpToDate1() {
    List<TenantModuleDescriptor> tml = createList(Action.uptodate, mdA100);
    DepResolution.install(map(mdA100), map(mdA100), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 1, tml.size());
    assertUpToDate(tml, 0, mdA100);
  }

  @Test
  public void testUpToDate2() {
    List<TenantModuleDescriptor> tml = enableList(mdA100, mdA100);
    DepResolution.install(map(mdA100), map(mdA100), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertUpToDate(tml, 0, mdA100);
    assertUpToDate(tml, 1, mdA100);
  }

  @Test
  public void testUpToDate3() {
    List<TenantModuleDescriptor> tml = enableList(mdA100, mdA100);
    DepResolution.install(map(mdA100), map(), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertEnable(tml, 0, mdA100);
    assertUpToDate(tml, 1, mdA100);
  }

  @Test
  public void testMultiErrors() {
    InterfaceDescriptor[] inta10 = {new InterfaceDescriptor("inta", "1.0")};
    InterfaceDescriptor[] inta20 = {new InterfaceDescriptor("inta", "2.0")};

    ModuleDescriptor mdA100 = new ModuleDescriptor("moduleA-1.0.0");
    mdA100.setProvides(inta10);

    ModuleDescriptor mdB = new ModuleDescriptor("moduleB-1.0.0");
    mdB.setRequires(inta20);

    ModuleDescriptor mdC = new ModuleDescriptor("moduleC-1.0.0");
    mdC.setRequires(inta20);

    Map<String, ModuleDescriptor> modsAvailable = map(mdA100, mdB, mdC);

    OkapiError error = Assert.assertThrows(OkapiError.class,
        () -> {
          List<TenantModuleDescriptor> tml = enableList(mdA100, mdB, mdC);
          DepResolution.install(modsAvailable, map(), tml, false);
        });
    Assert.assertEquals("Incompatible version for module moduleB-1.0.0 interface inta. Need 2.0. Have 1.0/moduleA-1.0.0."
            + " Incompatible version for module moduleC-1.0.0 interface inta. Need 2.0. Have 1.0/moduleA-1.0.0",
        error.getMessage());

    error = Assert.assertThrows(OkapiError.class,
        () -> {
          List<TenantModuleDescriptor> tml = enableList(mdA100, mdC, mdB);
          DepResolution.install(modsAvailable, map(), tml, false);
        });
    Assert.assertEquals("Incompatible version for module moduleB-1.0.0 interface inta. Need 2.0. Have 1.0/moduleA-1.0.0."
            + " Incompatible version for module moduleC-1.0.0 interface inta. Need 2.0. Have 1.0/moduleA-1.0.0",
        error.getMessage());
  }

  @Test
  public void testOkapi647() {
    InterfaceDescriptor[] i1_10 = {new InterfaceDescriptor("i1", "1.0")};
    ModuleDescriptor prov100 = new ModuleDescriptor("prov-1.0.0");
    prov100.setProvides(i1_10);

    InterfaceDescriptor[] i1_20 = {new InterfaceDescriptor("i1", "2.0")};
    ModuleDescriptor prov200 = new ModuleDescriptor("prov-2.0.0");
    prov200.setProvides(i1_20);

    OkapiError error = Assert.assertThrows(OkapiError.class,
        () -> {
          Map<String, ModuleDescriptor> modsAvailable = map(prov100, prov200);
          List<TenantModuleDescriptor> tml = enableList(prov100, prov200);
          DepResolution.install(modsAvailable, map(), tml, false);
        });
    Assert.assertEquals("Cannot remove module prov-1.0.0 which is explicitly given", error.getMessage());

    error = Assert.assertThrows(OkapiError.class,
        () -> {
          Map<String, ModuleDescriptor> modsAvailable = map(prov100, prov200);
          List<TenantModuleDescriptor> tml = enableList(prov200, prov100);
          DepResolution.install(modsAvailable, map(), tml, false);
        });
    Assert.assertEquals("Cannot remove module prov-2.0.0 which is explicitly given", error.getMessage());

    InterfaceDescriptor[] i2_10 = {new InterfaceDescriptor("i2", "1.0")};
    ModuleDescriptor req1_100 = new ModuleDescriptor("req1-1.0.0");
    req1_100.setProvides(i2_10);
    req1_100.setRequires(i1_10);

    InterfaceDescriptor[] i3_10 = {new InterfaceDescriptor("i3", "1.0")};
    ModuleDescriptor req2_100 = new ModuleDescriptor("req2-1.0.0");
    req1_100.setProvides(i3_10);
    req2_100.setRequires(i1_20);

    error = Assert.assertThrows(OkapiError.class,
        () -> {
          Map<String, ModuleDescriptor> modsAvailable = map(prov100, prov200, req1_100, req2_100);
          List<TenantModuleDescriptor> tml = enableList(req1_100, req2_100);
          DepResolution.install(modsAvailable, map(), tml, false);
        });
    Assert.assertEquals("Incompatible version for module req2-1.0.0 interface i1. Need 2.0. Have 1.0/prov-1.0.0", error.getMessage());

    {
      Map<String, ModuleDescriptor> modsAvailable = map(prov100, prov200, req1_100, req2_100);
      List<TenantModuleDescriptor> tml = enableList(req1_100);
      DepResolution.install(modsAvailable, map(), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
      assertEnable(tml, 0, prov100);
      assertEnable(tml, 1, req1_100);
    }

    {
      Map<String, ModuleDescriptor> modsAvailable = map(prov100, prov200, req1_100, req2_100);
      List<TenantModuleDescriptor> tml = enableList(req2_100);
      DepResolution.install(modsAvailable, map(), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
      assertEnable(tml, 0, prov200);
      assertEnable(tml, 1, req2_100);
    }

    ModuleDescriptor req1or2 = new ModuleDescriptor("req1or2-1.0.0");
    InterfaceDescriptor[] i4_10 = {new InterfaceDescriptor("i4", "1.0")};
    req1or2.setProvides(i4_10);
    req1or2.setRequires("i1", "1.0 2.0");

    ModuleDescriptor reqI1or2 = new ModuleDescriptor("reqI1or2-1.0.0");
    InterfaceDescriptor[] i5_10 = {new InterfaceDescriptor("i5", "1.0")};
    reqI1or2.setProvides(i5_10);
    reqI1or2.setRequires(i4_10);

    {
      Map<String, ModuleDescriptor> modsAvailable = map(prov100, prov200, req1_100, req2_100, req1or2, reqI1or2);
      List<TenantModuleDescriptor> tml = enableList(reqI1or2, req1_100);
      DepResolution.install(modsAvailable, map(), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 4, tml.size());
      assertEnable(tml, 0, prov100);
      assertEnable(tml, 1, req1or2);
      assertEnable(tml, 2, reqI1or2);
      assertEnable(tml, 3, req1_100);
    }

    {
      Map<String, ModuleDescriptor> modsAvailable = map(prov100, prov200, req1_100, req2_100, req1or2, reqI1or2);
      List<TenantModuleDescriptor> tml = enableList(req1_100, reqI1or2);
      DepResolution.install(modsAvailable, map(), tml, false);
      logger.debug("tml result = " + Json.encodePrettily(tml));
      Assert.assertEquals(Json.encodePrettily(tml), 4, tml.size());
      assertEnable(tml, 0, prov100);
      assertEnable(tml, 1, req1or2);
      assertEnable(tml, 2, req1_100);
      assertEnable(tml, 3, reqI1or2);
    }
  }

  @Test
  public void testSorting() {
    InterfaceDescriptor[] i0 = {new InterfaceDescriptor("i0", "1.0")};

    ModuleDescriptor modA = new ModuleDescriptor("modA-1.0.0");
    InterfaceDescriptor[] i1 = {new InterfaceDescriptor("i1", "1.0")};
    modA.setProvides(i1);

    ModuleDescriptor modB = new ModuleDescriptor("modB-1.0.0");
    InterfaceDescriptor[] i2 = {new InterfaceDescriptor("i2", "1.0")};
    modB.setProvides(i2);
    modB.setRequires(i1);
    modB.setOptional(i0);

    ModuleDescriptor modC = new ModuleDescriptor("modC-1.0.0");
    InterfaceDescriptor[] i3 = {new InterfaceDescriptor("i3", "1.0")};
    modC.setProvides(i3);
    modC.setRequires(i2);

    ModuleDescriptor modD = new ModuleDescriptor("modD-1.0.0");
    modD.setOptional(i2);

    Assert.assertTrue(DepResolution.moduleDepProvided(Collections.emptyList(), Set.of("i1"), modA));
    Assert.assertFalse(DepResolution.moduleDepProvided(Collections.emptyList(), Set.of("i1", "i2"), modB));
    Assert.assertFalse(DepResolution.moduleDepProvided(Collections.emptyList(), Set.of("i1", "i2", "i3"), modC));

    Assert.assertTrue(DepResolution.moduleDepProvided(List.of(modA), Set.of("i1"), modB));
    Assert.assertFalse(DepResolution.moduleDepProvided(List.of(modA), Set.of("i0", "i1"), modB));
    Assert.assertFalse(DepResolution.moduleDepProvided(List.of(modA), Set.of("i0", "i1", "i2", "i3"), modC));
    Assert.assertTrue(DepResolution.moduleDepProvided(List.of(modA, modB), Set.of("i1", "i2"), modC));

    List<ModuleDescriptor> l = new ArrayList<>(List.of(modC, modA, modB));
    DepResolution.topoSort(l);
    Assert.assertArrayEquals(new ModuleDescriptor[]{modA, modB, modC}, l.toArray());

    l = new ArrayList<>(List.of(modB, modA, modC));
    DepResolution.topoSort(l);
    Assert.assertArrayEquals(new ModuleDescriptor[]{modA, modB, modC}, l.toArray());

    {
      Map<String, ModuleDescriptor> modsAvailable = map(modA, modB, modC);
      List<TenantModuleDescriptor> tml = enableList(modA, modB, modC);
      DepResolution.install(modsAvailable, map(), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 3, tml.size());
      assertEnable(tml, 0, modA);
      assertEnable(tml, 1, modB);
      assertEnable(tml, 2, modC);
    }

    {
      Map<String, ModuleDescriptor> modsAvailable = map(modA, modB, modC);
      List<TenantModuleDescriptor> tml = enableList(modC, modB, modA);
      DepResolution.install(modsAvailable, map(), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 3, tml.size());
      assertEnable(tml, 0, modA);
      assertEnable(tml, 1, modB);
      assertEnable(tml, 2, modC);
    }

    {
      Map<String, ModuleDescriptor> modsAvailable = map(modA, modB, modC);
      List<TenantModuleDescriptor> tml = enableList(modC, modA, modB);
      DepResolution.install(modsAvailable, map(), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 3, tml.size());
      assertEnable(tml, 0, modA);
      assertEnable(tml, 1, modB);
      assertEnable(tml, 2, modC);
    }

    {
      Map<String, ModuleDescriptor> modsAvailable = map(modA, modB, modC);
      List<TenantModuleDescriptor> tml = enableList(modB, modC, modA);
      DepResolution.install(modsAvailable, map(), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 3, tml.size());
      assertEnable(tml, 0, modA);
      assertEnable(tml, 1, modB);
      assertEnable(tml, 2, modC);
    }

    {
      Map<String, ModuleDescriptor> modsAvailable = map(modA, modB, modC);
      List<TenantModuleDescriptor> tml = enableList(modC, modB);
      DepResolution.install(modsAvailable, map(modA), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
      assertEnable(tml, 0, modB);
      assertEnable(tml, 1, modC);
    }

    {
      Map<String, ModuleDescriptor> modsAvailable = map(modA, modB, modC);
      List<TenantModuleDescriptor> tml = createList(Action.disable, modB, modC, modA);
      DepResolution.install(modsAvailable, map(modA, modB, modC), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 3, tml.size());
      assertDisable(tml, 0, modC);
      assertDisable(tml, 1, modB);
      assertDisable(tml, 2, modA);
    }

    {
      Map<String, ModuleDescriptor> modsAvailable = map(modA, modB, modC);
      List<TenantModuleDescriptor> tml = createList(Action.disable, modB, modA, modC);
      DepResolution.install(modsAvailable, map(modA, modB, modC), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 3, tml.size());
      assertDisable(tml, 0, modC);
      assertDisable(tml, 1, modB);
      assertDisable(tml, 2, modA);
    }

    {
      Map<String, ModuleDescriptor> modsAvailable = map(modA, modB, modC);
      List<TenantModuleDescriptor> tml = createList(Action.disable, modB, modC, modA);
      DepResolution.install(modsAvailable, map(modA, modB, modC), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 3, tml.size());
      assertDisable(tml, 0, modC);
      assertDisable(tml, 1, modB);
      assertDisable(tml, 2, modA);
    }

    {
      Map<String, ModuleDescriptor> modsAvailable = map(modA, modB, modC);
      List<TenantModuleDescriptor> tml = createList(Action.disable, modB);
      DepResolution.install(modsAvailable, map(modA, modB, modC), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
      assertDisable(tml, 0, modC);
      assertDisable(tml, 1, modB);
    }

    {
      Map<String, ModuleDescriptor> modsAvailable = map(modA, modB, modC);
      List<TenantModuleDescriptor> tml = createList(Action.disable, modB, modC);
      DepResolution.install(modsAvailable, map(modA, modB, modC), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
      assertDisable(tml, 0, modC);
      assertDisable(tml, 1, modB);
    }

    {
      Map<String, ModuleDescriptor> modsAvailable = map(modA, modB, modC);
      List<TenantModuleDescriptor> tml = createList(Action.disable, modA);
      DepResolution.install(modsAvailable, map(modA, modB), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
      assertDisable(tml, 0, modB);
      assertDisable(tml, 1, modA);
    }

    {
      Map<String, ModuleDescriptor> modsAvailable = map(modA, modB, modC, modD);
      List<TenantModuleDescriptor> tml = enableList(modA, modD);
      DepResolution.install(modsAvailable, map(), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
      assertEnable(tml, 0, modA);
      assertEnable(tml, 1, modD);
    }

    {
      Map<String, ModuleDescriptor> modsAvailable = map(modA, modB, modC, modD);
      List<TenantModuleDescriptor> tml = enableList(modD, modA);
      DepResolution.install(modsAvailable, map(), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
      assertEnable(tml, 0, modD);
      assertEnable(tml, 1, modA);
    }

    {
      Map<String, ModuleDescriptor> modsAvailable = map(modA, modB, modC, modD);
      List<TenantModuleDescriptor> tml = enableList(modD, modB);
      DepResolution.install(modsAvailable, map(), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 3, tml.size());
      assertEnable(tml, 0, modA);
      assertEnable(tml, 1, modB);
      assertEnable(tml, 2, modD);
    }

    {
      Map<String, ModuleDescriptor> modsAvailable = map(modA, modB, modC, modD);
      List<TenantModuleDescriptor> tml = enableList(modB, modD);
      tml.addAll(createList(Action.suggest, modA));
      DepResolution.install(modsAvailable, map(), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 4, tml.size());
      assertEnable(tml, 0, modA);
      assertEnable(tml, 1, modB);
      assertEnable(tml, 2, modD);
      assertAction(tml, 3, Action.suggest, modA, null);
    }
  }

  @Test
  public void testCheckDependencies() {
    InterfaceDescriptor inu10 = new InterfaceDescriptor("inu", "1.0");
    InterfaceDescriptor[] inu10a = {inu10};

    InterfaceDescriptor int10 = new InterfaceDescriptor("int", "1.0");
    InterfaceDescriptor[] int10a = {int10};

    ModuleDescriptor mdA = new ModuleDescriptor("moduleA-1.0.0");
    mdA.setProvides(int10a);

    ModuleDescriptor mdB = new ModuleDescriptor("moduleB-1.0.0");
    mdB.setRequires(int10a);
    mdB.setProvides(inu10a);

    InterfaceDescriptor int20 = new InterfaceDescriptor("int", "2.0");
    InterfaceDescriptor[] int20a = {int20};

    ModuleDescriptor mdC = new ModuleDescriptor("moduleC-1.0.0");
    mdC.setProvides(int20a);

    ModuleDescriptor mdD = new ModuleDescriptor("moduleD-1.0.0");
    mdD.setRequires(int20a);
    mdD.setProvides(inu10a);

    InterfaceDescriptor int30 = new InterfaceDescriptor("int", "3.0");
    InterfaceDescriptor[] int30a = {int30};

    ModuleDescriptor mdE = new ModuleDescriptor("moduleE-1.0.0");
    mdE.setProvides(int30a);

    Assert.assertEquals("", DepResolution.checkAvailable(map(mdA, mdB, mdC, mdD)));

    Assert.assertEquals("Missing dependency: moduleB-1.0.0 requires int: 1.0",
        DepResolution.checkAvailable(map(mdB)));

    Map<String, ModuleDescriptor> available = map(mdB, mdC, mdD);

    Assert.assertEquals("Incompatible version for module moduleB-1.0.0 interface int. Need 1.0. Have 2.0/moduleC-1.0.0",
        DepResolution.checkAvailable(available));

    Collection<ModuleDescriptor> testList = new TreeSet<>();
    testList.add(mdD);
    Assert.assertEquals("", DepResolution.checkAvailable(available.values(), testList, false));

    available.put(mdE.getId(), mdE);

    testList = new TreeSet<>();
    testList.add(mdB);
    testList.add(mdC);
    Assert.assertEquals("Incompatible version for module moduleB-1.0.0 interface int. Need 1.0. Have 2.0/moduleC-1.0.0 3.0/moduleE-1.0.0",
        DepResolution.checkAvailable(available.values(), testList, false));
    Assert.assertTrue(testList.containsAll(List.of(mdB, mdC)));

    testList = new TreeSet<>();
    testList.add(mdB);
    testList.add(mdC);
    Assert.assertEquals("",
        DepResolution.checkAvailable(available.values(), testList, true));
    Assert.assertTrue(testList.containsAll(List.of(mdC)));

    Assert.assertEquals("", DepResolution.checkAvailable(map(mdC, mdD)));

    ModuleDescriptor mdOpt = new ModuleDescriptor("moduleO-1.0.0");
    mdOpt.setOptional(int10a);

    testList = new TreeSet<>();
    testList.add(mdOpt);
    Assert.assertEquals("", DepResolution.checkAvailable(map(mdOpt)));
    Assert.assertEquals("", DepResolution.checkAvailable(map(mdOpt, mdA)));
    Assert.assertEquals("", DepResolution.checkAvailable(map(mdOpt, mdA, mdC)));
    Assert.assertEquals("Incompatible version for module moduleO-1.0.0 interface int. Need 1.0. Have 2.0/moduleC-1.0.0",
        DepResolution.checkAvailable(map(mdOpt, mdC)));
  }

  @Test
  public void testUpgradeImpossible() {
    Map<String, ModuleDescriptor> modsAvailable = map(st100, st101, ot100, ot101);

    // patch to higher version with impossible combination 1/4
    OkapiError error = Assert.assertThrows(OkapiError.class,
        () -> {
          List<TenantModuleDescriptor> tml = enableList(ot100, st101);
          DepResolution.install(modsAvailable, map(ot100, st100), tml, false);
        });
    Assert.assertEquals("Incompatible version for module ot-1.0.0 interface int. Need 1.0. Have 2.0/st-1.0.1", error.getMessage());

    // patch to higher version with impossible combination 2/4
    error = Assert.assertThrows(OkapiError.class,
        () -> {
          List<TenantModuleDescriptor> tml = enableList(st101, ot100);
          DepResolution.install(modsAvailable, map(ot100, st100), tml, false);
        });
    Assert.assertEquals("Incompatible version for module ot-1.0.0 interface int. Need 1.0. Have 2.0/st-1.0.1", error.getMessage());

    // patch to higher version with impossible combination 3/4
    error = Assert.assertThrows(OkapiError.class,
        () -> {
          List<TenantModuleDescriptor> tml = enableList(ot101, st100);
          DepResolution.install(modsAvailable, map(ot100, st100), tml, false);
        });
    Assert.assertEquals("Incompatible version for module ot-1.0.1 interface int. Need 2.0. Have 1.0/st-1.0.0", error.getMessage());

    // patch to higher version with impossible combination 4/4
    error = Assert.assertThrows(OkapiError.class,
        () -> {
          List<TenantModuleDescriptor> tml = enableList(st100, ot101);
          DepResolution.install(modsAvailable, map(ot100, st100), tml, false);
        });
    Assert.assertEquals("Incompatible version for module ot-1.0.1 interface int. Need 2.0. Have 1.0/st-1.0.0", error.getMessage());
  }

  @Test
  public void testUpgradeOneGiven() {
    // patch to higher version with ot given
    {
      List<TenantModuleDescriptor> tml = enableList(ot101);
      DepResolution.install(map(st100, st101, ot100, ot101), map(ot100, st100), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
      assertUpgrade(tml, 0, st101, st100);
      assertUpgrade(tml, 1, ot101, ot100);
    }

    // patch to higher version with st given
    {
      List<TenantModuleDescriptor> tml = enableList(st101);
      DepResolution.install(map(st100, st101, ot100, ot101),
          map(ot100, st100), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
      assertUpgrade(tml, 0, st101, st100);
      assertUpgrade(tml, 1, ot101, ot100);
    }
    // patch to higher version with st given, but no fixup
    {
      List<TenantModuleDescriptor> tml = enableList(st101);
      OkapiError error = Assert.assertThrows(OkapiError.class,
          () -> DepResolution.installMaxIterations(map(st100, st101, ot100, ot101),
              map(ot100, st100), tml, false, 0));
      Assert.assertEquals("Incompatible version for module ot-1.0.0 interface int. Need 1.0. Have 2.0/st-1.0.1",
          error.getMessage());
    }

    // patch to higher version with st given, but with multiple products to support it
    {
      ModuleDescriptor ot101_alt = new ModuleDescriptor("otA-1.0.1");
      ot101_alt.setRequires(ot101.getRequires());

      List<TenantModuleDescriptor> tml = enableList(st101);
      OkapiError error = Assert.assertThrows(OkapiError.class,
          () -> DepResolution.install(map(st100, st101, ot100, ot101, ot101_alt),
              map(ot100, st100), tml, false));
      Assert.assertEquals("interface int required by module ot-1.0.0 is provided by multiple products: ot, otA",
          error.getMessage());
    }
  }

  @Test
  public void testUpgradeBothGiven() {
    Map<String, ModuleDescriptor> modsAvailable = map(st100, st101, ot100, ot101);
    // patch to higher version with both given 1/2
    {
      List<TenantModuleDescriptor> tml = enableList(ot101, st101);
      DepResolution.install(modsAvailable, map(ot100, st100), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
      assertUpgrade(tml, 0, st101, st100);
      assertUpgrade(tml, 1, ot101, ot100);
    }

    // patch to higher version with both given 2/2
    {
      List<TenantModuleDescriptor> tml = enableList(st101, ot101);
      DepResolution.install(modsAvailable, map(ot100, st100), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
      assertUpgrade(tml, 0, st101, st100);
      assertUpgrade(tml, 1, ot101, ot100);
    }
  }
  @Test
  public void testUpgradeWithProduct() {
    Map<String, ModuleDescriptor> modsAvailable = map(st100, st101, ot100, ot101);

    // patch to higher version with products only
    {
      List<TenantModuleDescriptor> tml = createList(Action.enable, true, st101, ot100);
      DepResolution.install(modsAvailable, map(ot100, st100), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
      assertUpgrade(tml, 0, st101, st100);
      assertUpgrade(tml, 1, ot101, ot100);
    }

    // patch to higher version with products for ot
    {
      List<TenantModuleDescriptor> tml = createList(Action.enable, true, ot100);
      DepResolution.install(modsAvailable, map(ot100, st100), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
      assertUpgrade(tml, 0, st101, st100);
      assertUpgrade(tml, 1, ot101, ot100);
    }

    // patch to higher version with products for st
    {
      List<TenantModuleDescriptor> tml = createList(Action.enable, true, st100);
      DepResolution.install(modsAvailable, map(ot100, st100), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
      assertUpgrade(tml, 0, st101, st100);
      assertUpgrade(tml, 1, ot101, ot100);
    }
  }

  @Test
  public void testDowngradeImpossible() {
    Map<String, ModuleDescriptor> modsAvailable = map(st100, st101, ot100, ot101);
    // patch to lower version with impossible combination
    OkapiError error = Assert.assertThrows(OkapiError.class,
        () -> {
          List<TenantModuleDescriptor> tml = enableList(ot100, st101);
          DepResolution.install(modsAvailable, map(ot101, st101), tml, false);
        });
    Assert.assertEquals("Incompatible version for module ot-1.0.0 interface int. Need 1.0. Have 2.0/st-1.0.1", error.getMessage());

    // patch to lower version with impossible combination
    error = Assert.assertThrows(OkapiError.class,
        () -> {
          List<TenantModuleDescriptor> tml = enableList(ot101, st100);
          DepResolution.install(modsAvailable, map(ot101, st101), tml, false);
        });
    Assert.assertEquals("Incompatible version for module ot-1.0.1 interface int. Need 2.0. Have 1.0/st-1.0.0", error.getMessage());
  }

  @Test
  public void testDowngradeGiven() {
    Map<String, ModuleDescriptor> modsAvailable = map(st100, st101, ot100, ot101);

    // patch to lower version with both given
    {
      List<TenantModuleDescriptor> tml = enableList(ot100, st100);
      DepResolution.install(modsAvailable, map(ot101, st101), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
      assertUpgrade(tml, 0, st100, st101);
      assertUpgrade(tml, 1, ot100, ot101);
    }

    // patch to lower version with ot given
    {
      List<TenantModuleDescriptor> tml = enableList(ot100);
      DepResolution.install(modsAvailable, map(ot101, st101), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
      assertUpgrade(tml, 0, st100, st101);
      assertUpgrade(tml, 1, ot100, ot101);
    }

    // patch to lower version with st given
    {
      List<TenantModuleDescriptor> tml = enableList(st100);
      DepResolution.install(modsAvailable, map(ot101, st101), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
      assertUpgrade(tml, 0, st100, st101);
      assertUpgrade(tml, 1, ot100, ot101);
    }
  }

  @Test
  public void testInstallConflicting() {
    Map<String, ModuleDescriptor> modsAvailable = map(st100, st101, ot100, ot101);

    // install conflicting versions 1/2
    OkapiError error = Assert.assertThrows(OkapiError.class,
        () -> {
          List<TenantModuleDescriptor> tml = enableList(st100, st101);
          DepResolution.install(modsAvailable, map(), tml, false);
        });
    Assert.assertEquals("Cannot remove module st-1.0.0 which is explicitly given", error.getMessage());

    // install conflicting versions for st 1/2
    error = Assert.assertThrows(OkapiError.class,
        () -> {
          List<TenantModuleDescriptor> tml = enableList(st101, st100);
          DepResolution.install(modsAvailable, map(), tml, false);
        });
    Assert.assertEquals("Cannot remove module st-1.0.1 which is explicitly given", error.getMessage());

    // install conflicting versions for st 2/2
    error = Assert.assertThrows(OkapiError.class,
        () -> {
          List<TenantModuleDescriptor> tml = enableList(ot100, ot101);
          DepResolution.install(modsAvailable, map(), tml, false);
        });
    Assert.assertEquals("Cannot remove module ot-1.0.0 which is explicitly given", error.getMessage());

    // install conflicting versions for ot 1/2
    error = Assert.assertThrows(OkapiError.class,
        () -> {
          List<TenantModuleDescriptor> tml = enableList(ot101, ot100);
          DepResolution.install(modsAvailable, map(), tml, false);
        });
    Assert.assertEquals("Cannot remove module ot-1.0.1 which is explicitly given", error.getMessage());

    // install conflicting versions for ot 2/2
    error = Assert.assertThrows(OkapiError.class,
        () -> {
          List<TenantModuleDescriptor> tml = enableList(ot100, ot101);
          DepResolution.install(modsAvailable, map(), tml, false);
        });
    Assert.assertEquals("Cannot remove module ot-1.0.0 which is explicitly given", error.getMessage());
  }

  @Test
  public void testOkapi925() {
    InterfaceDescriptor ont10 = new InterfaceDescriptor("ont", "1.0");
    InterfaceDescriptor int10 = new InterfaceDescriptor("int", "1.0");
    InterfaceDescriptor int20 = new InterfaceDescriptor("int", "2.0");
    InterfaceDescriptor tenant20 = new InterfaceDescriptor("_tenant", "2.0");

    tenant20.setInterfaceType("system");

    ModuleDescriptor st100 = new ModuleDescriptor("st-1.0.0");
    st100.setProvides(new InterfaceDescriptor[]{int10, tenant20});

    ModuleDescriptor st101 = new ModuleDescriptor("st-1.0.1");
    st101.setProvides(new InterfaceDescriptor[]{int20, tenant20});

    ModuleDescriptor ot100 = new ModuleDescriptor("ot-1.0.0");
    ot100.setRequires(new InterfaceDescriptor[]{int10});
    ot100.setProvides(new InterfaceDescriptor[]{tenant20});

    ModuleDescriptor ot101 = new ModuleDescriptor("ot-1.0.1");
    ot101.setRequires(new InterfaceDescriptor[] {int20});
    ot101.setProvides(new InterfaceDescriptor[]{tenant20});

    ModuleDescriptor ot102 = new ModuleDescriptor("ot-1.0.2");
    ot102.setRequires(new InterfaceDescriptor[] {int20, ont10});
    ot102.setProvides(new InterfaceDescriptor[]{tenant20});

    ModuleDescriptor p100 = new ModuleDescriptor("p-1.0.0");
    p100.setProvides(new InterfaceDescriptor[]{ont10, tenant20});

    Map<String, ModuleDescriptor> modsAvailable = map(st100, st101, ot100, ot101, ot102, p100);
    {
      List<TenantModuleDescriptor> tml = enableList(st101, ot101);
      DepResolution.install(modsAvailable, map(ot100, st100), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
      assertUpgrade(tml, 0, st101, st100);
      assertUpgrade(tml, 1, ot101, ot100);
    }
    {
      List<TenantModuleDescriptor> tml = enableList(st101);
      DepResolution.install(modsAvailable, map(ot100, st100), tml, false);
      Assert.assertEquals(Json.encodePrettily(tml), 3, tml.size());
      assertUpgrade(tml, 0, st101, st100);
      assertEnable(tml, 1, p100);
      assertUpgrade(tml, 2, ot102, ot100);
    }
  }

  @Test
  public void testCheckAllConflicts() {
    List<String> errors = DepResolution.checkEnabled(map(mdA100, mdE100));
    Assert.assertTrue(errors.isEmpty());

    errors = DepResolution.checkEnabled(map(mdA100, mdA110));
    Assert.assertEquals("Multiple modules moduleA-1.1.0, moduleA-1.0.0 provide interface int",
        errors.get(0));

    errors = DepResolution.checkEnabled(map(mdA100, mdA110, mdA200));
    Assert.assertEquals("Multiple modules moduleA-1.1.0, moduleA-1.0.0, moduleA-2.0.0 provide interface int",
            errors.get(0));
  }

  // see that order is right. mdE requires mdA (so mdA must be enabled first)
  @Test
  public void testDepOrderSwapEnable() {
    List<TenantModuleDescriptor> tml = enableList(mdE100, mdA100);
    DepResolution.install(map(mdA100, mdA110, mdE100, mdE110), map(), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertEnable(tml, 0, mdA100);
    assertEnable(tml, 1, mdE100);
  }

  // see that order is right. mdE requires mdA (so mdA must be upgraded first)
  @Test
  public void testDepOrderSwapUpgrade() {
    List<TenantModuleDescriptor> tml = enableList(mdE110, mdA110);
    DepResolution.install(map(mdA100, mdA110, mdE100, mdE110), map(mdE100, mdA100), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertUpgrade(tml, 0, mdA110, mdA100);
    assertUpgrade(tml, 1, mdE110, mdE100);
  }

  // see that order is right. mdE requires mdA (so mdA must be enabled first)
  @Test
  public void testDepOrderAlready() {
    List<TenantModuleDescriptor> tml = enableList(mdA100, mdE100);
    DepResolution.install(map(mdA100, mdA110, mdE100, mdE110), map(), tml, false);
    Assert.assertEquals(Json.encodePrettily(tml), 2, tml.size());
    assertEnable(tml, 0, mdA100);
    assertEnable(tml, 1, mdE100);
  }

  @Test
  public void testIterations() {
    int numberOfModules = 5;
    Map<String,ModuleDescriptor> available = new HashMap<>();
    ModuleDescriptor md = null;
    for (int i = 0; i < numberOfModules; i++) {
      md = new ModuleDescriptor("m" + i + "-1.0.0");
      if (i > 0) {
        md.setRequires("i" + (i-1), "1.0");
      }
      InterfaceDescriptor interfaceDescriptor = new InterfaceDescriptor("i" + i, "1.0");
      md.setProvides(new InterfaceDescriptor[] {interfaceDescriptor});
      available.put(md.getId(), md);
    }
    List<TenantModuleDescriptor> tml = enableList(md);
    DepResolution.installMaxIterations(available, map(), tml, false, numberOfModules);

    List<TenantModuleDescriptor> tml1 = enableList(md);
    OkapiError error = Assert.assertThrows(OkapiError.class,
        () -> DepResolution.installMaxIterations(available, map(), tml1, false, numberOfModules - 1));
    Assert.assertEquals("Dependency resolution not completing in " + (numberOfModules - 1) + " iterations", error.getMessage());
  }
}
