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
import io.vertx.ext.sql.SQLRowStream;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.okapi.common.ErrorType;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class PostgresQueryTest {

  Vertx vertx;
  
  class FakeSQLClient implements AsyncSQLClient {
    
    final boolean getConnectionSucceed;
    
    public FakeSQLClient(Vertx vertx, JsonObject conf) {
      getConnectionSucceed = conf.containsKey("port");
    }
    
    @Override
    public SQLClient getConnection(Handler<AsyncResult<SQLConnection>> hndlr) {
      Promise promise = Promise.promise();
      if (getConnectionSucceed) {
        promise.complete(null);
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
      return AsyncSQLClient.super.queryWithParams(sql, arguments, handler); //To change body of generated methods, choose Tools | Templates.
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
      System.out.println("createSQLClient");
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
      +"\"database\":\"okapi\"}",
      h.getConf().encode());    
    
    obj.put("postgres_port", "x");
    h = new FakeHandle(vertx, obj);
    Assert.assertEquals("{\"username\":\"okapi\",\"password\":\"okapi25\","
      +"\"database\":\"okapi\"}",
      h.getConf().encode());    

    obj.put("postgres_port", "123");
    obj.put("postgres_host", "my.service");
    h = new FakeHandle(vertx, obj);
    Assert.assertEquals("{\"host\":\"my.service\",\"port\":123,"
      +"\"username\":\"okapi\",\"password\":\"okapi25\",\"database\":\"okapi\"}",
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
      context.assertEquals(null, res.result());
    });
  }

}
