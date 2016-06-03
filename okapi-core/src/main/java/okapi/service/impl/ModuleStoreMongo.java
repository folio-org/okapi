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
package okapi.service.impl;

import okapi.service.ModuleStore;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import java.util.ArrayList;
import java.util.List;
import okapi.bean.ModuleDescriptor;
import static okapi.util.ErrorType.*;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import okapi.util.Success;

public class ModuleStoreMongo implements ModuleStore {

  private final Logger logger = LoggerFactory.getLogger("okapi");

  MongoClient cli;
  final private String collection = "okapi.modules";

  public ModuleStoreMongo(MongoHandle mongo) {
    this.cli = mongo.getClient();
  }

  @Override
  public void insert(ModuleDescriptor md,
          Handler<ExtendedAsyncResult<String>> fut) {
    String s = Json.encodePrettily(md);
    JsonObject document = new JsonObject(s);
    String id = md.getId();
    document.put("_id", id);
    cli.insert(collection, document, res -> {
      if (res.succeeded()) {
        fut.handle(new Success<>(id));
      } else {
        logger.debug("ModuleDbMongo: Failed to insert " + id
                + ": " + res.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      }
    });
  }

  @Override
  public void update(ModuleDescriptor md,
          Handler<ExtendedAsyncResult<String>> fut) {
    String id = md.getId();
    final String q = "{ \"_id\": \"" + id + "\"}";
    JsonObject jq = new JsonObject(q);
    String s = Json.encodePrettily(md);
    JsonObject document = new JsonObject(s);
    document.put("_id", id);
    cli.replace(collection, jq, document, res -> {
      if (res.succeeded()) {
        fut.handle(new Success<>(id));
      } else {
        logger.debug("ModuleDbMongo: Failed to update" + id
                + ": " + res.cause().getMessage());
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      }
    });
  }

  @Override
  public void get(String id,
          Handler<ExtendedAsyncResult<ModuleDescriptor>> fut) {
    final String q = "{ \"_id\": \"" + id + "\"}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        List<JsonObject> l = res.result();
        if (l.size() == 0) {
          fut.handle(new Failure<>(NOT_FOUND, "Module " + id + " not found"));
        } else {
          JsonObject d = l.get(0);
          d.remove("_id");
          final ModuleDescriptor md = Json.decodeValue(d.encode(),
                  ModuleDescriptor.class);
          fut.handle(new Success<>(md));
        }
      }
    });
  }

  @Override
  public void getAll(Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {
    final String q = "{}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        List<JsonObject> resl = res.result();
        List<ModuleDescriptor> ml = new ArrayList<>(resl.size());
        for (JsonObject jo : resl) {
          jo.remove("_id");
          ModuleDescriptor md = Json.decodeValue(jo.encode(),
                  ModuleDescriptor.class);
          ml.add(md);
        }
        fut.handle(new Success<>(ml));
      }
    });
  }

  @Override
  public void listIds(Handler<ExtendedAsyncResult<List<String>>> fut) {
    String q = "{}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, res -> {
      if (res.failed()) {
        fut.handle(new Failure<>(INTERNAL, res.cause()));
      } else {
        List<String> ids = new ArrayList<>(res.result().size());
        for (JsonObject jo : res.result()) {
          ids.add(jo.getString("id"));
        }
        fut.handle(new Success<>(ids));
      }
    });
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    String q = "{ \"id\": \"" + id + "\"}";
    JsonObject jq = new JsonObject(q);
    cli.find(collection, jq, fres -> {
      if (fres.failed()) {
        fut.handle(new Failure<>(INTERNAL, fres.cause()));
      } else {
        List<JsonObject> l = fres.result();
        if (l.size() == 0) {
          fut.handle(new Failure<>(NOT_FOUND, fres.cause()));
        } else {
          cli.remove(collection, jq, rres -> {
            if (rres.failed()) {
              fut.handle(new Failure<>(INTERNAL, rres.cause()));
            } else {
              fut.handle(new Success<>());
            }
          });
        }
      }
    });
  }

}
