package org.folio.okapi.env;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.folio.okapi.bean.EnvEntry;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import org.folio.okapi.util.LockedTypedMap1;

public class EnvManager {

  private final Logger logger = LoggerFactory.getLogger("okapi");
  private LockedTypedMap1<EnvEntry> envMap = new LockedTypedMap1<>(EnvEntry.class);

  public void init(Vertx vertx, Handler<ExtendedAsyncResult<Void>> fut) {
    envMap.init(vertx, "env", res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        fut.handle(new Success<>());
      }
    });
  }

  public void add(EnvEntry env, Handler<ExtendedAsyncResult<Void>> fut) {
    envMap.add(env.getName(), env, fut);
  }

  public void get(String name, Handler<ExtendedAsyncResult<EnvEntry>> fut) {
    envMap.get(name, fut);
  }

  private void getR(Iterator<String> it, List<EnvEntry> all,
          Handler<ExtendedAsyncResult<List<EnvEntry>>> fut) {
    if (!it.hasNext()) {
      fut.handle(new Success<>(all));
    } else {
      String srvcId = it.next();
      get(srvcId, resGet -> {
        if (resGet.failed()) {
          fut.handle(new Failure<>(resGet.getType(), resGet.cause()));
        } else {
          EnvEntry dpl = resGet.result();
          all.add(dpl);
          getR(it, all, fut);
        }
      });
    }
  }

  public void get(Handler<ExtendedAsyncResult<List<EnvEntry>>> fut) {
    envMap.getKeys(resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(resGet.getType(), resGet.cause()));
      } else {
        Collection<String> keys = resGet.result();
        List<EnvEntry> all = new LinkedList<>();
        if (keys == null || keys.isEmpty()) {
          fut.handle(new Success<>(all));
        } else {
          getR(keys.iterator(), all, fut);
        }
      }
    });
  }

  public void remove(String name, Handler<ExtendedAsyncResult<Boolean>> fut) {
    envMap.remove(name, fut);
  }
}
