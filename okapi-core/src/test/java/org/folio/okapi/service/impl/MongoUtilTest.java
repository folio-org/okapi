package org.folio.okapi.service.impl;

import com.mongodb.client.model.changestream.ChangeStreamDocument;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.mongo.AggregateOptions;
import io.vertx.ext.mongo.BulkOperation;
import io.vertx.ext.mongo.BulkWriteOptions;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.IndexModel;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.MongoClientBulkWriteResult;
import io.vertx.ext.mongo.MongoClientDeleteResult;
import io.vertx.ext.mongo.MongoClientUpdateResult;
import io.vertx.ext.mongo.MongoGridFsClient;
import io.vertx.ext.mongo.UpdateOptions;
import io.vertx.ext.mongo.WriteOption;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.List;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class MongoUtilTest {

  public class MongoClientDeleteResult0Hits extends MongoClientDeleteResult {
    @Override
    public long getRemovedCount() {
      return 0;
    }
  }

  class FakeMongoClient implements MongoClient {

    @Override
    public MongoClient save(String collection, JsonObject document, Handler<AsyncResult<String>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient saveWithOptions(String collection, JsonObject document, WriteOption writeOption, Handler<AsyncResult<String>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient insert(String collection, JsonObject document, Handler<AsyncResult<String>> resultHandler) {
      resultHandler.handle(Future.failedFuture("insert failed"));
      return this;
    }

    @Override
    public MongoClient insertWithOptions(String collection, JsonObject document, WriteOption writeOption, Handler<AsyncResult<String>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient updateCollection(String collection, JsonObject query, JsonObject update, Handler<AsyncResult<MongoClientUpdateResult>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient updateCollectionWithOptions(String collection, JsonObject query, JsonObject update, UpdateOptions options, Handler<AsyncResult<MongoClientUpdateResult>> resultHandler) {
      resultHandler.handle(Future.failedFuture("updateCollectionWithOptions failed"));
      return this;
    }

    @Override
    public MongoClient replaceDocuments(String collection, JsonObject query, JsonObject replace, Handler<AsyncResult<MongoClientUpdateResult>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient replaceDocumentsWithOptions(String collection, JsonObject query, JsonObject replace, UpdateOptions options, Handler<AsyncResult<MongoClientUpdateResult>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient bulkWrite(String collection, List<BulkOperation> operations, Handler<AsyncResult<MongoClientBulkWriteResult>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient bulkWriteWithOptions(String collection, List<BulkOperation> operations, BulkWriteOptions bulkWriteOptions, Handler<AsyncResult<MongoClientBulkWriteResult>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient find(String collection, JsonObject query, Handler<AsyncResult<List<JsonObject>>> resultHandler) {
      resultHandler.handle(Future.failedFuture("find failed"));
      return this;
    }

    @Override
    public ReadStream<JsonObject> findBatch(String collection, JsonObject query) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient findWithOptions(String collection, JsonObject query, FindOptions options, Handler<AsyncResult<List<JsonObject>>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ReadStream<JsonObject> findBatchWithOptions(String collection, JsonObject query, FindOptions options) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient findOne(String collection, JsonObject query, JsonObject fields, Handler<AsyncResult<JsonObject>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient findOneAndUpdate(String collection, JsonObject query, JsonObject update, Handler<AsyncResult<JsonObject>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient findOneAndUpdateWithOptions(String collection, JsonObject query, JsonObject update, FindOptions findOptions, UpdateOptions updateOptions, Handler<AsyncResult<JsonObject>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient findOneAndReplace(String collection, JsonObject query, JsonObject replace, Handler<AsyncResult<JsonObject>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient findOneAndReplaceWithOptions(String collection, JsonObject query, JsonObject replace, FindOptions findOptions, UpdateOptions updateOptions, Handler<AsyncResult<JsonObject>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient findOneAndDelete(String collection, JsonObject query, Handler<AsyncResult<JsonObject>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient findOneAndDeleteWithOptions(String collection, JsonObject query, FindOptions findOptions, Handler<AsyncResult<JsonObject>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient count(String collection, JsonObject query, Handler<AsyncResult<Long>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient removeDocuments(String collection, JsonObject query, Handler<AsyncResult<MongoClientDeleteResult>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient removeDocumentsWithOptions(String collection, JsonObject query, WriteOption writeOption, Handler<AsyncResult<MongoClientDeleteResult>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient removeDocument(String collection, JsonObject query, Handler<AsyncResult<MongoClientDeleteResult>> resultHandler) {
      if ("404".equals(query.getString("_id"))) {
        MongoClientDeleteResult res = new MongoClientDeleteResult0Hits();
        resultHandler.handle(Future.succeededFuture(res));
      } else {
        resultHandler.handle(Future.failedFuture("removeDocument failed"));
      }
      return this;
    }

    @Override
    public MongoClient removeDocumentWithOptions(String collection, JsonObject query, WriteOption writeOption, Handler<AsyncResult<MongoClientDeleteResult>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient createCollection(String collectionName, Handler<AsyncResult<Void>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient getCollections(Handler<AsyncResult<List<String>>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient dropCollection(String collection, Handler<AsyncResult<Void>> resultHandler) {
      resultHandler.handle(Future.failedFuture("dropCollection failed"));
      return this;
    }

    @Override
    public MongoClient createIndex(String collection, JsonObject key, Handler<AsyncResult<Void>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient createIndexWithOptions(String collection, JsonObject key, IndexOptions options, Handler<AsyncResult<Void>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient listIndexes(String collection, Handler<AsyncResult<JsonArray>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient dropIndex(String collection, String indexName, Handler<AsyncResult<Void>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient runCommand(String commandName, JsonObject command, Handler<AsyncResult<JsonObject>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient distinct(String collection, String fieldName, String resultClassname, Handler<AsyncResult<JsonArray>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient distinctWithQuery(String collection, String fieldName, String resultClassname, JsonObject query, Handler<AsyncResult<JsonArray>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ReadStream<JsonObject> distinctBatch(String collection, String fieldName, String resultClassname) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ReadStream<JsonObject> distinctBatchWithQuery(String collection, String fieldName, String resultClassname, JsonObject query) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ReadStream<JsonObject> distinctBatchWithQuery(String collection, String fieldName, String resultClassname, JsonObject query, int batchSize) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ReadStream<JsonObject> aggregate(String collection, JsonArray pipeline) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ReadStream<JsonObject> aggregateWithOptions(String collection, JsonArray pipeline, AggregateOptions options) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ReadStream<ChangeStreamDocument<JsonObject>> watch(String s, JsonArray jsonArray, boolean b, int i) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<String> save(String string, JsonObject jo) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<String> saveWithOptions(String string, JsonObject jo, WriteOption wo) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<String> insert(String string, JsonObject jo) {
      return Future.failedFuture("insert failed");
    }

    @Override
    public Future<String> insertWithOptions(String string, JsonObject jo, WriteOption wo) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<MongoClientUpdateResult> updateCollection(String string, JsonObject jo, JsonObject jo1) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<MongoClientUpdateResult> updateCollectionWithOptions(String string, JsonObject jo, JsonObject jo1, UpdateOptions uo) {
      return Future.failedFuture("updateCollectionWithOptions failed");
    }

    @Override
    public Future<MongoClientUpdateResult> replaceDocuments(String string, JsonObject jo, JsonObject jo1) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<MongoClientUpdateResult> replaceDocumentsWithOptions(String string, JsonObject jo, JsonObject jo1, UpdateOptions uo) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<MongoClientBulkWriteResult> bulkWrite(String string, List<BulkOperation> list) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<MongoClientBulkWriteResult> bulkWriteWithOptions(String string, List<BulkOperation> list, BulkWriteOptions bwo) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<List<JsonObject>> find(String string, JsonObject jo) {
      return Future.failedFuture("find failed");
    }

    @Override
    public Future<List<JsonObject>> findWithOptions(String string, JsonObject jo, FindOptions fo) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<JsonObject> findOne(String string, JsonObject jo, JsonObject jo1) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<JsonObject> findOneAndUpdate(String string, JsonObject jo, JsonObject jo1) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<JsonObject> findOneAndUpdateWithOptions(String string, JsonObject jo, JsonObject jo1, FindOptions fo, UpdateOptions uo) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<JsonObject> findOneAndReplace(String string, JsonObject jo, JsonObject jo1) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<JsonObject> findOneAndReplaceWithOptions(String string, JsonObject jo, JsonObject jo1, FindOptions fo, UpdateOptions uo) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<JsonObject> findOneAndDelete(String string, JsonObject jo) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<JsonObject> findOneAndDeleteWithOptions(String string, JsonObject jo, FindOptions fo) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<Long> count(String string, JsonObject jo) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<MongoClientDeleteResult> removeDocuments(String string, JsonObject jo) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<MongoClientDeleteResult> removeDocumentsWithOptions(String string, JsonObject jo, WriteOption wo) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
    public Future<MongoClientDeleteResult> removeDocumentWithOptions(String string, JsonObject jo, WriteOption wo) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<Void> createCollection(String string) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<List<String>> getCollections() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<Void> dropCollection(String string) {
      return Future.failedFuture("dropCollection failed");
    }

    @Override
    public Future<Void> createIndex(String string, JsonObject jo) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<Void> createIndexWithOptions(String string, JsonObject jo, IndexOptions io) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient createIndexes(String string, List<IndexModel> list, Handler<AsyncResult<Void>> hndlr) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<Void> createIndexes(String string, List<IndexModel> list) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<JsonArray> listIndexes(String string) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<Void> dropIndex(String string, String string1) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<JsonObject> runCommand(String string, JsonObject jo) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<JsonArray> distinct(String string, String string1, String string2) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<JsonArray> distinctWithQuery(String string, String string1, String string2, JsonObject jo) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient createDefaultGridFsBucketService(Handler<AsyncResult<MongoGridFsClient>> hndlr) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<MongoGridFsClient> createDefaultGridFsBucketService() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MongoClient createGridFsBucketService(String string, Handler<AsyncResult<MongoGridFsClient>> hndlr) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<MongoGridFsClient> createGridFsBucketService(String string) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void close(Handler<AsyncResult<Void>> hndlr) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<Void> close() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

  }

  @Test
  public void testDelete(TestContext context) {
    MongoClient cli = new FakeMongoClient();
    MongoUtil<String> util = new MongoUtil<>("collection", cli);
    util.delete("1").onComplete(res -> {
      context.assertTrue(res.failed());
      context.assertEquals("removeDocument failed", res.cause().getMessage());
    });
  }

  @Test
  public void testDelete404(TestContext context) {
    MongoClient cli = new FakeMongoClient();
    MongoUtil<String> util = new MongoUtil<>("collection", cli);
    util.delete("404").onComplete(res -> {
      context.assertTrue(res.succeeded());
      context.assertEquals(Boolean.FALSE, res.result());
    });
  }


  @Test
  public void testInit(TestContext context) {
    MongoClient cli = new FakeMongoClient();
    MongoUtil<String> util = new MongoUtil<>("collection", cli);
    util.init(true).onComplete(res -> {
      context.assertTrue(res.failed());
      context.assertEquals("dropCollection failed", res.cause().getMessage());
    });
  }

  @Test
  public void testAdd(TestContext context) {
    MongoClient cli = new FakeMongoClient();
    MongoUtil<DeploymentDescriptor> util = new MongoUtil<>("collection", cli);
    util.add(new DeploymentDescriptor(), "1").onComplete(res -> {
      context.assertTrue(res.failed());
      context.assertEquals("updateCollectionWithOptions failed", res.cause().getMessage());
    });
  }

  @Test
  public void testInsert(TestContext context) {
    MongoClient cli = new FakeMongoClient();
    MongoUtil<DeploymentDescriptor> util = new MongoUtil<>("collection", cli);
    util.insert(new DeploymentDescriptor(), "1").onComplete(res -> {
      context.assertTrue(res.failed());
      context.assertEquals("insert failed", res.cause().getMessage());
    });
  }

  @Test
  public void testGetAll(TestContext context) {
    MongoClient cli = new FakeMongoClient();
    MongoUtil<DeploymentDescriptor> util = new MongoUtil<>("collection", cli);
    util.getAll(DeploymentDescriptor.class).onComplete(res -> {
      context.assertTrue(res.failed());
      context.assertEquals("find failed", res.cause().getMessage());
    });
  }

}
