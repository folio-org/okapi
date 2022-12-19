package org.folio.okapi.managers;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.RoutingContext;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.WithAssertions;
import org.awaitility.Awaitility;
import org.folio.okapi.bean.ModuleInstance;
import org.folio.okapi.util.ProxyContext;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@RunWith(VertxUnitRunner.class)
public class ProxyServiceTest implements WithAssertions {

  private static Vertx vertx;
  private static int port;

  @BeforeClass
  public static void startServer(TestContext context) throws InterruptedException {
    vertx = Vertx.vertx();
    vertx.createHttpServer()
    // waits 2000 ms = 2 seconds before responding
    .requestHandler(req -> vertx.setTimer(2000, x -> req.response().end()))
    .listen(0)
    .onComplete(context.asyncAssertSuccess(httpServer -> {
      port = httpServer.actualPort();
    }));
  }

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

  @Test
  public void httpIdleTimeout() {
    var routingContext = mock(RoutingContext.class, Mockito.RETURNS_DEEP_STUBS);
    var proxyContext = new ProxyContext(routingContext, 0);
    var httpClientRequests = List.of(mock(HttpClientRequest.class));
    ModuleInstance moduleInstance = mock(ModuleInstance.class, Mockito.RETURNS_DEEP_STUBS);
    when(moduleInstance.getUrl()).thenReturn("http://localhost:" + port + "/");
    var conf = new JsonObject().put("idle_timeout", 1 /* second */);
    var proxyService = new ProxyService(vertx, null, null, null, null, conf);
    proxyService.proxyRequestHttpClient(null, proxyContext, Buffer.buffer(), httpClientRequests, moduleInstance);
    Awaitility.await().untilAsserted(() ->
        verify(routingContext.response()).end(contains("(idle_timeout is 1 seconds)")));
  }
}
