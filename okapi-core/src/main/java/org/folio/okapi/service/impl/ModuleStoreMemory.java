package org.folio.okapi.service.impl;

import org.folio.okapi.service.ModuleStore;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.folio.okapi.bean.ModuleDescriptor;
import static org.folio.okapi.common.ErrorType.*;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.common.Failure;
import org.folio.okapi.common.Success;

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
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    if (modules.containsKey(id)) {
      modules.remove(id);
      fut.handle(new Success<>());
    } else {
      fut.handle(new Failure<>(NOT_FOUND, ""));
    }
  }

}
