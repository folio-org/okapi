package org.folio.okapi.service.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.spy;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.MongoClientDeleteResult;
import io.vertx.ext.mongo.MongoClientUpdateResult;
import io.vertx.ext.mongo.UpdateOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.List;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class MongoUtilTest {

  public static class MongoClientDeleteResult0Hits extends MongoClientDeleteResult {
    @Override
    public long getRemovedCount() {
      return 0;
    }
  }

  public abstract static class FakeMongoClient implements MongoClient {

    @Override
    public Future<String> insert(String string, JsonObject jo) {
      return Future.failedFuture("insert failed");
    }

    @Override
    public Future<MongoClientUpdateResult> updateCollectionWithOptions(String string, JsonObject jo, JsonObject jo1, UpdateOptions uo) {
      return Future.failedFuture("updateCollectionWithOptions failed");
    }

    @Override
    public Future<List<JsonObject>> find(String string, JsonObject jo) {
      return Future.failedFuture("find failed");
    }

    @Override
    public Future<MongoClientDeleteResult> removeDocument(String string, JsonObject jo) {
      if ("404".equals(jo.getString("_id"))) {
        MongoClientDeleteResult res = new MongoClientDeleteResult0Hits();
        return Future.succeededFuture(res);
      }
      return Future.failedFuture("removeDocument failed");
    }

    @Override
    public Future<Void> dropCollection(String string) {
      return Future.failedFuture("dropCollection failed");
    }

  }

  private <T> MongoUtil<T> mongoUtil() {
    return new MongoUtil<>("collection", spy(FakeMongoClient.class));
  }

  @Test
  public void testDelete(TestContext context) {
    mongoUtil().delete("1").onComplete(context.asyncAssertFailure(cause -> {
      assertThat(cause.getMessage(), is("removeDocument failed"));
    }));
  }

  @Test
  public void testDelete404(TestContext context) {
    mongoUtil().delete("404").onComplete(context.asyncAssertSuccess(result -> {
      assertThat(result, is(Boolean.FALSE));
    }));
  }

  @Test
  public void testInit(TestContext context) {
    mongoUtil().init(true).onComplete(context.asyncAssertFailure(cause -> {
      assertThat(cause.getMessage(), is("dropCollection failed"));
    }));
  }

  @Test
  public void testAdd(TestContext context) {
    mongoUtil().add(new DeploymentDescriptor(), "1")
        .onComplete(context.asyncAssertFailure(cause -> {
          assertThat(cause.getMessage(), is("updateCollectionWithOptions failed"));
        }));
  }

  @Test
  public void testInsert(TestContext context) {
    mongoUtil().insert(new DeploymentDescriptor(), "1")
        .onComplete(context.asyncAssertFailure(cause -> {
          assertThat(cause.getMessage(), is("insert failed"));
        }));
  }

  @Test
  public void testGetAll(TestContext context) {
    MongoUtil<DeploymentDescriptor> mongoUtil = mongoUtil();
    mongoUtil.getAll(DeploymentDescriptor.class).onComplete(context.asyncAssertFailure(cause -> {
      assertThat(cause.getMessage(), is("find failed"));
    }));
  }

}
