package org.folio.okapi.common;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class HttpClientFail2Test {
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
  public void test(TestContext context) {
    Async async = context.async();

    HttpClient client = vertx.createHttpClient();
    HttpClientRequest req1 = client.request(
        new RequestOptions().setAbsoluteURI("http://localhost:9292"))
        .exceptionHandler(res1 -> {
          HttpClientRequest req2 = client.request(
              new RequestOptions().setAbsoluteURI("http://localhost:9292"))
              .exceptionHandler(res2 -> async.complete())
              .onSuccess(res2 -> {
                context.fail("unexpected success; something running?");
                async.complete();
              });
          req2.end();
        })
        .onSuccess(
            res1 -> {
              context.fail("unexpected success; something running?");
              async.complete();
            });
    req1.end();
    async.await(5000);
  }

}
