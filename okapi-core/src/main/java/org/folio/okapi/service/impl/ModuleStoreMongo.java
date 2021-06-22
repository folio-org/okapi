package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import io.vertx.ext.mongo.MongoClient;
import java.util.List;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.service.ModuleStore;

/**
 * Stores ModuleDescriptors in a Mongo database.
 */
public class ModuleStoreMongo implements ModuleStore {

  private static final String COLLECTION = "okapi.modules";
  private final MongoUtil<ModuleDescriptor> util;


  public ModuleStoreMongo(MongoClient cli) {
    this.util = new MongoUtil<>(COLLECTION, cli);
  }

  @Override
  public Future<Void> init(boolean reset) {
    return util.init(reset);
  }

  @Override
  public Future<Void> insert(List<ModuleDescriptor> mds) {
    return util.insertBatch(mds);
  }

  @Override
  public Future<List<ModuleDescriptor>> getAll() {
    return util.getAll(ModuleDescriptor.class);
  }

  @Override
  public Future<Boolean> delete(String id) {
    return util.delete(id);
  }

}
