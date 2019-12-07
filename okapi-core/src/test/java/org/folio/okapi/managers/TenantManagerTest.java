package org.folio.okapi.managers;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.List;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.service.impl.TenantStoreNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class TenantManagerTest {

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
      tm.init(vertx, res -> async.complete());
      async.await();
    }
    TenantDescriptor td = new TenantDescriptor();
    td.setId("tenant");
    td.setName("first name");
    Tenant tenant = new Tenant(td);
    {
      Async async = context.async();
      tm.insert(tenant, res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      td.setName("second name");
      tm.updateDescriptor(td, res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      tm.list(res -> {
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
      tm.updateModuleCommit(td.getId(), "mod-1.0.0", "mod-1.0.1", res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      tm.delete(td.getId(), res -> {
        context.assertTrue(res.succeeded());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      tm.delete(td.getId(), res -> {
        context.assertTrue(res.failed());
        context.assertEquals(ErrorType.NOT_FOUND, res.getType());
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
      tm.init(vertx, res -> async.complete());
      async.await();
    }
    TenantDescriptor td = new TenantDescriptor();
    td.setId("tenant");
    td.setName("first name");
    Tenant tenant = new Tenant(td);
    {
      Async async = context.async();
      tm.insert(tenant, res -> {
        context.assertTrue(res.failed());
        context.assertEquals(ErrorType.INTERNAL, res.getType());
        context.assertEquals(fakeMsg, res.cause().getMessage());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      tm.updateDescriptor(td, res -> {
        context.assertTrue(res.failed());
        context.assertEquals(ErrorType.INTERNAL, res.getType());
        context.assertEquals(fakeMsg, res.cause().getMessage());
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      tm.list(res -> {
        context.assertTrue(res.succeeded()); // ok, no tenantStore in use
        async.complete();
      });
      async.await();
    }
    {
      Async async = context.async();
      tm.delete(td.getId(), res -> {
        context.assertTrue(res.failed());
        context.assertEquals(ErrorType.INTERNAL, res.getType());
        context.assertEquals(fakeMsg, res.cause().getMessage());
        async.complete();
      });
      async.await();
    }
  }
}
