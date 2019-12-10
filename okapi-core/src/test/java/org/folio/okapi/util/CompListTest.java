package org.folio.okapi.util;

import io.vertx.core.Handler;
import io.vertx.core.Promise;
import java.util.HashSet;
import java.util.Set;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import org.junit.Test;
import org.junit.Assert;

public class CompListTest {
  Set<String> cset = new HashSet<>();
  
  private void succeedHandler(String c, Handler<ExtendedAsyncResult<Void>> hndlr) {
    cset.add(c);
    hndlr.handle(new Success<>());
  }

  private void failureHandler(String c, Handler<ExtendedAsyncResult<Void>> hndlr) {
    hndlr.handle(new Failure<>(ErrorType.INTERNAL, "failHandler"));
  }

  CompList<Void> buildSucceed() {
    cset.clear();
    CompList<Void> comp = new CompList<>(ErrorType.NOT_FOUND);
    {
      Promise<Void> promise = Promise.promise();
      succeedHandler("a", promise::handle);
      comp.add(promise);
    }
    {
      Promise<Void> promise = Promise.promise();
      succeedHandler("b", promise::handle);
      comp.add(promise);
    }
    return comp;
  }

  CompList<Void> buildFail() {
    CompList<Void> comp = buildSucceed();
    {
      Promise<Void> promise = Promise.promise();
      failureHandler("a", promise::handle);
      comp.add(promise);
    }
    return comp;
  }


  @Test
  public void testAllSucceeds() {
    CompList<Void> comp = buildSucceed();
    Promise promise = Promise.promise();
    comp.all(promise.future());
    Assert.assertTrue(promise.future().succeeded());
    Assert.assertTrue(cset.contains("a"));
    Assert.assertTrue(cset.contains("b"));
  }

  @Test
  public void testAllFailsT() {
    cset.clear();
    CompList<Boolean> comp = new CompList<>(ErrorType.NOT_FOUND);
    {
      Promise<Void> promise = Promise.promise();
      succeedHandler("a", promise::handle);
      comp.add(promise);
    }
    {
      Promise<Void> promise = Promise.promise();
      failureHandler("a", promise::handle);
      comp.add(promise);
    }
    {
      Promise promise = Promise.promise();
      comp.all(Boolean.TRUE, promise.future());
      Assert.assertTrue(promise.future().failed());
    }
  }

  @Test
  public void testAllFails() {
    CompList<Void> comp = buildFail();
    Promise promise = Promise.promise();
    comp.all(promise.future());
    Assert.assertTrue(promise.future().failed());
  }

  @Test
  public void testSeqSucceeds() {
    CompList<Void> comp = buildSucceed();
    Promise promise = Promise.promise();
    comp.seq(promise.future());
    Assert.assertTrue(promise.future().succeeded());
    Assert.assertTrue(cset.contains("a"));
    Assert.assertTrue(cset.contains("b"));
  }

  @Test
  public void testSeqFails() {
    CompList<Void> comp = buildFail();
    Promise promise = Promise.promise();
    comp.seq(promise.future());
    Assert.assertTrue(promise.future().failed());
  }

}
