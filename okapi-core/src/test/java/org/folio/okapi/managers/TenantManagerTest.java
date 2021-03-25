package org.folio.okapi.managers;

import com.github.dockerjava.api.model.Link;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.InterfaceDescriptor;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.bean.RoutingEntry;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.service.impl.TenantStoreNull;
import org.folio.okapi.util.LockedTypedMap1Faulty;
import org.folio.okapi.util.OkapiError;
import org.folio.okapi.util.TestBase;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class TenantManagerTest extends TestBase {
  private final Logger logger = OkapiLogger.get();

  private Vertx vertx;

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void test1(TestContext context) {
    TenantManager tm = new TenantManager(null, new TenantStoreNull(), true);
    {
      Async async = context.async();
      tm.init(vertx).onComplete(context.asyncAssertSuccess(x -> async.complete()));
      async.await();
    }
    TenantDescriptor td = new TenantDescriptor();
    td.setId("tenant");
    td.setName("first name");
    Tenant tenant = new Tenant(td);
    {
      Async async = context.async();
      tm.insert(tenant).onComplete(res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      td.setName("second name");
      tm.updateDescriptor(td).onComplete(res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      tm.list().onComplete(res -> {
        context.assertTrue(res.succeeded());
        List<TenantDescriptor> list = res.result();
        context.assertEquals(1, list.size());
        TenantDescriptor td1 = list.get(0);
        context.assertEquals("tenant", td1.getId());
        context.assertEquals("second name", td1.getName());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      tm.updateModuleCommit(tenant, "mod-1.0.0", "mod-1.0.1").onComplete(res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      tm.delete(td.getId()).onComplete(res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      tm.delete(td.getId()).onComplete(res -> {
        context.assertTrue(res.failed());
        context.assertEquals(ErrorType.NOT_FOUND, OkapiError.getType(res.cause()));
        async.complete();
      });
      async.await();
    }
  }

  @Test
  public void testTenantStoreFaulty(TestContext context) {
    final String fakeMsg = "fmsg";
    TenantManager tm = new TenantManager(null, new TenantStoreFaulty(ErrorType.INTERNAL, fakeMsg), true);
    {
      Async async = context.async();
      tm.init(vertx).onComplete(x -> async.complete()); // init fails
      async.await();
    }
    TenantDescriptor td = new TenantDescriptor();
    td.setId("tenant");
    td.setName("first name");
    Tenant tenant = new Tenant(td);
    {
      Async async = context.async();
      tm.insert(tenant).onComplete(res -> {
        context.assertTrue(res.failed());
        context.assertEquals(ErrorType.INTERNAL, OkapiError.getType(res.cause()));
        context.assertEquals(fakeMsg, res.cause().getMessage());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      tm.updateDescriptor(td).onComplete(res -> {
        context.assertTrue(res.failed());
        context.assertEquals(ErrorType.INTERNAL, OkapiError.getType(res.cause()));
        context.assertEquals(fakeMsg, res.cause().getMessage());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      tm.list().onComplete(res -> {
        context.assertTrue(res.succeeded()); // ok, no tenantStore in use
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      tm.delete(td.getId()).onComplete(res -> {
        context.assertTrue(res.failed());
        context.assertEquals(ErrorType.INTERNAL, OkapiError.getType(res.cause()));
        context.assertEquals(fakeMsg, res.cause().getMessage());
        async.complete();
      });
      async.await();
    }
  }

  @Test
  public void testTenantsMapFaulty(TestContext context) {
    TenantManager tm = new TenantManager(null, new TenantStoreNull(), true);

    LockedTypedMap1Faulty<Tenant> tenantsMap = new LockedTypedMap1Faulty<>(Tenant.class);
    tm.setTenantsMap(tenantsMap);

    {
      Async async = context.async();
      tm.init(vertx).onComplete(context.asyncAssertSuccess(x -> async.complete()));
      async.await();
    }
    tenantsMap.setGetError("gerror");
    tenantsMap.setAddError(null);
    tenantsMap.setGetKeysError(null);

    TenantDescriptor td = new TenantDescriptor();
    td.setId("tenant");
    td.setName("first name");
    Tenant tenant = new Tenant(td);
    {
      Async async = context.async();
      tm.insert(tenant).onComplete(res -> {
        context.assertTrue(res.failed());
        context.assertEquals(ErrorType.INTERNAL, OkapiError.getType(res.cause()));
        context.assertEquals("gerror", res.cause().getMessage());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      tm.updateDescriptor(td).onComplete(res -> {
        context.assertTrue(res.failed());
        context.assertEquals(ErrorType.INTERNAL, OkapiError.getType(res.cause()));
        context.assertEquals("gerror", res.cause().getMessage());
        async.complete();
      });
      async.await();
    }

    tenantsMap.setGetError(null);
    tenantsMap.setAddError("aerror");
    tenantsMap.setGetKeysError(null);
    {
      Async async = context.async();
      tm.insert(tenant).onComplete(res -> {
        context.assertTrue(res.failed());
        context.assertEquals(ErrorType.INTERNAL, OkapiError.getType(res.cause()));
        context.assertEquals("aerror", res.cause().getMessage());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      tm.updateDescriptor(td).onComplete(res -> {
        context.assertTrue(res.failed());
        context.assertEquals(ErrorType.INTERNAL, OkapiError.getType(res.cause()));
        context.assertEquals("aerror", res.cause().getMessage());
        async.complete();
      });
      async.await();
    }

    tenantsMap.setGetError(null);
    tenantsMap.setAddError("aerror");
    tenantsMap.setGetKeysError(null);
    {
      Async async = context.async();
      tm.list().onComplete(res -> {
        // the add failure is ignored, so empty list is returned
        context.assertTrue(res.succeeded());
        context.assertEquals(0, res.result().size());
        async.complete();
      });
      async.await();
    }

    tenantsMap.setGetError(null);
    tenantsMap.setAddError(null);
    tenantsMap.setGetKeysError("gkerror");
    {
      Async async = context.async();
      tm.list().onComplete(res -> {
        context.assertTrue(res.failed());
        context.assertEquals(ErrorType.INTERNAL, OkapiError.getType(res.cause()));
        context.assertEquals("gkerror", res.cause().getMessage());
        async.complete();
      });
      async.await();
    }
  }

  @Test
  public void testTenantInterfacesNoSystem(TestContext context) {
    ModuleDescriptor md = new ModuleDescriptor();
    JsonObject obj = new JsonObject();
    md.setId("module-1.0.0");
    TenantManager.getTenantInstanceForModule(md,null, md, obj, null, false)
        .onComplete(context.asyncAssertSuccess(instances -> context.assertTrue(instances.isEmpty())));

    InterfaceDescriptor interfaceDescriptor = new InterfaceDescriptor();
    interfaceDescriptor.setId("_tenant");
    interfaceDescriptor.setVersion("1.0");
    InterfaceDescriptor[] provides = new InterfaceDescriptor[1];
    provides[0] = interfaceDescriptor;
    md.setProvides(provides);

    TenantManager.getTenantInstanceForModule(md,null, md, obj, null, false)
        .onComplete(context.asyncAssertSuccess(instances -> context.assertEquals(1, instances.size())));

    TenantManager.getTenantInstanceForModule(md,null, md, obj, null, true)
        .onComplete(context.asyncAssertSuccess(instances -> context.assertTrue(instances.isEmpty())));

    interfaceDescriptor.setVersion("1.1");
    TenantManager.getTenantInstanceForModule(md,null, md, obj, null, false)
        .onComplete(context.asyncAssertSuccess(instances -> context.assertTrue(instances.isEmpty())));

    interfaceDescriptor.setVersion("1.2");
    TenantManager.getTenantInstanceForModule(md, null, md, obj, null, false)
        .onComplete(context.asyncAssertSuccess(instances -> context.assertTrue(instances.isEmpty())));

    interfaceDescriptor.setVersion("2.0");
    TenantManager.getTenantInstanceForModule(md,null, md, obj, null, false)
        .onComplete(context.asyncAssertSuccess(instances -> context.assertTrue(instances.isEmpty())));
  }

  @Test
  public void testTenantInterfacesv1(TestContext context) {
    ModuleDescriptor md = new ModuleDescriptor();
    JsonObject obj = new JsonObject();
    md.setId("module-1.0.0");
    TenantManager.getTenantInstanceForModule(md,null, md, obj, null, false)
        .onComplete(context.asyncAssertSuccess(instances -> context.assertTrue(instances.isEmpty())));

    InterfaceDescriptor interfaceDescriptor = new InterfaceDescriptor();
    interfaceDescriptor.setId("_tenant");
    interfaceDescriptor.setVersion("1.0");
    interfaceDescriptor.setInterfaceType("system");
    InterfaceDescriptor[] provides = new InterfaceDescriptor[1];
    provides[0] = interfaceDescriptor;
    md.setProvides(provides);

    TenantManager.getTenantInstanceForModule(md,null, md, obj, null, false)
        .onComplete(context.asyncAssertSuccess(instances -> {
          context.assertFalse(instances.isEmpty());
          ModuleInstance instance = instances.get(0);
          context.assertEquals(HttpMethod.POST, instance.getMethod());
          context.assertEquals("/_/tenant", instance.getPath());
        }));

    interfaceDescriptor.setVersion("1.1");
    TenantManager.getTenantInstanceForModule(md,null, md, obj, null, false)
        .onComplete(context.asyncAssertSuccess(instances -> context.assertTrue(instances.isEmpty())));

    interfaceDescriptor.setVersion("1.2");
    TenantManager.getTenantInstanceForModule(md,null, md, obj, null, false)
        .onComplete(context.asyncAssertSuccess(instances -> context.assertTrue(instances.isEmpty())));

    interfaceDescriptor.setVersion("1.3");
    TenantManager.getTenantInstanceForModule(md,null, md, obj, null, false)
        .onComplete(context.asyncAssertFailure(cause -> context.assertEquals("Unsupported interface _tenant: 1.3",
            cause.getMessage())));

    interfaceDescriptor.setVersion("2.0");
    TenantManager.getTenantInstanceForModule(md,null, md, obj, null, false)
        .onComplete(context.asyncAssertSuccess(instances -> context.assertTrue(instances.isEmpty())));

    List<RoutingEntry> handlers = new LinkedList<>();
    RoutingEntry tmp = new RoutingEntry();
    tmp.setPathPattern("/_/tenantpath");
    tmp.setMethods(new String[]{"GET", "POST"});
    handlers.add(tmp);

    tmp = new RoutingEntry();
    tmp.setPathPattern("/_/tenant/disable");
    tmp.setMethods(new String[]{"POST"});
    handlers.add(tmp);

    tmp = new RoutingEntry();
    tmp.setPathPattern("/_/tenantpurge");
    tmp.setMethods(new String[]{"DELETE"});
    handlers.add(tmp);

    interfaceDescriptor.setHandlers(handlers.toArray(new RoutingEntry[0]));

    interfaceDescriptor.setVersion("1.1");
    TenantManager.getTenantInstanceForModule(md,null, md, obj, null, false)
        .onComplete(context.asyncAssertSuccess(instances -> {
          context.assertFalse(instances.isEmpty());
          ModuleInstance instance = instances.get(0);
          context.assertEquals(HttpMethod.POST, instance.getMethod());
          context.assertEquals("/_/tenantpath", instance.getPath());
        }));
    TenantManager.getTenantInstanceForModule(md, md, null, obj, null, false)
        .onComplete(context.asyncAssertSuccess(instances -> {
          context.assertFalse(instances.isEmpty());
          ModuleInstance instance = instances.get(0);
          context.assertEquals(HttpMethod.POST, instance.getMethod());
          context.assertEquals("/_/tenant/disable", instance.getPath());
        }));
    TenantManager.getTenantInstanceForModule(md, md, null, obj, null, true)
        .onComplete(context.asyncAssertSuccess(instances -> {
          context.assertEquals(1, instances.size());
          ModuleInstance instance = instances.get(0);
          context.assertEquals(HttpMethod.DELETE, instance.getMethod());
          context.assertEquals("/_/tenantpurge", instance.getPath());
        }));

    interfaceDescriptor.setVersion("1.2");
    TenantManager.getTenantInstanceForModule(md,null, md, obj, null, false)
        .onComplete(context.asyncAssertSuccess(instances -> {
          context.assertEquals(1, instances.size());
          ModuleInstance instance = instances.get(0);
          context.assertEquals(HttpMethod.POST, instance.getMethod());
          context.assertEquals("/_/tenantpath", instance.getPath());
        }));

    interfaceDescriptor.setVersion("2.0");
    TenantManager.getTenantInstanceForModule(md,null, md, obj, null, false)
        .onComplete(context.asyncAssertSuccess(instances -> {
          context.assertEquals(1, instances.size());
          ModuleInstance instance = instances.get(0);
          context.assertEquals(HttpMethod.POST, instance.getMethod());
          context.assertEquals("/_/tenantpath", instance.getPath());
        }));

  }

  @Test
  public void testTenantInterfacesv2(TestContext context) {
    ModuleDescriptor md = new ModuleDescriptor();
    JsonObject obj = new JsonObject();
    md.setId("module-1.0.0");
    TenantManager.getTenantInstanceForModule(md,null, md, obj, null, false)
        .onComplete(context.asyncAssertSuccess(instances -> context.assertTrue(instances.isEmpty())));

    InterfaceDescriptor interfaceDescriptor = new InterfaceDescriptor();
    interfaceDescriptor.setId("_tenant");
    interfaceDescriptor.setVersion("2.0");
    interfaceDescriptor.setInterfaceType("system");
    InterfaceDescriptor[] provides = new InterfaceDescriptor[1];
    provides[0] = interfaceDescriptor;
    md.setProvides(provides);

    List<RoutingEntry> handlers = new LinkedList<>();
    RoutingEntry tmp = new RoutingEntry();
    tmp.setPathPattern("/_/tenantpath");
    tmp.setMethods(new String [] { "POST"});
    handlers.add(tmp);

    tmp = new RoutingEntry();
    tmp.setPathPattern("/_/tenantpath/{id}");
    tmp.setMethods(new String [] { "DELETE", "GET"});
    handlers.add(tmp);

    interfaceDescriptor.setHandlers(handlers.toArray(new RoutingEntry[0]));

    TenantManager.getTenantInstanceForModule(md,null, md, obj, null, false)
        .onComplete(context.asyncAssertSuccess(instances -> {
          context.assertEquals(3, instances.size());
          ModuleInstance instance = instances.get(0);
          context.assertEquals(HttpMethod.POST, instance.getMethod());
          context.assertEquals("/_/tenantpath", instance.getPath());
          instance = instances.get(1);
          context.assertEquals(HttpMethod.GET, instance.getMethod());
          context.assertEquals("/_/tenantpath/{id}", instance.getPath());
          instance = instances.get(2);
          context.assertEquals(HttpMethod.DELETE, instance.getMethod());
          context.assertEquals("/_/tenantpath/{id}", instance.getPath());
          context.assertFalse(obj.getBoolean("purge"));
        }));
    TenantManager.getTenantInstanceForModule(md, md, null, obj, null, false)
        .onComplete(context.asyncAssertSuccess(instances -> {
          context.assertEquals(3, instances.size());
          ModuleInstance instance = instances.get(0);
          context.assertEquals(HttpMethod.POST, instance.getMethod());
          context.assertEquals("/_/tenantpath", instance.getPath());
          instance = instances.get(1);
          context.assertEquals(HttpMethod.GET, instance.getMethod());
          context.assertEquals("/_/tenantpath/{id}", instance.getPath());
          instance = instances.get(2);
          context.assertEquals(HttpMethod.DELETE, instance.getMethod());
          context.assertEquals("/_/tenantpath/{id}", instance.getPath());
          context.assertFalse(obj.getBoolean("purge"));
        }));
    TenantManager.getTenantInstanceForModule(md, md, null, obj, null, true)
        .onComplete(context.asyncAssertSuccess(instances -> {
          context.assertEquals(3, instances.size());
          ModuleInstance instance = instances.get(0);
          context.assertEquals(HttpMethod.POST, instance.getMethod());
          context.assertEquals("/_/tenantpath", instance.getPath());
          instance = instances.get(1);
          context.assertEquals(HttpMethod.GET, instance.getMethod());
          context.assertEquals("/_/tenantpath/{id}", instance.getPath());
          instance = instances.get(2);
          context.assertEquals(HttpMethod.DELETE, instance.getMethod());
          context.assertEquals("/_/tenantpath/{id}", instance.getPath());
          context.assertTrue(obj.getBoolean("purge"));
        }));
  }
}
