package org.folio.okapi.common;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(VertxExtension.class)
class ChattyResponsePredicateTest {

  static Stream<Arguments> predicate() {
    return Stream.of(
        arguments(ChattyResponsePredicate.SC_OK, 200),
        arguments(ChattyResponsePredicate.SC_CREATED, 201),
        arguments(ChattyResponsePredicate.SC_NO_CONTENT, 204),
        arguments(ChattyResponsePredicate.SC_SUCCESS, 200)
        );
  }

  @ParameterizedTest
  @MethodSource
  void predicate(ResponsePredicate predicate, Integer status, Vertx vertx, VertxTestContext vtc) {
    vertx.createHttpServer()
    .requestHandler(request -> request.response().setStatusCode(555).end("Body and Soul"))
    .listen(0)
    .compose(httpServer -> WebClient.create(vertx)
        .post(httpServer.actualPort(), "localhost", "/")
        .expect(predicate)
        .send())
    .onComplete(vtc.failing(e -> {
      assertThat(e.getMessage(), allOf(
          startsWith("Response status code 555 is not"),
          containsString("" + status),
          endsWith(" - Body and Soul")));
      vtc.completeNow();
    }));
  }

  @ParameterizedTest
  @MethodSource("predicate")
  void predicateSuccess(ResponsePredicate predicate, Integer status, Vertx vertx,
      VertxTestContext vtc) {

    vertx.createHttpServer()
    .requestHandler(request -> request.response().setStatusCode(status).end())
    .listen(0)
    .compose(httpServer -> WebClient.create(vertx)
        .post(httpServer.actualPort(), "localhost", "/")
        .expect(predicate)
        .send())
    .onComplete(vtc.succeedingThenComplete());
  }

  static Stream<Arguments> maxLength() {
    return Stream.of(
        arguments("a".repeat(250) + "b".repeat(250), "a".repeat(250) + "b".repeat(250)),
        arguments("a".repeat(250) + "xx" + "b".repeat(249), "a".repeat(250) + "…" + "b".repeat(249)),
        arguments("a".repeat(250) + "xxx" + "b".repeat(249), "a".repeat(250) + "…" + "b".repeat(249))
        );
  }

  @ParameterizedTest
  @MethodSource
  void maxLength(String bodyIn, String bodyOut, Vertx vertx, VertxTestContext vtc) {
    vertx.createHttpServer()
    .requestHandler(request -> request.response().setStatusCode(500).end(bodyIn))
    .listen(0)
    .compose(httpServer -> WebClient.create(vertx)
        .post(httpServer.actualPort(), "localhost", "/")
        .expect(ChattyResponsePredicate.SC_OK)
        .send())
    .onComplete(vtc.failing(e -> {
      assertThat(e.getMessage(), endsWith(" - " + bodyOut));
      vtc.completeNow();
    }));
  }

  static Stream<Arguments> contentType() {
    return Stream.of(
        arguments(ChattyResponsePredicate.JSON, "application/json"),
        arguments(ChattyResponsePredicate.contentType("text/plain"), "text/plain"),
        arguments(ChattyResponsePredicate.contentType(List.of("text/plain", "text/html")), "text/html")
        );
  }

  @ParameterizedTest
  @MethodSource("contentType")
  void contentTypeSuccess(ResponsePredicate predicate, String contentType, Vertx vertx, VertxTestContext vtc) {
    vertx.createHttpServer()
    .requestHandler(request -> request.response()
        .putHeader("Content-Type", contentType)
        .end("Body and Soul"))
    .listen(0)
    .compose(httpServer -> WebClient.create(vertx)
        .post(httpServer.actualPort(), "localhost", "/")
        .expect(predicate)
        .send())
    .onComplete(vtc.succeedingThenComplete());
  }

  @ParameterizedTest
  @MethodSource("contentType")
  void contentTypeFailure(ResponsePredicate predicate, String contentType, Vertx vertx, VertxTestContext vtc) {
    vertx.createHttpServer()
    .requestHandler(request -> request.response()
        .putHeader("Content-Type", "foo")
        .end("Body and Soul"))
    .listen(0)
    .compose(httpServer -> WebClient.create(vertx)
        .post(httpServer.actualPort(), "localhost", "/")
        .expect(predicate)
        .send())
    .onComplete(vtc.failing(e -> {
      assertThat(e.getMessage(), allOf(containsString(contentType), endsWith(" - Body and Soul")));
      vtc.completeNow();
    }));
  }
}