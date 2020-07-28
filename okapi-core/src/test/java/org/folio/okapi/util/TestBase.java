package org.folio.okapi.util;

import io.vertx.core.Handler;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.folio.okapi.common.ExtendedAsyncResult;

public class TestBase {

  /**
   * Like context.asyncAssertSuccess() but for ExtendedAsyncResult;
   */
  protected Handler<ExtendedAsyncResult<Void>> asyncAssertSuccess(TestContext context) {
    Async async = context.async();
    return handler -> {
      if (handler.failed()) {
        context.fail(handler.cause());
      }
      async.complete();
    };
  }

  /**
   * Like context.asyncAssertSuccess(Handler) but for ExtendedAsyncResult;
   */
  protected <T> Handler<ExtendedAsyncResult<T>> asyncAssertSuccess(
      TestContext context, Handler<Void> block) {
    Async async = context.async();
    return handler -> {
      if (handler.failed()) {
        context.fail(handler.cause());
      }
      context.verify(block);
      async.complete();
    };
  }

  /**
   * @return resource file at resourcePath as UTF-8
   */
  protected static String getResource(String resourcePath) {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
      ByteArrayOutputStream result = new ByteArrayOutputStream();
      byte [] buffer = new byte[1024];
      while (true) {
        int length = inputStream.read(buffer);
        if (length == -1) {
          break;
        }
        result.write(buffer, 0, length);
      }
      return result.toString(StandardCharsets.UTF_8.name());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
