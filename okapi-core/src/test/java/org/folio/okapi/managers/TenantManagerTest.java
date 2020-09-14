package org.folio.okapi.managers;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.List;

import org.apache.logging.log4j.Logger;
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
    TenantManager tm = new TenantManager(null, new TenantStoreNull());
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
      tm.updateModuleCommit(td.getId(), "mod-1.0.0", "mod-1.0.1").onComplete(res -> {
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
    TenantManager tm = new TenantManager(null, new TenantStoreFaulty(ErrorType.INTERNAL, fakeMsg));
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
    TenantManager tm = new TenantManager(null, new TenantStoreNull());

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
  public void handleTimerForNonexistingTenant(TestContext context) {
    TenantManager tenantManager = new TenantManager(null, new TenantStoreNull());
    tenantManager.getTimers().add("tenantId_moduleId_0");
    tenantManager.init(Vertx.vertx()).onComplete(context.asyncAssertSuccess(done -> {
      tenantManager.handleTimer("tenantId", "moduleId", 0);
      Assert.assertEquals(0, tenantManager.getTimers().size());
    }));
  }
}
