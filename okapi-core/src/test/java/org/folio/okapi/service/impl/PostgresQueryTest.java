package org.folio.okapi.service.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.sqlclient.PrepareOptions;
import io.vertx.sqlclient.PreparedQuery;
import io.vertx.sqlclient.PreparedStatement;
import io.vertx.sqlclient.Query;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.Transaction;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.spi.DatabaseMetadata;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collector;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class PostgresQueryTest {

  Vertx vertx;

  class FakeQuery<T> implements PreparedQuery<T>  {

    @Override
    public void execute(Tuple tuple, Handler<AsyncResult<T>> handler) {
       handler.handle(execute(tuple));
    }

    @Override
    public Future<T> execute(Tuple tuple) {
      return execute();
    }

    @Override
    public void executeBatch(List<Tuple> list, Handler<AsyncResult<T>> handler) {
      handler.handle(executeBatch(list));
    }

    @Override
    public Future<T> executeBatch(List<Tuple> list) {
      return Future.failedFuture("fake batch querty failed");
    }

    @Override
    public void execute(Handler<AsyncResult<T>> handler) {
      handler.handle(execute());
    }

    @Override
    public Future<T> execute() {
      return Future.failedFuture("fake query failed");
    }

    @Override
    public <R> PreparedQuery<SqlResult<R>> collecting(Collector<Row, ?, R> collector) {
      return null;
    }

    @Override
    public <U> PreparedQuery<RowSet<U>> mapping(Function<Row, U> function) {
      return null;
    }
  }
  class FakeSqlConnection implements SqlConnection {

    @Override
    public SqlConnection prepare(String s, Handler<AsyncResult<PreparedStatement>> handler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<PreparedStatement> prepare(String s) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SqlConnection prepare(String s, PrepareOptions prepareOptions, Handler<AsyncResult<PreparedStatement>> handler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<PreparedStatement> prepare(String s, PrepareOptions prepareOptions) {
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
    public void begin(Handler<AsyncResult<Transaction>> handler) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Future<Transaction> begin() {
      return null;
    }

    @Override
    public boolean isSSL() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Query<RowSet<Row>> query(String s) {
      return new FakeQuery<>();
    }

    @Override
    public PreparedQuery<RowSet<Row>> preparedQuery(String s) {
      return new FakeQuery<>();
    }

    @Override
    public PreparedQuery<RowSet<Row>> preparedQuery(String s, PrepareOptions prepareOptions) {
      return null;
    }

    @Override
    public void close(Handler<AsyncResult<Void>> handler) {

    }

    @Override
    public DatabaseMetadata databaseMetadata() {
      return null;
    }

    @Override
    public Future<Void> close() {
      return null;
    }
  }

  class FakeHandle extends PostgresHandle {

    JsonObject conf;

    protected FakeHandle(Vertx vertx, JsonObject conf) {
      super(vertx, conf);
      this.conf = conf;
    }

    @Override
    public Future<SqlConnection> getConnection() {
      if ("0".equals(conf.getString("postgres_port"))) {
        return Future.failedFuture("getConnection failed");
      }
      return Future.succeededFuture(new FakeSqlConnection());
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
    q.query("select", Tuple.of("id")).onComplete(res -> {
      context.assertTrue(res.failed());
      context.assertEquals("fake query failed", res.cause().getMessage());
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
    q.query("select").onComplete(res -> {
      context.assertTrue(res.failed());
      context.assertEquals("fake query failed", res.cause().getMessage());
      async.complete();
    });
  }

  @Test
  public void testGetConnectionFailed1(TestContext context) {
    Async async = context.async();
    JsonObject obj = new JsonObject();
    obj.put("postgres_port", "0");
    FakeHandle h = new FakeHandle(vertx, obj);

    PostgresQuery q = new PostgresQuery(h);
    q.query("select").onComplete(res -> {
      context.assertTrue(res.failed());
      context.assertEquals("getConnection failed", res.cause().getMessage());
      async.complete();
    });
  }

  @Test
  public void testGetConnectionFailed2(TestContext context) {
    Async async = context.async();
    JsonObject obj = new JsonObject();
    obj.put("postgres_port", "0");
    FakeHandle h = new FakeHandle(vertx, obj);

    PostgresQuery q = new PostgresQuery(h);
    q.query("select", Tuple.of("id")).onComplete(res -> {
      context.assertTrue(res.failed());
      context.assertEquals("getConnection failed", res.cause().getMessage());
      async.complete();
    });
  }

}
