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
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import okapi.bean.ModuleDescriptor;
import static okapi.util.ErrorType.*;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import okapi.util.Success;

/**
 * Implements a mock in-memory storage for the modules.
 */
public class ModuleStoreMemory implements ModuleStore {

  private final Map<String, ModuleDescriptor> modules = new LinkedHashMap<>();

  public ModuleStoreMemory(Vertx vertx) {
  }

  @Override
  public void insert(ModuleDescriptor md,
          Handler<ExtendedAsyncResult<String>> fut) {
    String id = md.getId();
    if (modules.containsKey(id)) {
      fut.handle(new Failure<>(USER, "Duplicate module id '" + id + "' in in-memory insert"));
    } else {
      modules.put(id, new ModuleDescriptor(md));
      fut.handle(new Success<>(id));
    }
  }

  @Override
  public void update(ModuleDescriptor md,
          Handler<ExtendedAsyncResult<String>> fut) {
    String id = md.getId();
    if (!modules.containsKey(id)) {
      fut.handle(new Failure<>(NOT_FOUND, "Module " + id + " not found, can not update (inmemory-db)"));
    } else {
      modules.put(id, new ModuleDescriptor(md));
      fut.handle(new Success<>(id));
    }
  }

  @Override
  public void get(String id,
          Handler<ExtendedAsyncResult<ModuleDescriptor>> fut) {
    ModuleDescriptor md = modules.get(id);
    if (md == null) {
      fut.handle(new Failure<>(NOT_FOUND, ""));
    } else {
      fut.handle(new Success<>(new ModuleDescriptor(md)));
    }
  }

  @Override
  public void getAll(Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {
    List<ModuleDescriptor> ml = new ArrayList<>();
    for (String id : modules.keySet()) {
      ml.add(new ModuleDescriptor(modules.get(id)));
    }
    fut.handle(new Success<>(ml));
  }

  @Override
  public void listIds(Handler<ExtendedAsyncResult<List<String>>> fut) {
    List<String> ml = new ArrayList<>();
    for (String id : modules.keySet()) {
      ml.add(id);
    }
    fut.handle(new Success<>(ml));
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    if (modules.containsKey(id)) {
      modules.remove(id);
      fut.handle(new Success<>());
    } else {
      fut.handle(new Failure<>(NOT_FOUND, ""));
    }
  }

}
