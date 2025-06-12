package org.folio.okapi.service.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import org.folio.okapi.bean.TimerDescriptor;
import org.folio.okapi.util.PgTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@Timeout(5000)
@ExtendWith(VertxExtension.class)
class TimerStorePostgresTest extends PgTestBase {

  static TimerStorePostgres timerStorePostgres;

  @BeforeAll
  static void beforeAll(Vertx vertx) {
    var conf = new JsonObject()
        .put("postgres_host", POSTGRESQL_CONTAINER.getHost())
        .put("postgres_port", POSTGRESQL_CONTAINER.getFirstMappedPort() + "")
        .put("postgres_database", POSTGRESQL_CONTAINER.getDatabaseName())
        .put("postgres_username", POSTGRESQL_CONTAINER.getUsername())
        .put("postgres_password", POSTGRESQL_CONTAINER.getPassword());
    var postgresHandle = new PostgresHandle(vertx, conf);
    timerStorePostgres = new TimerStorePostgres(postgresHandle);
  }

  @Test
  void test(VertxTestContext vtc) {
    timerStorePostgres.init(false)
    .compose(x -> timerStorePostgres.getAll())
    .onComplete(vtc.succeeding(list -> assertThat(list, is(empty()))))
    .compose(x -> timerStorePostgres.put(timerDescriptor("test_tenant_mod-expire_0")))
    .compose(x -> timerStorePostgres.getAll())
    .onComplete(vtc.succeeding(list -> {
      assertThat(list, hasSize(1));
      assertThat(list.get(0).getId(), is("test_tenant_mod-expire_0"));
    }))
    .compose(x -> timerStorePostgres.delete("test_tenant_mod-expire_0"))
    .compose(x -> timerStorePostgres.getAll())
    .onComplete(vtc.succeeding(list -> {
      assertThat(list, is(empty()));
      vtc.completeNow();
    }));
  }

  TimerDescriptor timerDescriptor(String id) {
    var timerDescriptor = new TimerDescriptor();
    timerDescriptor.setId(id);
    return timerDescriptor;
  }
}
