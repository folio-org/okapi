package org.folio.okapi.service.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.sqlclient.PreparedQuery;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.Transaction;
import io.vertx.sqlclient.Tuple;
import java.util.List;
import java.util.stream.Collector;
import org.folio.okapi.common.ErrorType;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class PostgresQueryTest {

  Vertx vertx;

  class FakeSqlConnection implements SqlConnection {

    @Override
    public SqlConnection prepare(String string, Handler<AsyncResult<PreparedQuery>> hndlr) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<PreparedQuery> prepare(String string) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SqlConnection exceptionHandler(Handler<Throwable> hndlr) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SqlConnection closeHandler(Handler<Void> hndlr) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Transaction begin() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean isSSL() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void close() {
    }

    @Override
    public SqlConnection preparedQuery(String string, Handler<AsyncResult<RowSet<Row>>> hndlr) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <R> SqlConnection preparedQuery(String string, Collector<Row, ?, R> clctr, Handler<AsyncResult<SqlResult<R>>> hndlr) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SqlConnection query(String string, Handler<AsyncResult<RowSet<Row>>> hndlr) {
      hndlr.handle(Future.failedFuture("fake query failed"));
      return this;
    }

    @Override
    public <R> SqlConnection query(String string, Collector<Row, ?, R> clctr, Handler<AsyncResult<SqlResult<R>>> hndlr) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SqlConnection preparedQuery(String string, Tuple tuple, Handler<AsyncResult<RowSet<Row>>> hndlr) {
      hndlr.handle(Future.failedFuture("fake preparedQuery failed"));
      return this;
    }

    @Override
    public <R> SqlConnection preparedQuery(String string, Tuple tuple, Collector<Row, ?, R> clctr, Handler<AsyncResult<SqlResult<R>>> hndlr) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SqlConnection preparedBatch(String string, List<Tuple> list, Handler<AsyncResult<RowSet<Row>>> hndlr) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <R> SqlConnection preparedBatch(String string, List<Tuple> list, Collector<Row, ?, R> clctr, Handler<AsyncResult<SqlResult<R>>> hndlr) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<RowSet<Row>> query(String string) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <R> Future<SqlResult<R>> query(String string, Collector<Row, ?, R> clctr) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<RowSet<Row>> preparedQuery(String string) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <R> Future<SqlResult<R>> preparedQuery(String string, Collector<Row, ?, R> clctr) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<RowSet<Row>> preparedQuery(String string, Tuple tuple) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <R> Future<SqlResult<R>> preparedQuery(String string, Tuple tuple, Collector<Row, ?, R> clctr) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<RowSet<Row>> preparedBatch(String string, List<Tuple> list) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <R> Future<SqlResult<R>> preparedBatch(String string, List<Tuple> list, Collector<Row, ?, R> clctr) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

  }

  class FakeHandle extends PostgresHandle {

    JsonObject conf;

    protected FakeHandle(Vertx vertx, JsonObject conf) {
      super(vertx, conf);
      this.conf = conf;
    }

    @Override
    public void getConnection(Handler<AsyncResult<SqlConnection>> con) {
      if ("0".equals(conf.getString("postgres_port"))) {
        con.handle(Future.failedFuture("getConnection failed"));
        return;
      }
      con.handle(Future.succeededFuture(new FakeSqlConnection()));
    }
  }

  class PostgresHandleGetConnectionFail extends PostgresHandle {

    protected PostgresHandleGetConnectionFail(Vertx vertx, JsonObject conf) {
      super(vertx, conf);
    }

    @Override
    public void getConnection(Handler<AsyncResult<SqlConnection>> con) {
      con.handle(Future.succeededFuture(new FakeSqlConnection()));
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
    Assert.assertEquals("okapi", h.getOptions().getUser());
    Assert.assertEquals("okapi25", h.getOptions().getPassword());
    Assert.assertEquals("okapi", h.getOptions().getDatabase());

    obj.put("postgres_database", "x");
    h = new FakeHandle(vertx, obj);
    Assert.assertEquals("x", h.getOptions().getDatabase());

    obj.put("postgres_port", "123");
    obj.put("postgres_host", "my.service");
    h = new FakeHandle(vertx, obj);
    Assert.assertEquals(123, h.getOptions().getPort());
    Assert.assertEquals("my.service", h.getOptions().getHost());
  }

  @Test
  public void testQueryWithParams(TestContext context) {
    Async async = context.async();
    JsonObject obj = new JsonObject();
    FakeHandle h = new FakeHandle(vertx, obj);

    PostgresQuery q = new PostgresQuery(h);
    q.query("select", Tuple.of("id"), res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.INTERNAL, res.getType());
      context.assertEquals("fake preparedQuery failed", res.cause().getMessage());
      async.complete();
    });
  }

  @Test
  public void testQuery(TestContext context) {
    Async async = context.async();
    JsonObject obj = new JsonObject();
    FakeHandle h = new FakeHandle(vertx, obj);

    PostgresQuery q = new PostgresQuery(h);
    q.close(); // try without an existing connection
    q.query("select", res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.INTERNAL, res.getType());
      context.assertEquals("fake query failed", res.cause().getMessage());
      async.complete();
    });
  }

  @Test
  public void testGetConnectionFailed(TestContext context) {
    Async async = context.async();
    JsonObject obj = new JsonObject();
    obj.put("postgres_port", "0");
    FakeHandle h = new FakeHandle(vertx, obj);

    PostgresQuery q = new PostgresQuery(h);
    q.query("select", res -> {
      context.assertTrue(res.failed());
      context.assertEquals(ErrorType.INTERNAL, res.getType());
      context.assertEquals("getConnection failed", res.cause().getMessage());
      async.complete();
    });
  }

}
