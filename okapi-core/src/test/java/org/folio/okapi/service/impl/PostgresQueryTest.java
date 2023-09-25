package org.folio.okapi.service.impl;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.sqlclient.PreparedQuery;
import io.vertx.sqlclient.Query;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class PostgresQueryTest {

  Vertx vertx;

  @Before
  public void setup() {
    vertx = Vertx.vertx();
  }

  @After
  public void tearDown() {
    vertx.close();
  }

  @Test
  public void postgresHandleDefaults() {
    var h = new PostgresHandle(vertx, new JsonObject());
    Assert.assertEquals("okapi", h.getOptions().getUser());
    Assert.assertEquals("okapi25", h.getOptions().getPassword());
    Assert.assertEquals("okapi", h.getOptions().getDatabase());
  }

  @Test
  public void postgresHandleDatabase() {
    var h = new PostgresHandle(vertx, new JsonObject().put("postgres_database", "x"));
    Assert.assertEquals("x", h.getOptions().getDatabase());
  }

  @Test
  public void postgresHandlePortHost() {
    var h = new PostgresHandle(vertx, new JsonObject()
        .put("postgres_port", "123")
        .put("postgres_host", "my.service"));
    Assert.assertEquals(123, h.getOptions().getPort());
    Assert.assertEquals("my.service", h.getOptions().getHost());
  }

  @Test
  public void testQueryWithParams(TestContext context) {
    var preparedQuery = mock(PreparedQuery.class);
    when(preparedQuery.execute(any(Tuple.class))).thenReturn(failedFuture("foo failure"));
    var sqlConnection = mock(SqlConnection.class);
    when(sqlConnection.preparedQuery(any())).thenReturn(preparedQuery);
    var postgresHandle = mock(PostgresHandle.class);
    when(postgresHandle.getConnection()).thenReturn(succeededFuture(sqlConnection));

    new PostgresQuery(postgresHandle)
      .query("select", Tuple.of("id"))
      .onComplete(context.asyncAssertFailure(e -> assertEquals("foo failure", e.getMessage())));
  }

  @Test
  public void testQuery(TestContext context) {
    var query = mock(Query.class);
    when(query.execute()).thenReturn(failedFuture("bar failure"));
    var sqlConnection = mock(SqlConnection.class);
    when(sqlConnection.query(any())).thenReturn(query);
    var postgresHandle = mock(PostgresHandle.class);
    when(postgresHandle.getConnection()).thenReturn(succeededFuture(sqlConnection));

    new PostgresQuery(postgresHandle)
      .query("select")
      .onComplete(context.asyncAssertFailure(e -> assertEquals("bar failure", e.getMessage())));
  }

  @Test
  public void close(TestContext context) {
    var query1 = mock(Query.class);
    when(query1.execute()).thenReturn(failedFuture("failure 1"));
    var query2 = mock(Query.class);
    when(query2.execute()).thenReturn(failedFuture("failure 2"));
    var sqlConnection1 = mock(SqlConnection.class);
    when(sqlConnection1.query(any())).thenReturn(query1);
    var sqlConnection2 = mock(SqlConnection.class);
    when(sqlConnection2.query(any())).thenReturn(query2);
    var postgresHandle = mock(PostgresHandle.class);
    when(postgresHandle.getConnection())
      .thenReturn(succeededFuture(sqlConnection1), succeededFuture(sqlConnection2));

    var q = new PostgresQuery(postgresHandle);
    q.close(); // try without an existing connection
    q.query("select")
      .onComplete(context.asyncAssertFailure(e -> assertEquals("failure 1", e.getMessage())))
      .recover(e -> {
        q.close();  // close an existing connection
        return q.query("select");
      })
      .onComplete(context.asyncAssertFailure(e -> assertEquals("failure 2", e.getMessage())));
  }

  @Test
  public void testGetConnectionFailed1(TestContext context) {
    var postgresHandle = mock(PostgresHandle.class);
    when(postgresHandle.getConnection()).thenReturn(failedFuture("getConnection failed"));

    new PostgresQuery(postgresHandle)
      .query("select")
      .onComplete(context.asyncAssertFailure(e -> assertEquals("getConnection failed", e.getMessage())));
  }

  @Test
  public void testGetConnectionFailed2(TestContext context) {
    var postgresHandle = mock(PostgresHandle.class);
    when(postgresHandle.getConnection()).thenReturn(failedFuture("getConnection failed"));

    new PostgresQuery(postgresHandle)
      .query("select", Tuple.of("id"))
      .onComplete(context.asyncAssertFailure(e -> assertEquals("getConnection failed", e.getMessage())));
  }

}
