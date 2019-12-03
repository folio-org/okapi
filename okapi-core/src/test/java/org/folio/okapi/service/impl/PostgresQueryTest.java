package org.folio.okapi.service.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.AsyncSQLClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.SQLOperations;
import io.vertx.ext.sql.SQLOptions;
import io.vertx.ext.sql.SQLRowStream;
import io.vertx.ext.sql.TransactionIsolation;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.List;
import org.folio.okapi.common.ErrorType;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class PostgresQueryTest {

  Vertx vertx;

  class FakeSQLConnection implements SQLConnection {

    @Override
    public SQLConnection setOptions(SQLOptions options) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLConnection setAutoCommit(boolean autoCommit, Handler<AsyncResult<Void>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLConnection execute(String sql, Handler<AsyncResult<Void>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLConnection query(String sql, Handler<AsyncResult<ResultSet>> resultHandler) {
      Promise promise = Promise.promise();
      promise.fail("fake query failed");
      resultHandler.handle(promise.future());
      return this;
    }

    @Override
    public SQLConnection queryStream(String sql, Handler<AsyncResult<SQLRowStream>> handler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLConnection queryWithParams(String sql, JsonArray params, Handler<AsyncResult<ResultSet>> resultHandler) {
      Promise promise = Promise.promise();
      promise.fail("fake queryWithParams failed");
      resultHandler.handle(promise.future());
      return this;
    }

    @Override
    public SQLConnection queryStreamWithParams(String sql, JsonArray params, Handler<AsyncResult<SQLRowStream>> handler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLConnection update(String sql, Handler<AsyncResult<UpdateResult>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLConnection updateWithParams(String sql, JsonArray params, Handler<AsyncResult<UpdateResult>> resultHandler) {
      Promise promise = Promise.promise();
      promise.fail("fake updateWithParams failed");
      resultHandler.handle(promise.future());
      return this;
    }

    @Override
    public SQLConnection call(String sql, Handler<AsyncResult<ResultSet>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLConnection callWithParams(String sql, JsonArray params, JsonArray outputs, Handler<AsyncResult<ResultSet>> resultHandler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void close(Handler<AsyncResult<Void>> handler) {
      Promise promise = Promise.promise();
      promise.complete();
      handler.handle(promise.future());
    }

    @Override
    public void close() {
    }

    @Override
    public SQLConnection commit(Handler<AsyncResult<Void>> handler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLConnection rollback(Handler<AsyncResult<Void>> handler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLConnection batch(List<String> sqlStatements, Handler<AsyncResult<List<Integer>>> handler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLConnection batchWithParams(String sqlStatement, List<JsonArray> args, Handler<AsyncResult<List<Integer>>> handler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLConnection batchCallableWithParams(String sqlStatement, List<JsonArray> inArgs, List<JsonArray> outArgs, Handler<AsyncResult<List<Integer>>> handler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLConnection setTransactionIsolation(TransactionIsolation isolation, Handler<AsyncResult<Void>> handler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLConnection getTransactionIsolation(Handler<AsyncResult<TransactionIsolation>> handler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

  }

  class FakeSQLClient implements AsyncSQLClient {

    final boolean getConnectionSucceed;

    public FakeSQLClient(Vertx vertx, JsonObject conf) {
      getConnectionSucceed = conf.containsKey("port");
    }

    @Override
    public SQLClient getConnection(Handler<AsyncResult<SQLConnection>> hndlr) {
      Promise promise = Promise.promise();
      if (getConnectionSucceed) {
        promise.complete(new FakeSQLConnection());
      } else {
        promise.fail("fake getConnection failed");
      }
      hndlr.handle(promise.future());
      return this;
    }

    @Override
    public void close(Handler<AsyncResult<Void>> hndlr) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void close() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLClient query(String sql, Handler<AsyncResult<ResultSet>> handler) {
      return AsyncSQLClient.super.query(sql, handler); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLClient queryStream(String sql, Handler<AsyncResult<SQLRowStream>> handler) {
      return AsyncSQLClient.super.queryStream(sql, handler); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLClient queryStreamWithParams(String sql, JsonArray params, Handler<AsyncResult<SQLRowStream>> handler) {
      return AsyncSQLClient.super.queryStreamWithParams(sql, params, handler); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLClient queryWithParams(String sql, JsonArray arguments, Handler<AsyncResult<ResultSet>> handler) {
      return AsyncSQLClient.super.queryWithParams(sql, arguments, handler);
    }

    @Override
    public SQLClient update(String sql, Handler<AsyncResult<UpdateResult>> handler) {
      return AsyncSQLClient.super.update(sql, handler); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLClient updateWithParams(String sql, JsonArray params, Handler<AsyncResult<UpdateResult>> handler) {
      return AsyncSQLClient.super.updateWithParams(sql, params, handler); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLClient call(String sql, Handler<AsyncResult<ResultSet>> handler) {
      return AsyncSQLClient.super.call(sql, handler); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLClient callWithParams(String sql, JsonArray params, JsonArray outputs, Handler<AsyncResult<ResultSet>> handler) {
      return AsyncSQLClient.super.callWithParams(sql, params, outputs, handler); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLOperations querySingle(String sql, Handler<AsyncResult<JsonArray>> handler) {
      return AsyncSQLClient.super.querySingle(sql, handler); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SQLOperations querySingleWithParams(String sql, JsonArray arguments, Handler<AsyncResult<JsonArray>> handler) {
      return AsyncSQLClient.super.querySingleWithParams(sql, arguments, handler); //To change body of generated methods, choose Tools | Templates.
    }
  }

  class FakeHandle extends PostgresHandle {

    JsonObject conf;

    protected JsonObject getConf() {
      return this.conf;
    }

    protected FakeHandle(Vertx vertx, JsonObject conf) {
      super(vertx, conf);
    }

    @Override
    public AsyncSQLClient createSQLClient(Vertx vertx, JsonObject conf) {
      this.conf = conf;
      return new FakeSQLClient(vertx, conf);
    }
  }

  @Before
  public void setup() {
    vertx = Vertx.vertx();
  }

  @After
  public void tearDown() {
    vertx.close();
  }

  @Test
  public void testConstructor() {
    JsonObject obj = new JsonObject();
    FakeHandle h = new FakeHandle(vertx, obj);
    Assert.assertEquals("{\"username\":\"okapi\",\"password\":\"okapi25\","
      + "\"database\":\"okapi\"}",
      h.getConf().encode());

    obj.put("postgres_port", "x");
    h = new FakeHandle(vertx, obj);
    Assert.assertEquals("{\"username\":\"okapi\",\"password\":\"okapi25\","
      + "\"database\":\"okapi\"}",
      h.getConf().encode());

    obj.put("postgres_port", "123");
    obj.put("postgres_host", "my.service");
    h = new FakeHandle(vertx, obj);
    Assert.assertEquals("{\"host\":\"my.service\",\"port\":123,"
      + "\"username\":\"okapi\",\"password\":\"okapi25\",\"database\":\"okapi\"}",
      h.getConf().encode());
  }

  @Test
  public void testGetConnectionFail(TestContext context) {
    JsonObject obj = new JsonObject();
    FakeHandle h = new FakeHandle(vertx, obj);
    h.getConnection(res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.INTERNAL, res.getType());
      context.assertEquals("fake getConnection failed", res.cause().getMessage());
    });
  }

  @Test
  public void testGetConnectionSucceed(TestContext context) {
    JsonObject obj = new JsonObject();
    obj.put("postgres_port", "5432");
    FakeHandle h = new FakeHandle(vertx, obj);
    h.getConnection(res -> {
      context.assertTrue(res.succeeded());
    });
  }

  @Test
  public void testQueryWithParams(TestContext context) {
    JsonObject obj = new JsonObject();
    obj.put("postgres_port", "5432");
    FakeHandle h = new FakeHandle(vertx, obj);

    PostgresQuery q = new PostgresQuery(h);
    q.queryWithParams("select", null, res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.INTERNAL, res.getType());
      context.assertEquals("fake queryWithParams failed", res.cause().getMessage());
    });
  }

  @Test
  public void testUpdateWithParams(TestContext context) {
    JsonObject obj = new JsonObject();
    obj.put("postgres_port", "5432");
    FakeHandle h = new FakeHandle(vertx, obj);

    PostgresQuery q = new PostgresQuery(h);
    q.updateWithParams("select", null, res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.INTERNAL, res.getType());
      context.assertEquals("fake updateWithParams failed", res.cause().getMessage());
    });
  }

  @Test
  public void testQuery(TestContext context) {
    JsonObject obj = new JsonObject();
    obj.put("postgres_port", "5432");
    FakeHandle h = new FakeHandle(vertx, obj);

    PostgresQuery q = new PostgresQuery(h);
    q.query("select", res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.INTERNAL, res.getType());
      context.assertEquals("fake query failed", res.cause().getMessage());
    });
  }

  @Test
  public void testClose(TestContext context) {
    JsonObject obj = new JsonObject();
    obj.put("postgres_port", "5432");
    FakeHandle h = new FakeHandle(vertx, obj);
    PostgresQuery q = new PostgresQuery(h);
    q.close();
  }

}
