package org.folio.okapi.util;

import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import static org.folio.okapi.common.ErrorType.INTERNAL;
import static org.folio.okapi.common.ErrorType.NOT_FOUND;
import static org.folio.okapi.common.ErrorType.USER;

public class LockedStringMap {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  static class StringMap {

    @JsonProperty
    Map<String, String> strings = new LinkedHashMap<>();
  }

  static class KeyList {

    @JsonProperty
    Set<String> keys = new TreeSet<>();
  }

  AsyncMap<String, String> list = null;
  Vertx vertx = null;
  private final int delay = 10; // ms in recursing for retry of map
  private final String allkeys = "_keys"; // keeps a list of all known keys

  public void init(Vertx vertx, String mapName, Handler<ExtendedAsyncResult<Void>> fut) {
    this.vertx = vertx;
    AsyncMapFactory.<String, String>create(vertx, mapName, res -> {
      if (res.succeeded()) {
        this.list = res.result();
        fut.handle(new Success<>());
      } else {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      }
    });
  }

  public void clear(Handler<ExtendedAsyncResult<Void>> fut) {
    list.clear(res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        fut.handle(new Success<>());
      }
    });
  }

  public void getString(String k, String k2, Handler<ExtendedAsyncResult<String>> fut) {
    list.get(k, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(INTERNAL, resGet.cause()));
      } else {
        String val = resGet.result();
        if (k2 == null) {
          if (val == null) {
            fut.handle(new Failure<>(NOT_FOUND, k));
          } else {
            fut.handle(new Success<>(val));
          }
        } else {
          if (val == null) {
            fut.handle(new Failure<>(NOT_FOUND, k + "/" + k2));
          } else {
            StringMap smap = new StringMap();
            StringMap oldlist = Json.decodeValue(val, StringMap.class);
            smap.strings.putAll(oldlist.strings);
            if (smap.strings.containsKey(k2)) {
              fut.handle(new Success<>(smap.strings.get(k2)));
            }
          }
        }
      }
    });
  }

  public void getString(String k, Handler<ExtendedAsyncResult<Collection<String>>> fut) {
    list.get(k, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(INTERNAL, resGet.cause()));
      } else {
        String val = resGet.result();
        StringMap map;
        if (val != null) {
          map = Json.decodeValue(val, StringMap.class);
        } else {
          map = new StringMap(); // not found, just return an empty map
        }
        fut.handle(new Success<>(map.strings.values()));
      }
    });
  }

  private void getKeysR(Handler<ExtendedAsyncResult<Collection<String>>> fut,
          Set<String> result, Iterator<String> it) {
    if (!it.hasNext()) {
      fut.handle(new Success<>(result));
    } else {
      String k = it.next();
      list.get(k, res -> {
        if (res.failed()) {
          fut.handle(new Failure<>(INTERNAL, res.cause()));
        } else {
          String val = res.result();
          if (val != null) {
            result.add(k);
          }
          getKeysR(fut, result, it);
        }
      });
    }
  }

  public void getKeys(Handler<ExtendedAsyncResult<Collection<String>>> fut) {
    list.get(allkeys, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(INTERNAL, resGet.cause()));
      } else {
        String val = resGet.result();
        if (val != null && !val.isEmpty()) {
          KeyList keys = Json.decodeValue(val, KeyList.class);
          getKeysR(fut, new LinkedHashSet<>(), keys.keys.iterator());
        } else {
          KeyList nokeys = new KeyList();
          fut.handle(new Success<>(nokeys.keys));
        }
      }
    });
  }


  private void addKey(String k, Handler<ExtendedAsyncResult<Void>> fut) {
    KeyList klist = new KeyList();
    list.get(allkeys, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(INTERNAL, resGet.cause()));
      } else {
        String oldVal = resGet.result();
        if (oldVal != null) {
          KeyList oldlist = Json.decodeValue(oldVal, KeyList.class);
          klist.keys.addAll(oldlist.keys);
        }
        if (klist.keys.contains(k)) {
          fut.handle(new Success<>());
        } else {
          klist.keys.add(k);
          String newVal = Json.encodePrettily(klist);
          if (oldVal == null) { // new entry
            list.putIfAbsent(allkeys, newVal, resPut -> {
              if (resPut.succeeded()) {
                if (resPut.result() == null) {
                  fut.handle(new Success<>());
                } else { // Someone messed with it, try again
                  vertx.setTimer(delay, res -> {
                    addKey(k, fut);
                  });
                }
              } else {
                fut.handle(new Failure<>(INTERNAL, resPut.cause()));
              }
            });
          } else { // existing entry, put and retry if someone else messed with it
            list.replaceIfPresent(allkeys, oldVal, newVal, resRepl -> {
              if (resRepl.succeeded()) {
                if (resRepl.result()) {
                  fut.handle(new Success<>());
                } else {
                  vertx.setTimer(delay, res -> {
                    addKey(k, fut);
                  });
                }
              } else {
                fut.handle(new Failure<>(INTERNAL, resRepl.cause()));
              }
            });
          }
        }
      }
    });
  }

  public void addOrReplace(boolean allowReplace, String k, String k2, String value, Handler<ExtendedAsyncResult<Void>> fut) {
    list.get(k, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(INTERNAL, resGet.cause()));
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
            fut.handle(new Failure<>(USER, "Duplicate instance " + k2));
            return;
          }
          smap.strings.put(k2, value);
          newVal = Json.encodePrettily(smap);
        }
        if (oldVal == null) { // new entry
          list.putIfAbsent(k, newVal, resPut -> {
            if (resPut.succeeded()) {
              if (resPut.result() == null) {
                addKey(k, fut);
              } else { // Someone messed with it, try again
                vertx.setTimer(delay, res -> {
                  addOrReplace(allowReplace, k, k2, value, fut);
                });
              }
            } else {
              fut.handle(new Failure<>(INTERNAL, resPut.cause()));
            }
          });
        } else { // existing entry, put and retry if someone else messed with it
          list.replaceIfPresent(k, oldVal, newVal, resRepl -> {
            if (resRepl.succeeded()) {
              if (resRepl.result()) {
                addKey(k, fut);
              } else {
                vertx.setTimer(delay, res -> {
                  addOrReplace(allowReplace, k, k2, value, fut);
                });
              }
            } else {
              fut.handle(new Failure<>(INTERNAL, resRepl.cause()));
            }
          });
        }
      } // get success
    });
  }

  public void remove(String k, Handler<ExtendedAsyncResult<Boolean>> fut) {
    remove(k, null, fut);
  }

  public void remove(String k, String k2, Handler<ExtendedAsyncResult<Boolean>> fut) {
    list.get(k, resGet -> {
      if (resGet.failed()) {
        fut.handle(new Failure<>(INTERNAL, resGet.cause()));
      } else {
        String val = resGet.result();
        if (val == null) {
          fut.handle(new Failure<>(NOT_FOUND, k));
          return;
        }
        StringMap smap = new StringMap();
        if (k2 != null) {
          smap = Json.decodeValue(val, StringMap.class);
          if (!smap.strings.containsKey(k2)) {
            fut.handle(new Failure<>(NOT_FOUND, k + "/" + k2));
            return;
          }
          smap.strings.remove(k2);
        }
        if (smap.strings.isEmpty()) {
          list.removeIfPresent(k, val, resDel -> {
            if (resDel.succeeded()) {
              if (resDel.result()) {
                fut.handle(new Success<>(true));
                // Note that we don't remove from the allkeys list.
                // That could lead to race conditions, better to have
                // unused entries in the allkeys list.
              } else {
                vertx.setTimer(delay, res -> {
                  remove(k, k2, fut);
                });
              }
            } else {
              fut.handle(new Failure<>(INTERNAL, resDel.cause()));
            }
          });
        } else { // list was not empty, remove value
          String newVal = Json.encodePrettily(smap);
          list.replaceIfPresent(k, val, newVal, resPut -> {
            if (resPut.succeeded()) {
              if (resPut.result()) {
                fut.handle(new Success<>(false));
              } else {
                vertx.setTimer(delay, res -> {
                  remove(k, k2, fut);
                });
              }
            } else {
              fut.handle(new Failure<>(INTERNAL, resPut.cause()));
            }
          });
        }
      }
    });
  }

}
