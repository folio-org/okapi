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

  private void getRemoteUrl(Iterator<String> it,
    Handler<ExtendedAsyncResult<String>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Failure<>(ErrorType.NOT_FOUND, "pull: none of remote URls work"));
    } else {
      final String baseUrl = it.next();
      String url = baseUrl;
      if (!url.endsWith("/")) {
        url += "/";
      }
      url += "_/version";
      Buffer body = Buffer.buffer();
      HttpClientRequest req = httpClient.getAbs(url, res -> {
        res.handler(x -> {
          body.appendBuffer(x);
        });
        res.endHandler(x -> {
          if (res.statusCode() != 200) {
            fut.handle(new Failure<>(ErrorType.USER, body.toString()));
          } else {
            fut.handle(new Success<>(baseUrl));
          }
        });
        res.exceptionHandler(x -> {
          fut.handle(new Failure<>(ErrorType.INTERNAL, x.getMessage()));
        });
      });
      req.exceptionHandler(res -> {
        getRemoteUrl(it, fut);
      });
      req.end();
    }
  }

  private void getList(String urlBase,
    Handler<ExtendedAsyncResult<ModuleDescriptorBrief[]>> fut) {
    String url = urlBase;
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
          ModuleDescriptorBrief ml[] = Json.decodeValue(body.toString(),
            ModuleDescriptorBrief[].class);
          for (ModuleDescriptorBrief mdb : ml) {
          }
          fut.handle(new Success<>(ml));
        }
      });
      res.exceptionHandler(x -> {
        logger.warn("exception handler 1");
        fut.handle(new Failure<>(ErrorType.INTERNAL, x.getMessage()));
      });
    });
    req.exceptionHandler(x -> {
      logger.warn("exception handler 1");
      fut.handle(new Failure<>(ErrorType.INTERNAL, x.getMessage()));
    });
    req.end();
  }

  private void getFull(String urlBase, Iterator<ModuleDescriptorBrief> it,
    List<ModuleDescriptor> ml,
    Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Success<>(ml));
    } else {
      String url = urlBase;
      if (!url.endsWith("/")) {
        url += "/";
      }
      url += "_/proxy/modules/" + it.next().getId();
      Buffer body = Buffer.buffer();
      HttpClientRequest req = httpClient.getAbs(url, res -> {
        res.handler(x -> {
          body.appendBuffer(x);
        });
        res.endHandler(x -> {
          if (res.statusCode() != 200) {
            fut.handle(new Failure<>(ErrorType.USER, body.toString()));
          } else {
            ModuleDescriptor md = Json.decodeValue(body.toString(),
              ModuleDescriptor.class);
            ml.add(md);
            getFull(urlBase, it, ml, fut);
          }
        });
        res.exceptionHandler(x -> {
          logger.warn("exception handler 1");
          fut.handle(new Failure<>(ErrorType.INTERNAL, x.getMessage()));
        });
      });
      req.exceptionHandler(x -> {
        logger.warn("exception handler 2");
        fut.handle(new Failure<>(ErrorType.INTERNAL, x.getMessage()));
      });
      req.end();
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
        logger.warn("exception handler 1");
        fut.handle(new Failure<>(ErrorType.INTERNAL, x.getMessage()));
      });
    });
    req.exceptionHandler(x -> {
      logger.warn("exception handler 2");
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
      final ModuleDescriptor mdf = md;
      addFull(okapiUrl, md, res -> {
        if (res.failed()) {
          addList(enabled, it, added, failed + 1, fut);
        } else {
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

  private void merge(String urlBase, ModuleDescriptorBrief[] mlLocal,
    ModuleDescriptorBrief[] mlRemote, Handler<ExtendedAsyncResult<List<ModuleDescriptorBrief>>> fut) {

    TreeMap<String, Boolean> enabled = new TreeMap<>();
    for (ModuleDescriptorBrief md : mlLocal) {
      enabled.put(md.getId(), false);
    }

    List<ModuleDescriptorBrief> mlAdd = new LinkedList<>();
    for (ModuleDescriptorBrief md : mlRemote) {
      if (enabled.get(md.getId()) == null) {
        mlAdd.add(md);
      }
    }
    List<ModuleDescriptor> mlList = new LinkedList<>();
    getFull(urlBase, mlAdd.iterator(), mlList, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
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

  public void pull(PullDescriptor pd, Handler<ExtendedAsyncResult<List<ModuleDescriptorBrief>>> fut) {
    getRemoteUrl(Arrays.asList(pd.getUrls()).iterator(), resUrl -> {
      if (resUrl.failed()) {
        fut.handle(new Failure<>(resUrl.getType(), resUrl.cause()));
      } else {
        getList(okapiUrl, resLocal -> {
          if (resLocal.failed()) {
            fut.handle(new Failure<>(resLocal.getType(), resLocal.cause()));
          } else {
            ModuleDescriptorBrief[] mlLocal = resLocal.result();
            getList(resUrl.result(), resRemote -> {
              if (resRemote.failed()) {
                fut.handle(new Failure<>(resRemote.getType(), resRemote.cause()));
              } else {
                merge(resUrl.result(), resLocal.result(), resRemote.result(), fut);
              }
            });
          }
        });
        ;
      }
    });
  }
}
