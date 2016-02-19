/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
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
 * Module database using Mongo
 * 
 */
public class ModuleStoreMemory implements ModuleStore {
  private final Map<String,ModuleDescriptor> modules = new LinkedHashMap<>();

  public ModuleStoreMemory(Vertx vertx) {
  }

  /**
   * Initialize the whole database.
   * Watch out! Deletes all your modules from the database!
   * @param fut
   */
  @Override
  public void init(Handler<ExtendedAsyncResult<Void>> fut) {
    modules.clear();
    fut.handle(new Success<>());
  }

  @Override
  public void insert(ModuleDescriptor md,
                     Handler<ExtendedAsyncResult<String>> fut) {
    String id = md.getId();
    modules.put(id, md);
    fut.handle(new Success<>(id));
  }

  @Override
  public void get(String id,
                  Handler<ExtendedAsyncResult<ModuleDescriptor>> fut) {
    System.out.println("Trying to get " + id);
    ModuleDescriptor md = modules.get(id);
    if (md == null)
      fut.handle(new Failure<>(NOT_FOUND,""));
    else
      fut.handle(new Success<>(md));
  }

  @Override
  public void getAll(Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut) {
    List<ModuleDescriptor> ml = new ArrayList<>();
    for ( String id : modules.keySet() )
      ml.add(modules.get(id));
    fut.handle(new Success<>(ml));
  }

  @Override
  public void listIds(Handler<ExtendedAsyncResult<List<String>>> fut) {
    List<String> ml = new ArrayList<>();
    for ( String id : modules.keySet() )
      ml.add(id);
    fut.handle(new Success<>(ml));
  }

  @Override
  public void delete(String id,Handler<ExtendedAsyncResult<Void>> fut ) {
    if (modules.containsKey(id)) {
      modules.remove(id);
      fut.handle(new Success<>());
    } else {
      fut.handle(new Failure<>(NOT_FOUND,""));
    }
  }


}
