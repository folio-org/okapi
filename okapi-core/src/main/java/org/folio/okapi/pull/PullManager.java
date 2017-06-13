package org.folio.okapi.pull;

import io.vertx.core.json.Json;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.Arrays;
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

  private void getBrief(Iterator<String> it,
    Handler<ExtendedAsyncResult<ModuleDescriptorBrief[]>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Failure<>(ErrorType.NOT_FOUND, "all pull urls failed"));
    } else {
      String url = it.next();
      if (!url.endsWith("/")) {
        url += "/";
      }
      url += "_/proxy/modules";
      Buffer body = Buffer.buffer();
      logger.info("get " + url);
      HttpClientRequest req = httpClient.getAbs(url, res -> {
        res.handler(x -> {
          body.appendBuffer(x);
        });
        res.endHandler(x -> {
          if (res.statusCode() != 200) {
            fut.handle(new Failure<>(ErrorType.USER, body.toString()));
          } else {
            ModuleDescriptorBrief ml[] = Json.decodeValue(body.toString(),
              ModuleDescriptorBrief[].class);
            for (ModuleDescriptorBrief mdb : ml) {
              logger.info("md1 " + mdb.getId());
            }
            Arrays.sort(ml);
            for (ModuleDescriptorBrief mdb : ml) {
              logger.info("md2 " + mdb.getId());
            }
            fut.handle(new Success<>(ml));
          }
        });
        res.exceptionHandler(x -> {
          logger.warn("exception handler 1");
          fut.handle(new Failure<>(ErrorType.INTERNAL, x.getMessage()));
        });
      });
      req.exceptionHandler(res -> {
        logger.warn("exception handler 2");
        getBrief(it, fut);
      });
      req.end();
    }
  }

  public void pull(PullDescriptor pd, Handler<ExtendedAsyncResult<Void>> fut) {
    List<String> localUrls = new LinkedList<>();
    localUrls.add(okapiUrl);
    getBrief(localUrls.iterator(), resLocal -> {
      if (resLocal.failed()) {
        fut.handle(new Failure<>(resLocal.getType(), resLocal.cause()));
      } else {
        ModuleDescriptorBrief[] mlLocal = resLocal.result();
        getBrief(Arrays.asList(pd.getUrls()).iterator(), resRemote -> {
          if (resRemote.failed()) {
            fut.handle(new Failure<>(resRemote.getType(), resRemote.cause()));
          } else {
            ModuleDescriptorBrief[] mlRemote = resRemote.result();
            fut.handle(new Success<>());
          }
        });
      }
    });
  }
}
