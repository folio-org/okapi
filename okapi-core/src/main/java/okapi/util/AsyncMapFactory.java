/*
 * Copyright (C) 2016 Index Data
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
package okapi.util;

import okapi.common.ExtendedAsyncResult;
import okapi.common.Failure;
import okapi.common.Success;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;
import okapi.common.ExtendedAsyncResult;
import okapi.common.Failure;
import okapi.common.Success;
import static okapi.common.ErrorType.*;

/**
 * Factory to create either a vert.x ClusterWideMap or a
 * AsyncLocalmap, if not running in a clustered mode.
 */
public class AsyncMapFactory {

  public static <K, V> void create(Vertx vertx, String mapName,
          Handler<ExtendedAsyncResult<AsyncMap<K, V>>> fut) {
    if (vertx.isClustered()) {
      SharedData shared = vertx.sharedData();
      shared.<K, V>getClusterWideMap(mapName, res -> {
        if (res.succeeded()) {
          fut.handle(new Success<>(res.result()));
        } else {
          fut.handle(new Failure<>(INTERNAL, res.cause()));
        }
      });
    } else {
      AsyncLocalmap<K, V> l = new AsyncLocalmap<>(vertx, mapName);
      fut.handle(new Success<>(l));
    }
  }
}
