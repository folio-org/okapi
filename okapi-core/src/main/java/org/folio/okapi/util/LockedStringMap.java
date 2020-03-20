package org.folio.okapi.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.ErrorType;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Messages;
import org.folio.okapi.common.OkapiLogger;
import org.folio.okapi.common.Success;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.shareddata.AsyncMap;

public class LockedStringMap {

  static class StringMap {

    @JsonProperty
    Map<String, String> strings = new LinkedHashMap<>();
  }

  private AsyncMap<String, String> list = null;
  private Vertx vertx = null;
  private static final int DELAY = 10; // ms in recursing for retry of map
  protected final Logger logger = OkapiLogger.get();
  private Messages messages = Messages.getInstance();

  public void init(Vertx vertx, String mapName, Handler<ExtendedAsyncResult<Void>> fut) {
    this.vertx = vertx;
    AsyncMapFactory.<String, String>create(vertx, mapName, res -> {
      if (res.succeeded()) {
        this.list = res.result();
        fut.handle(new Success<>());
      } else {
        fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
      }
    });
  }

  public void size(Handler<AsyncResult<Integer>> fut) {
    list.size(fut);
  }

  public void getString(String k, String k2, Handler<ExtendedAsyncResult<String>> fut) {
    list.get(k, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, resGet.cause()));
      } else {
        String val = resGet.result();
        if (k2 == null) {
          if (val == null) {
            fut.handle(new Failure<>(ErrorType.NOT_FOUND, k));
          } else {
            fut.handle(new Success<>(val));
          }
        } else {
          if (val == null) {
            fut.handle(new Failure<>(ErrorType.NOT_FOUND, k + "/" + k2));
          } else {
            StringMap smap = new StringMap();
            StringMap oldlist = Json.decodeValue(val, StringMap.class);
            smap.strings.putAll(oldlist.strings);
            if (smap.strings.containsKey(k2)) {
              fut.handle(new Success<>(smap.strings.get(k2)));
            } else {
              fut.handle(new Failure<>(ErrorType.NOT_FOUND, k + "/" + k2));
            }
          }
        }
      }
    });
  }

  public void getString(String k, Handler<ExtendedAsyncResult<Collection<String>>> fut) {
    list.get(k, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, resGet.cause()));
      } else {
        String val = resGet.result();
        StringMap map;
        if (val != null) {
          map = Json.decodeValue(val, StringMap.class);
          fut.handle(new Success<>(map.strings.values()));
        } else {
          fut.handle(new Failure<>(ErrorType.NOT_FOUND, k));
        }
      }
    });
  }

  public void getKeys(Handler<ExtendedAsyncResult<Collection<String>>> fut) {
    list.keys(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, res.cause()));
      } else {
        List<String> s2 = new ArrayList<>(res.result());
        java.util.Collections.sort(s2);
        fut.handle(new Success<>(s2));
      }
    });
  }

  public void addOrReplace(boolean allowReplace, String k, String k2, String value,
    Handler<ExtendedAsyncResult<Void>> fut) {
    list.get(k, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, resGet.cause()));
      } else {
        String oldVal = resGet.result();
        String newVal;
        if (k2 == null) {
          newVal = value;
        } else {
          StringMap smap = new StringMap();
          if (oldVal != null) {
            StringMap oldlist = Json.decodeValue(oldVal, StringMap.class);
            smap.strings.putAll(oldlist.strings);
          }
          if (!allowReplace && smap.strings.containsKey(k2)) {
            fut.handle(new Failure<>(ErrorType.USER, messages.getMessage("11400", k2)));
            return;
          }
          smap.strings.put(k2, value);
          newVal = Json.encodePrettily(smap);
        }
        addOrReplace2(allowReplace, k, k2, value, oldVal, newVal, fut);
      } // get success
    });
  }

  private void addOrReplace2(boolean allowReplace, String k, String k2, String value,
    String oldVal, String newVal, Handler<ExtendedAsyncResult<Void>> fut) {

    if (oldVal == null) { // new entry
      list.putIfAbsent(k, newVal, resPut -> {
        if (resPut.succeeded()) {
          if (resPut.result() == null) {
            fut.handle(new Success<>());
          } else { // Someone messed with it, try again
            vertx.setTimer(DELAY, res
              -> addOrReplace(allowReplace, k, k2, value, fut));
          }
        } else {
          fut.handle(new Failure<>(ErrorType.INTERNAL, resPut.cause()));
        }
      });
    } else { // existing entry, put and retry if someone else messed with it
      list.replaceIfPresent(k, oldVal, newVal, resRepl -> {
        if (resRepl.succeeded()) {
          if (Boolean.TRUE.equals(resRepl.result())) {
            fut.handle(new Success<>());
          } else {
            vertx.setTimer(DELAY, res
              -> addOrReplace(allowReplace, k, k2, value, fut));
          }
        } else {
          fut.handle(new Failure<>(ErrorType.INTERNAL, resRepl.cause()));
        }
      });
    }
  }

  public void remove(String k, Handler<ExtendedAsyncResult<Boolean>> fut) {
    remove(k, null, fut);
  }

  public void remove(String k, String k2,
    Handler<ExtendedAsyncResult<Boolean>> fut) {

    list.get(k, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(ErrorType.INTERNAL, resGet.cause()));
      } else {
        String val = resGet.result();
        if (val == null) {
          fut.handle(new Failure<>(ErrorType.NOT_FOUND, k));
          return;
        }
        StringMap smap = new StringMap();
        if (k2 != null) {
          smap = Json.decodeValue(val, StringMap.class);
          if (!smap.strings.containsKey(k2)) {
            fut.handle(new Failure<>(ErrorType.NOT_FOUND, k + "/" + k2));
            return;
          }
          smap.strings.remove(k2);
        }
        remove2(k, k2, smap, val, fut);
      }
    });
  }

  private void remove2(String k, String k2, StringMap smap, String val,
    Handler<ExtendedAsyncResult<Boolean>> fut) {

    if (smap.strings.isEmpty()) {
      list.removeIfPresent(k, val, resDel -> {
        if (resDel.succeeded()) {
          if (Boolean.TRUE.equals(resDel.result())) {
            fut.handle(new Success<>(true));
          } else {
            vertx.setTimer(DELAY, res -> remove(k, k2, fut));
          }
        } else {
          fut.handle(new Failure<>(ErrorType.INTERNAL, resDel.cause()));
        }
      });
    } else { // list was not empty, remove value
      String newVal = Json.encodePrettily(smap);
      list.replaceIfPresent(k, val, newVal, resPut -> {
        if (resPut.succeeded()) {
          if (Boolean.TRUE.equals(resPut.result())) {
            fut.handle(new Success<>(false));
          } else {
            vertx.setTimer(DELAY, res -> remove(k, k2, fut));
          }
        } else {
          fut.handle(new Failure<>(ErrorType.INTERNAL, resPut.cause()));
        }
      });
    }
  }

}
