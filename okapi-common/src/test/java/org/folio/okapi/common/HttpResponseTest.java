package org.folio.okapi.common;

import static org.mockito.Mockito.*;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import java.io.FileNotFoundException;
import org.junit.jupiter.api.Test;

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
    verify(ctx.response()).end("foo");
  }

  @Test
  void closed() {
    var ctx = ctx();
    when(ctx.response().closed()).thenReturn(true);
    HttpResponse.responseError(ctx, 111, "foo");
    verify(ctx.response(), never()).setStatusCode(anyInt());
    verify(ctx.response(), never()).end(anyString());
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
