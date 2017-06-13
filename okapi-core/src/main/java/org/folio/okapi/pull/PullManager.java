package org.folio.okapi.pull;

import io.vertx.core.json.Json;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.folio.okapi.bean.ModuleDescriptorBrief;
import org.folio.okapi.bean.PullDescriptor;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

public class PullManager {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final String okapiUrl;
  private final Vertx vertx;
  private final HttpClient httpClient;

  public PullManager(Vertx vertx, String okapiUrl) {
    this.okapiUrl = okapiUrl;
    this.vertx = vertx;
    this.httpClient = vertx.createHttpClient();
  }

  private void pullUrl(Iterator<String> it, Handler<ExtendedAsyncResult<Void>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Failure<>(ErrorType.NOT_FOUND, "all pull urls failed"));
    } else {
      String url = it.next();
      if (!url.endsWith("/")) {
        url += "/";
      }
      url += "_/proxy/modules";
      Buffer body = Buffer.buffer();
      HttpClientRequest req = httpClient.getAbs(url, res -> {
        res.handler(x -> {
          body.appendBuffer(x);
        });
        res.endHandler(x -> {
          if (res.statusCode() != 200) {
            fut.handle(new Failure<>(ErrorType.USER, body.toString()));
          } else {
            ModuleDescriptorBrief ml[] = Json.decodeValue(body.toString(), ModuleDescriptorBrief[].class);
            for (ModuleDescriptorBrief mdb : ml) {
              logger.info("md " + mdb.getId());
            }
            fut.handle(new Success<>());
          }
        });
        res.exceptionHandler(x -> {
          fut.handle(new Failure<>(ErrorType.INTERNAL, x.getCause()));
        });
      });
      req.exceptionHandler(res -> {
        pullUrl(it, fut);
      });
      req.end();
    }
  }

  public void pull(PullDescriptor pd, Handler<ExtendedAsyncResult<Void>> fut) {
    List<String> sl = new LinkedList<>();
    for (String s : pd.getUrls()) {
      sl.add(s);
    }
    pullUrl(sl.iterator(), fut);
  }
}
