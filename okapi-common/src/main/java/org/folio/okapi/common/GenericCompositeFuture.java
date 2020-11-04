package org.folio.okapi.common;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.impl.future.CompositeFutureImpl;
import java.util.List;

/**
 * Coordinate a {@code List<Future<T extends Future <?>>>}, provides
 * {@link #all(List) all(List&lt;T>)}, {@link #any(List) any(List&lt;T>)} and
 * {@link #join(List) join(List&lt;T>)} that work like
 * {@link CompositeFuture#all(List)}, {@link CompositeFuture#any(List)} and
 * {@link CompositeFuture#join(List)} but properly check the generic type
 * {@code <T extends Future<?>>}.
 * <p>
 * See <a href="https://github.com/eclipse-vertx/vert.x/pull/3595">
 * https://github.com/eclipse-vertx/vert.x/pull/3595</a>
 * why Vert.x doesn't have this.
 */
public interface GenericCompositeFuture {
  /**
   * Return a composite future, succeeded when all futures are succeeded,
   * failed as soon as one of the futures is failed.
   * <p/>
   * When the list is empty, the returned future will be already succeeded.
   * <p/>
   * This is the same as {@link CompositeFuture#all(List)} but with generic type declaration.
   */
  static <T extends Future<?>> CompositeFuture all(List<T> futures) {
    return CompositeFutureImpl.all(futures.toArray(new Future[futures.size()]));
  }

  /**
   * Return a composite future, succeeded as soon as one future is succeeded,
   * failed when all futures are failed.
   * <p/>
   * When the list is empty, the returned future will be already succeeded.
   * <p/>
   * This is the same as {@link CompositeFuture#any(List)} but with generic type declaration.
   */
  static <T extends Future<?>> CompositeFuture any(List<T> futures) {
    return CompositeFutureImpl.any(futures.toArray(new Future[futures.size()]));
  }

  /**
   * Return a composite future, succeeded when all {@code futures} are succeeded,
   * failed when any of the {@code futures} is failed.
   * <p/>
   * It always waits until all its {@code futures} are completed and will not fail
   * as soon as one of the {@code futures} fails.
   * <p/>
   * When the list is empty, the returned future will be already succeeded.
   * <p/>
   * This is the same as {@link CompositeFuture#join(List)} but with generic type declaration.
   */
  static <T extends Future<?>> CompositeFuture join(List<T> futures) {
    return CompositeFutureImpl.join(futures.toArray(new Future[futures.size()]));
  }
}
