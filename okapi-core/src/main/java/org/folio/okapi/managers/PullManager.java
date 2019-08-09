package org.folio.okapi.managers;

import io.vertx.core.json.Json;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.logging.Logger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.PullDescriptor;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.SemVer;
import org.folio.okapi.common.Success;

@java.lang.SuppressWarnings({"squid:S1192"})
public class PullManager {

  private final Logger logger = OkapiLogger.get();
  private final HttpClient httpClient;
  private int concurrentRuns;
  private static final int CONCURRENT_MAX = 10;
  private boolean concurrentComplete;
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
    } else {
      final String baseUrl = it.next();
      String url = baseUrl;
      if (!url.endsWith("/")) {
        url += "/";
      }
      url += "_/version";
      final Buffer body = Buffer.buffer();
      HttpClientRequest req = httpClient.getAbs(url, res -> {
        res.handler(body::appendBuffer);
        res.endHandler(x -> {
          if (res.statusCode() != 200) {
            logger.info("pull for " + baseUrl + " failed with status "
                    + res.statusCode());
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
      req.exceptionHandler(res -> {
        logger.info("pull for " + baseUrl + " failed with status "
                + res.getMessage());
        getRemoteUrl(it, fut);
      });
      req.end();
    }
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
    HttpClientRequest req = httpClient.getAbs(url, res -> {
      res.handler(body::appendBuffer);
      res.endHandler(x -> {
        if (res.statusCode() != 200) {
          fut.handle(new Failure<>(ErrorType.USER, body.toString()));
        } else {
          ModuleDescriptor[] ml = Json.decodeValue(body.toString(),
            ModuleDescriptor[].class);
          fut.handle(new Success<>(ml));
        }
      });
      res.exceptionHandler(x
        -> fut.handle(new Failure<>(ErrorType.INTERNAL, x.getMessage())));
    });
    req.exceptionHandler(x
      -> fut.handle(new Failure<>(ErrorType.INTERNAL, x.getMessage())));
    if (skipList != null) {
      String [] idList = new String[skipList.size()];
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

  private void getFull(String urlBase, Iterator<ModuleDescriptor> it,
    List<ModuleDescriptor> ml,
    Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {

    while (!concurrentComplete && concurrentRuns < CONCURRENT_MAX && it.hasNext()) {
      ++concurrentRuns;
      String url = urlBase;
      if (!url.endsWith("/")) {
        url += "/";
      }
      url += "_/proxy/modules/" + it.next().getId();
      getFullReq(url, fut, ml, urlBase, it);
    }
    if (!it.hasNext() && !concurrentComplete && concurrentRuns == 0) {
      concurrentComplete = true;
      fut.handle(new Success<>(ml));
    }
  }

  private void getFullReq(String url, Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut,
    List<ModuleDescriptor> ml, String urlBase, Iterator<ModuleDescriptor> it) {

    final Buffer body = Buffer.buffer();
    HttpClientRequest req = httpClient.getAbs(url, res -> {
      res.handler(body::appendBuffer);
      res.endHandler(x -> {
        if (concurrentRuns > 0) {
          concurrentRuns--;
        }
        if (res.statusCode() != 200) {
          if (!concurrentComplete) {
            concurrentComplete = true;
            fut.handle(new Failure<>(ErrorType.USER, body.toString()));
          }
        } else {
          ModuleDescriptor md = Json.decodeValue(body.toString(),
            ModuleDescriptor.class);
          ml.add(md);
          getFull(urlBase, it, ml, fut);
        }
      });
      res.exceptionHandler(x -> {
        if (concurrentRuns > 0) {
          concurrentRuns--;
        }
        if (!concurrentComplete) {
          concurrentComplete = true;
          fut.handle(new Failure<>(ErrorType.INTERNAL, x.getMessage()));
        }
      });
    });
    req.exceptionHandler(x -> {
      if (concurrentRuns > 0) {
        concurrentRuns--;
      }
      if (!concurrentComplete) {
        concurrentComplete = true;
        fut.handle(new Failure<>(ErrorType.INTERNAL, x.getMessage()));
      }
    });
    req.end();
  }

  private void merge(String urlBase, List<ModuleDescriptor> mlLocal,
    ModuleDescriptor[] mlRemote, Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {

    TreeMap<String, Boolean> enabled = new TreeMap<>();
    for (ModuleDescriptor md : mlLocal) {
      enabled.put(md.getId(), false);
    }

    List<ModuleDescriptor> mlAdd = new LinkedList<>();
    for (ModuleDescriptor md : mlRemote) {
      if (!"okapi".equals(md.getProduct()) && enabled.get(md.getId()) == null) {
        mlAdd.add(md);
      }
    }
    logger.info("pull: " + mlAdd.size() + " MDs to fetch");
    List<ModuleDescriptor> mlList = new LinkedList<>();
    concurrentRuns = 0;
    concurrentComplete = false;
    getFull(urlBase, mlAdd.iterator(), mlList, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        logger.info("pull: local insert");
        moduleManager.createList(mlList, true, true, true, res1 -> {
          if (res1.failed()) {
            fut.handle(new Failure<>(res1.getType(), res1.cause()));
          } else {
            fut.handle(new Success<>(mlAdd));
          }
        });
      }
    });
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
      for (int i = 0; i < remoteList.length; i++) {
        ModuleDescriptor md = remoteList[i];
        if (!"okapi".equals(md.getProduct())) {
          mustAddList.add(md);
          briefList.add(new ModuleDescriptor(md, true));
        }
      }
      logger.info("pull: " + mustAddList.size() + " MDs to insert");
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
        SemVer semVer = new SemVer(remoteVersion);
        SemVer minVer = new SemVer("2.32.0");
        int diff = semVer.compareTo(minVer);
        logger.info("Remote registry at " + remoteUrl + " is version " + remoteVersion + " diff=" + diff);
        if (diff >= -1) {  // -1 to include snapshot
          logger.info("pull smart");
          pullSmart(remoteUrl, resLocal.result(), fut);
          return;
        }
        logger.info("pull legacy");
        getList(remoteUrl, null, resRemote -> {
          if (resRemote.failed()) {
            fut.handle(new Failure<>(resRemote.getType(), resRemote.cause()));
            return;
          }
          merge(remoteUrl, resLocal.result(), resRemote.result(), fut);
        });

      });
    });
  }
}
