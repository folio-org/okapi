package org.folio.okapi.common;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import java.io.FileNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HttpResponseTest {

  @Test
  void cause() {
    var ctx = ctx();
    var cause = new FileNotFoundException("gone");
    HttpResponse.responseError(ctx, ErrorType.FORBIDDEN, cause);
    verify(ctx.request()).method();
    verify(ctx.request()).path();
    verify(ctx.response()).setStatusCode(403);
    verify(ctx.response()).end("gone");
  }

  @Test
  void nullMessage() {
    var ctx = ctx();
    HttpResponse.responseError(ctx, ErrorType.INTERNAL, (String) null);
    verify(ctx.response()).setStatusCode(500);
    verify(ctx.response()).end("(null)");
  }

  @Test
  void ok() {
    var ctx = ctx();
    HttpResponse.responseError(ctx, ErrorType.OK, "fine");
    verify(ctx.response()).setStatusCode(200);
    verify(ctx.response()).end("fine");
    verify(ctx.request(), never()).method();
    verify(ctx.request(), never()).path();
  }

  @Test
  void status111() {
    var ctx = ctx();
    HttpResponse.responseError(ctx, 111, "foo");
    verify(ctx.response()).setStatusCode(111);
    verify(ctx.response()).putHeader("Content-Type", "text/plain");
    verify(ctx.response()).end("foo");
  }

  @Test
  void status0() {
    var ctx = ctx();
    HttpResponse.responseError(ctx, 0, "zero");
    verify(ctx.response()).setStatusCode(500);
    verify(ctx.response()).end("zero");
  }

  @Test
  void json() {
    var ctx = ctx();
    HttpResponse.responseJson(ctx, 201);
    verify(ctx.response()).setStatusCode(201);
    verify(ctx.response()).putHeader("Content-Type", "application/json");
  }

  @Test
  void closed() {
    var ctx = ctx();
    when(ctx.response().closed()).thenReturn(true);
    HttpResponse.responseError(ctx, 111, "foo");
    verify(ctx.response(), never()).setStatusCode(anyInt());
    verify(ctx.response(), never()).end(anyString());
  }

  @Test
  void closedJson() {
    var ctx = ctx();
    when(ctx.response().closed()).thenReturn(true);
    HttpResponse.responseJson(ctx, 201);
    verify(ctx.response(), never()).setStatusCode(anyInt());
    verify(ctx.response(), never()).end(anyString());
  }

  @ParameterizedTest
  @ValueSource(ints = { -200, -1, 0, 99, 1000, 200000 })
  void sanitizeStatusCodeInvalid(int code) {
    assertThat(HttpResponse.sanitizeStatusCode(code), is(500));
  }

  @ParameterizedTest
  @ValueSource(ints = { 100, 200, 599, 600, 999 })
  void sanitizeStatusCodeValid(int code) {
    assertThat(HttpResponse.sanitizeStatusCode(code), is(code));
  }

  private RoutingContext ctx() {
    HttpServerRequest request = mock(HttpServerRequest.class);
    when(request.method()).thenReturn(HttpMethod.PUT);
    when(request.path()).thenReturn("/api");
    HttpServerResponse response = mock(HttpServerResponse.class);
    when(response.setStatusCode(anyInt())).thenReturn(response);
    RoutingContext routingContext = mock(RoutingContext.class);
    when(routingContext.request()).thenReturn(request);
    when(routingContext.response()).thenReturn(response);
    return routingContext;
  }
}
