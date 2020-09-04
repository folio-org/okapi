package org.folio.okapi.managers;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.Json;
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
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.util.OkapiError;

@java.lang.SuppressWarnings({"squid:S1192"})
public class PullManager {

  private final Logger logger = OkapiLogger.get();
  private final HttpClient httpClient;
  private final ModuleManager moduleManager;
  private final Messages messages = Messages.getInstance();

  public PullManager(Vertx vertx, ModuleManager moduleManager) {
    this.httpClient = vertx.createHttpClient();
    this.moduleManager = moduleManager;
  }

  private Future<List<String>> getRemoteUrl(Iterator<String> it) {
    if (!it.hasNext()) {
      return Future.failedFuture(new OkapiError(ErrorType.NOT_FOUND, messages.getMessage("11000")));
    }
    final String baseUrl = it.next();
    String url = baseUrl;
    if (!url.endsWith("/")) {
      url += "/";
    }
    url += "_/version";
    final Buffer body = Buffer.buffer();
    Promise<List<String>> promise = Promise.promise();
    httpClient.get(new RequestOptions().setAbsoluteURI(url)).onComplete(res1 -> {
      if (res1.failed()) {
        logger.warn("pull for {} failed: {}", baseUrl,
            res1.cause().getMessage(), res1.cause());
        getRemoteUrl(it).onComplete(promise::handle);
        return;
      }
      HttpClientResponse res = res1.result();
      res.handler(body::appendBuffer);
      res.endHandler(x -> {
        if (res.statusCode() != 200) {
          logger.warn("pull for {} failed with status {}",
              baseUrl, res.statusCode());
          promise.fail(new OkapiError(ErrorType.USER,
              "pull for " + baseUrl + " returned status "
              + res.statusCode() + "\n" + body.toString()));
        } else {
          List<String> result = new LinkedList<>();
          result.add(baseUrl);
          result.add(body.toString());
          promise.complete(result);
        }
      });
      res.exceptionHandler(x -> promise.fail(x.getMessage()));
    });
    return promise.future();
  }

  private Future<ModuleDescriptor[]> getList(String urlBase,
                                             Collection<ModuleDescriptor> skipList) {
    String url = urlBase;
    if (!url.endsWith("/")) {
      url += "/";
    }
    url += "_/proxy/modules?full=true";
    String[] idList = new String[skipList.size()];
    int i = 0;
    for (ModuleDescriptor md : skipList) {
      idList[i++] = md.getId();
    }
    Promise<ModuleDescriptor[]> promise = Promise.promise();
    httpClient.request(
        new RequestOptions().setMethod(HttpMethod.GET).setAbsoluteURI(url))
        .onFailure(res -> promise.fail(res.getMessage()))
        .onSuccess(req -> {
          req.end(Json.encodePrettily(idList));
          req.onFailure(res -> promise.fail(res.getMessage()));
          req.onSuccess(res -> {
            final Buffer body = Buffer.buffer();
            res.handler(body::appendBuffer);
            res.endHandler(x -> {
              if (res.statusCode() != 200) {
                promise.fail(new OkapiError(ErrorType.USER, body.toString()));
                return;
              }
              ModuleDescriptor[] ml = Json.decodeValue(body.toString(),
                  ModuleDescriptor[].class);
              promise.complete(ml);
            });
            res.exceptionHandler(x
                -> promise.fail(x.getMessage()));
          });
        });
    return promise.future();
  }

  private Future<List<ModuleDescriptor>> pullSmart(String remoteUrl,
                                                   Collection<ModuleDescriptor> localList) {
    return getList(remoteUrl, localList).compose(res -> {
      ModuleDescriptor[] remoteList = res;
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
      return moduleManager.createList(mustAddList, true, true,true)
          .compose(res1 -> Future.succeededFuture(briefList));
    });
  }

  Future<List<ModuleDescriptor>> pull(PullDescriptor pd) {
    return getRemoteUrl(Arrays.asList(pd.getUrls()).iterator())
        .compose(resUrl ->
            moduleManager.getModulesWithFilter(true, true, null).compose(
                resLocal -> {
                  final String remoteUrl = resUrl.get(0);
                  final String remoteVersion = resUrl.get(1);
                  logger.info("Remote registry at {} is version {}", remoteUrl, remoteVersion);
                  logger.info("pull smart");
                  return pullSmart(remoteUrl, resLocal);
                }));

  }
}
