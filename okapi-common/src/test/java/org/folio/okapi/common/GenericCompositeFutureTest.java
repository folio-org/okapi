package org.folio.okapi.common;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.assertj.core.api.WithAssertions;
import org.junit.Test;

public class GenericCompositeFutureTest implements WithAssertions {
  @Test
  public void success() {
    List<Future<String>> successSuccess = Arrays.asList(Future.succeededFuture(), Future.succeededFuture());
    List<Future<String>> successFailure = Arrays.asList(Future.succeededFuture(), Future.failedFuture(""));
    assertThat(GenericCompositeFuture.all(successSuccess).succeeded()).isTrue();
    assertThat(GenericCompositeFuture.all(successFailure).failed()).isTrue();
    assertThat(GenericCompositeFuture.any(successSuccess).succeeded()).isTrue();
    assertThat(GenericCompositeFuture.any(successFailure).succeeded()).isTrue();
    assertThat(GenericCompositeFuture.join(successSuccess).succeeded()).isTrue();
    assertThat(GenericCompositeFuture.join(successFailure).failed()).isTrue();
  }

  @Test
  public void completion() {
    List<Future<Integer>> failureAndUnknown = Arrays.asList(Future.failedFuture(""), Promise.<Integer>promise().future());
    assertThat(GenericCompositeFuture.all(failureAndUnknown).isComplete()).isTrue();
    assertThat(GenericCompositeFuture.any(failureAndUnknown).isComplete()).isFalse();
    assertThat(GenericCompositeFuture.join(failureAndUnknown).isComplete()).isFalse();
  }

  @Test
  public void generics() {
    List<MyFuture<Double>> list = Collections.emptyList();
    CompositeFuture all = GenericCompositeFuture.all(list);
    CompositeFuture any = GenericCompositeFuture.any(list);
    CompositeFuture join = GenericCompositeFuture.join(list);
    assertThat(all.succeeded()).isTrue();
    assertThat(any.succeeded()).isTrue();
    assertThat(join.succeeded()).isTrue();
  }

  class MyFuture<T> implements Future<T> {
    @Override
    public boolean isComplete() {
      return false;
    }

    @Override
    public Future<T> onComplete(Handler<AsyncResult<T>> handler) {
      return null;
    }

    @Override
    public T result() {
      return null;
    }

    @Override
    public Throwable cause() {
      return null;
    }

    @Override
    public boolean succeeded() {
      return false;
    }

    @Override
    public boolean failed() {
      return false;
    }

    @Override
    public <U> Future<U> compose(Function<T, Future<U>> successMapper, Function<Throwable, Future<U>> failureMapper) {
      return null;
    }

    @Override
    public <U> Future<U> eventually(Function<Void, Future<U>> function) {
      return null;
    }

    @Override
    public <U> Future<U> map(Function<T, U> mapper) {
      return null;
    }

    @Override
    public <V> Future<V> map(V value) {
      return null;
    }

    @Override
    public Future<T> otherwise(Function<Throwable, T> mapper) {
      return null;
    }

    @Override
    public Future<T> otherwise(T value) {
      return null;
    }
  };
}
