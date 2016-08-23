/*
 * Copyright (C) 2015 Index Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.folio.okapi.util;

import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import java.util.LinkedList;
import java.util.List;

public class LockedTypedMap<T> extends LockedStringMap {

  private final Class<T> clazz;

  public LockedTypedMap(Class<T> c) {
    this.clazz = c;
  }

  public void add(String k, String k2, T value, Handler<ExtendedAsyncResult<Void>> fut) {
    String json = Json.encode(value);
    addOrReplace(false, k, k2, json, fut);
  }

  public void put(String k, String k2, T value, Handler<ExtendedAsyncResult<Void>> fut) {
    String json = Json.encode(value);
    addOrReplace(true, k, k2, json, fut);
  }

  public void get(String k, String k2, Handler<ExtendedAsyncResult<T>> fut) {
    getString(k, k2, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        fut.handle(new Success<>(Json.decodeValue(res.result(), clazz)));
      }
    });
  }

  public void get(String k, Handler<ExtendedAsyncResult<List<T>>> fut) {
    getString(k, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(res.getType(), res.cause()));
      } else {
        LinkedList<T> t = new LinkedList<>();
        for (String s : res.result()) {
          t.add((T) Json.decodeValue(s, clazz));
        }
        fut.handle(new Success<>(t));
      }
    });
  }
}
