package org.folio.okapi.managers;

import static org.mockito.Mockito.*;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.web.RoutingContext;
import java.util.Arrays;
import java.util.Base64;
import org.assertj.core.api.WithAssertions;
import org.junit.Test;

public class ProxyServiceTest implements WithAssertions {

  class MyReadStream implements ReadStream<Buffer> {
    boolean pause = false;
    Handler<Buffer> handler;
    Handler<Void> endHandler;

    public ReadStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
      return this;
    }

    public ReadStream<Buffer> handler(Handler<Buffer> handler) {
      this.handler = handler;
      return this;
    }

    public void handle(int n) {
      for (int i=1; i<=n; i++) {
        if (pause) {
          throw new AssertionError("Unexpected pause with " + i);
        }
        handler.handle(Buffer.buffer(Integer.toString(i)));
      }
    }

    public ReadStream<Buffer> pause() {
      pause = true;
      return this;
    }

    public ReadStream<Buffer> resume() {
      pause = false;
      return this;
    }

    public ReadStream<Buffer> fetch(long amount) {
      return this;
    }

    public ReadStream<Buffer> endHandler(Handler<Void> endHandler) {
      this.endHandler = endHandler;
      return this;
    }
  }

  class MyWriteStream implements WriteStream<Buffer> {
    int maxSize = 4;
    int queue;
    int processed;
    boolean overflow = false;
    boolean processImmediately = false;
    boolean end = false;
    Handler<Void> drainHandler;

    public MyWriteStream() {
    }

    public WriteStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
      return this;
    }

    public Future<Void> write(Buffer data) {
      write(data, null);
      return null;
    }

    public void write(Buffer data, Handler<AsyncResult<Void>> handler) {
      queue++;
      if (writeQueueFull()) {
        overflow = true;
      }
      if (processImmediately) {
        processQueue(1);
      }
      if (handler != null) {
        handler.handle(null);
      }
    }

    public void processQueue(int i) {
      int n = i > queue ? queue: i;
      queue -= n;
      processed += n;
      if (overflow && queue <= maxSize/2) {
        overflow = false;
        if (drainHandler != null) {
          drainHandler.handle(null);
        }
      }
    }

    public void end(Handler<AsyncResult<Void>> handler) {
      end = true;
      handler.handle(Future.succeededFuture());
    }

    public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
      this.maxSize = maxSize;
      return this;
    }

    public boolean writeQueueFull() {
      return queue >= maxSize;
    }

    public WriteStream<Buffer> drainHandler(Handler<Void> handler) {
      drainHandler = handler;
      return this;
    }
  }

  @Test
  public void pumpOneToManySimple() {
    MyReadStream readStream = new MyReadStream();
    MyWriteStream writeStream1 = new MyWriteStream();
    MyWriteStream writeStream2 = new MyWriteStream();
    ProxyService.pumpOneToMany(readStream, Arrays.asList(writeStream1, writeStream2));

    readStream.handle(4);
    assertThat(writeStream1.overflow).isTrue();
    assertThat(writeStream2.overflow).isTrue();
    assertThat(readStream.pause).isTrue();

    writeStream1.processQueue(4);
    assertThat(writeStream1.overflow).isFalse();
    assertThat(writeStream2.overflow).isTrue();
    assertThat(readStream.pause).isTrue();

    writeStream2.processQueue(4);
    assertThat(writeStream1.overflow).isFalse();
    assertThat(writeStream2.overflow).isFalse();
    assertThat(readStream.pause).isFalse();

    readStream.handle(1);
    assertThat(writeStream1.end).isFalse();
    assertThat(writeStream2.end).isFalse();

    readStream.endHandler.handle(null);
    assertThat(writeStream1.end).isTrue();
    assertThat(writeStream2.end).isTrue();
  }

  @Test
  public void pumpOneToManyPiledUp() {
    MyReadStream readStream = new MyReadStream();
    MyWriteStream writeStream1 = new MyWriteStream();
    MyWriteStream writeStream2 = new MyWriteStream();
    ProxyService.pumpOneToMany(readStream, Arrays.asList(writeStream1, writeStream2));

    readStream.handle(4);
    writeStream1.processQueue(4);

    // async requests may have piled up
    writeStream1.write(null);
    writeStream1.write(null);
    writeStream1.write(null);
    writeStream1.write(null);      // this reaches maxSize
    writeStream1.processQueue(4);  // below maxSize/2 causing a second drainHandler call
    assertThat(writeStream2.writeQueueFull()).isTrue();
    assertThat(writeStream2.overflow).isTrue();
    assertThat(readStream.pause).isTrue();
  }

  @Test
  public void testStatusOk() {
    assertThat(ProxyService.statusOk(100)).isFalse();
    assertThat(ProxyService.statusOk(200)).isTrue();
    assertThat(ProxyService.statusOk(300)).isFalse();
  }

  private void rejectMdcLookups(String headerName, String headerValue) {
    var proxyService = new ProxyService(Vertx.vertx(), null, null, null, null, new JsonObject());
    var routingContext = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
    var headers = MultiMap.caseInsensitiveMultiMap().add(headerName, headerValue);
    when(routingContext.request().headers()).thenReturn(headers);
    when(routingContext.request().getHeader(headerName)).thenReturn(headerValue);
    proxyService.proxy(routingContext);
    verify(routingContext.response()).setStatusCode(400);
  }

  @Test
  public void rejectMdcLookups() {
    rejectMdcLookups("X-Okapi-Tenant", "${foo}");
    rejectMdcLookups("X-Okapi-Request-Id", "${foo}-y");
    rejectMdcLookups("X-Okapi-Module-Id", "x${foo}y${bar}z");

    JsonObject jsonObject = new JsonObject();
    jsonObject.put("user_id", "x-${foo${bar}}");
    byte[] encodedBytes = Base64.getEncoder().encode(jsonObject.encode().getBytes());
    String encodedString = new String(encodedBytes);
    String token = "method." + encodedString + ".trail";
    rejectMdcLookups("X-Okapi-Token", token);
  }

  @Test
  public void rejectMdcLookupsRedirect() {
    var proxyService = new ProxyService(Vertx.vertx(), null, null, null, null, new JsonObject());
    var routingContext = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
    when(routingContext.request().path()).thenReturn("/_/invoke/tenant/x${foo}y/123");
    proxyService.redirectProxy(routingContext);
    verify(routingContext.response()).setStatusCode(400);
  }
}
