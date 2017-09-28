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
import java.util.TreeMap;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.bean.PullDescriptor;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

public class PullManager {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final String okapiUrl;
  private final HttpClient httpClient;
  private int concurrentRuns;
  private static final int CONCURRENT_MAX = 10;
  private boolean concurrentComplete;

  public PullManager(Vertx vertx, String okapiUrl) {
    this.okapiUrl = okapiUrl;
    this.httpClient = vertx.createHttpClient();
  }

  private void getRemoteUrl(Iterator<String> it,
    Handler<ExtendedAsyncResult<String>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Failure<>(ErrorType.NOT_FOUND, "pull: none of remote URLs work"));
    } else {
      final String baseUrl = it.next();
      String url = baseUrl;
      if (!url.endsWith("/")) {
        url += "/";
      }
      url += "_/version";
      final Buffer body = Buffer.buffer();
      HttpClientRequest req = httpClient.getAbs(url, res -> {
        res.handler(x -> body.appendBuffer(x));
        res.endHandler(x -> {
          if (res.statusCode() != 200) {
            logger.info("pull for " + baseUrl + " failed with status "
                    + res.statusCode());
            fut.handle(new Failure<>(ErrorType.USER, body.toString()));
          } else {
            fut.handle(new Success<>(baseUrl));
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
    Handler<ExtendedAsyncResult<ModuleDescriptor[]>> fut) {
    String url = urlBase;
    if (!url.endsWith("/")) {
      url += "/";
    }
    url += "_/proxy/modules";
    final Buffer body = Buffer.buffer();
    HttpClientRequest req = httpClient.getAbs(url, res -> {
      res.handler(x -> {
        body.appendBuffer(x);
      });
      res.endHandler(x -> {
        if (res.statusCode() != 200) {
          fut.handle(new Failure<>(ErrorType.USER, body.toString()));
        } else {
          ModuleDescriptor[] ml = Json.decodeValue(body.toString(),
            ModuleDescriptor[].class);
          fut.handle(new Success<>(ml));
        }
      });
      res.exceptionHandler(x -> {
        fut.handle(new Failure<>(ErrorType.INTERNAL, x.getMessage()));
      });
    });
    req.exceptionHandler(x -> {
      fut.handle(new Failure<>(ErrorType.INTERNAL, x.getMessage()));
    });
    req.end();
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
      final Buffer body = Buffer.buffer();
      HttpClientRequest req = httpClient.getAbs(url, res -> {
        res.handler(x -> {
          body.appendBuffer(x);
        });
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
    if (!it.hasNext() && !concurrentComplete && concurrentRuns == 0) {
      concurrentComplete = true;
      fut.handle(new Success<>(ml));
    }
  }

  private void addFull(String urlBase, ModuleDescriptor ml,
    Handler<ExtendedAsyncResult<ModuleDescriptor>> fut) {
    String url = urlBase;
    if (!url.endsWith("/")) {
      url += "/";
    }
    url += "_/proxy/modules";
    Buffer body = Buffer.buffer();
    HttpClientRequest req = httpClient.postAbs(url, res -> {
      res.handler(x -> {
        body.appendBuffer(x);
      });
      res.endHandler(x -> {
        if (res.statusCode() != 201) {
          fut.handle(new Failure<>(ErrorType.USER, body.toString()));
        } else {
          ModuleDescriptor md = Json.decodeValue(body.toString(),
            ModuleDescriptor.class);
          fut.handle(new Success<>(md));
        }
      });
      res.exceptionHandler(x -> {
        fut.handle(new Failure<>(ErrorType.INTERNAL, x.getMessage()));
      });
    });
    req.exceptionHandler(x -> {
      fut.handle(new Failure<>(ErrorType.INTERNAL, x.getMessage()));
    });
    req.end(Json.encodePrettily(ml));
  }

  private void addList(TreeMap<String, Boolean> enabled, Iterator<ModuleDescriptor> it, int added, int failed,
    Handler<ExtendedAsyncResult<Boolean>> fut) {
    ModuleDescriptor md = null;
    while (it.hasNext()) {
      md = it.next();
      String id = md.getId();
      if (enabled.get(id) == null) {
        break;
      }
      md = null;
    }
    if (md == null) {
      if (failed == 0 && added == 0) {
        fut.handle(new Success<>(Boolean.FALSE));
      } else if (added > 0) {
        fut.handle(new Success<>(Boolean.TRUE));
      } else {
        fut.handle(new Failure<>(ErrorType.INTERNAL, "pull: cannot add list"));
      }
    } else {
      final String id = md.getId();
      final ModuleDescriptor mdf = md;
      addFull(okapiUrl, md, res -> {
        if (res.failed()) {
          logger.info("adding md " + id + " failed: " + res.cause().getMessage());
          addList(enabled, it, added, failed + 1, fut);
        } else {
          logger.info("adding md " + id + " OK");
          enabled.put(mdf.getId(), true);
          addList(enabled, it, added + 1, failed, fut);
        }
      });
    }
  }

  private void addRepeat(TreeMap<String, Boolean> enabled, List<ModuleDescriptor> md,
    Handler<ExtendedAsyncResult<Void>> fut) {
    addList(enabled, md.iterator(), 0, 0, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        if (res.result()) {
          addRepeat(enabled, md, fut);
        } else {
          fut.handle(new Success<>());
        }
      }
    });
  }

  private void merge(String urlBase, ModuleDescriptor[] mlLocal,
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
        addRepeat(enabled, mlList, res1 -> {
          if (res1.failed()) {
            fut.handle(new Failure<>(res1.getType(), res1.cause()));
          } else {
            fut.handle(new Success<>(mlAdd));
          }
        });
      }
    });
  }

  public void pull(PullDescriptor pd, Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {
    getRemoteUrl(Arrays.asList(pd.getUrls()).iterator(), resUrl -> {
      if (resUrl.failed()) {
        fut.handle(new Failure<>(resUrl.getType(), resUrl.cause()));
      } else {
        getList(okapiUrl, resLocal -> {
          if (resLocal.failed()) {
            fut.handle(new Failure<>(resLocal.getType(), resLocal.cause()));
          } else {
            getList(resUrl.result(), resRemote -> {
              if (resRemote.failed()) {
                fut.handle(new Failure<>(resRemote.getType(), resRemote.cause()));
              } else {
                merge(resUrl.result(), resLocal.result(), resRemote.result(), fut);
              }
            });
          }
        });
      }
    });
  }
}
