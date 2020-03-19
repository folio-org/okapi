package org.folio.okapi.managers;

import io.vertx.core.json.Json;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.PullDescriptor;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;

@java.lang.SuppressWarnings({"squid:S1192"})
public class PullManager {

  private final Logger logger = OkapiLogger.get();
  private final HttpClient httpClient;
  private final ModuleManager moduleManager;
  private Messages messages = Messages.getInstance();

  public PullManager(Vertx vertx, ModuleManager moduleManager) {
    this.httpClient = vertx.createHttpClient();
    this.moduleManager = moduleManager;
  }

  private void getRemoteUrl(Iterator<String> it,
    Handler<ExtendedAsyncResult<List<String>>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Failure<>(ErrorType.NOT_FOUND, messages.getMessage("11000")));
      return;
    }
    final String baseUrl = it.next();
    String url = baseUrl;
    if (!url.endsWith("/")) {
      url += "/";
    }
    url += "_/version";
    final Buffer body = Buffer.buffer();
    HttpClientRequest req = httpClient.getAbs(url, res1 -> {
      if (res1.failed()) {
        logger.warn("pull for {} failed: {}", baseUrl,
          res1.cause().getMessage(), res1.cause());
        getRemoteUrl(it, fut);
        return;
      }
      HttpClientResponse res = res1.result();
      res.handler(body::appendBuffer);
      res.endHandler(x -> {
        if (res.statusCode() != 200) {
          logger.warn("pull for {} failed with status {}",
            baseUrl, res.statusCode());
          fut.handle(new Failure<>(ErrorType.USER,
            "pull for " + baseUrl + " returned status " + res.statusCode() + "\n" + body.toString()));
        } else {
          List<String> result = new LinkedList<>();
          result.add(baseUrl);
          result.add(body.toString());
          fut.handle(new Success<>(result));
        }
      });
      res.exceptionHandler(x
        -> fut.handle(new Failure<>(ErrorType.INTERNAL, x.getMessage()))
      );
    });
    req.end();
  }

  private void getList(String urlBase,
    Collection<ModuleDescriptor> skipList,
    Handler<ExtendedAsyncResult<ModuleDescriptor[]>> fut) {
    String url = urlBase;
    if (!url.endsWith("/")) {
      url += "/";
    }
    url += "_/proxy/modules";
    if (skipList != null) {
      url += "?full=true";
    }
    final Buffer body = Buffer.buffer();
    HttpClientRequest req = httpClient.getAbs(url, res1 -> {
      if (res1.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, res1.cause().getMessage()));
        return;
      }
      HttpClientResponse res = res1.result();
      res.handler(body::appendBuffer);
      res.endHandler(x -> {
        if (res.statusCode() != 200) {
          fut.handle(new Failure<>(ErrorType.USER, body.toString()));
          return;
        }
        ModuleDescriptor[] ml = Json.decodeValue(body.toString(),
          ModuleDescriptor[].class);
        fut.handle(new Success<>(ml));
      });
      res.exceptionHandler(x
        -> fut.handle(new Failure<>(ErrorType.INTERNAL, x.getMessage())));
    });
    if (skipList != null) {
      String[] idList = new String[skipList.size()];
      int i = 0;
      for (ModuleDescriptor md : skipList) {
        idList[i] = md.getId();
        i++;
      }
      req.end(Json.encodePrettily(idList));
    } else {
      req.end();
    }
  }

  private void pullSmart(String remoteUrl, Collection<ModuleDescriptor> localList,
    Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {

    getList(remoteUrl, localList, resRemote -> {
      if (resRemote.failed()) {
        fut.handle(new Failure<>(resRemote.getType(), resRemote.cause()));
        return;
      }
      ModuleDescriptor[] remoteList = resRemote.result();
      List<ModuleDescriptor> mustAddList = new LinkedList<>();
      List<ModuleDescriptor> briefList = new LinkedList<>();
      Set<String> enabled = new TreeSet<>();
      for (ModuleDescriptor md : localList) {
        enabled.add(md.getId());
      }
      for (ModuleDescriptor md : remoteList) {
        if (!"okapi".equals(md.getProduct()) && !enabled.contains(md.getId())) {
          mustAddList.add(md);
          briefList.add(new ModuleDescriptor(md, true));
        }
      }
      logger.info("pull: {} MDs to insert", mustAddList.size());
      moduleManager.createList(mustAddList, true, true, true, res1 -> {
        if (res1.failed()) {
          fut.handle(new Failure<>(res1.getType(), res1.cause()));
          return;
        }
        fut.handle(new Success<>(briefList));
      });
    });
  }

  public void pull(PullDescriptor pd, Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {
    getRemoteUrl(Arrays.asList(pd.getUrls()).iterator(), resUrl -> {
      if (resUrl.failed()) {
        fut.handle(new Failure<>(resUrl.getType(), resUrl.cause()));
        return;
      }
      moduleManager.getModulesWithFilter(true, true, null, resLocal -> {
        if (resLocal.failed()) {
          fut.handle(new Failure<>(resLocal.getType(), resLocal.cause()));
          return;
        }
        final String remoteUrl = resUrl.result().get(0);
        final String remoteVersion = resUrl.result().get(1);
        logger.info("Remote registry at {} is version {}", remoteUrl, remoteVersion);
        logger.info("pull smart");
        pullSmart(remoteUrl, resLocal.result(), fut);
      });
    });
  }
}
