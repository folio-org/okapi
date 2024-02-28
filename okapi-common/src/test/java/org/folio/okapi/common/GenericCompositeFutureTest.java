package org.folio.okapi.common;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

  abstract class MyFuture<T> implements Future<T> {
  }
}
