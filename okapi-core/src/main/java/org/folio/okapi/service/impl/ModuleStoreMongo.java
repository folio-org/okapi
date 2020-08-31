package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.mongo.MongoClient;
import java.util.List;
import org.folio.okapi.bean.ModuleDescriptor;
import org.folio.okapi.common.ExtendedAsyncResult;
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
  public void init(boolean reset, Handler<ExtendedAsyncResult<Void>> fut) {
    util.init(reset, fut);
  }

  @Override
  public void insert(ModuleDescriptor md,
                     Handler<ExtendedAsyncResult<Void>> fut) {
    util.insert(md, md.getId(), fut);
  }

  @Override
  public void update(ModuleDescriptor md,
                     Handler<ExtendedAsyncResult<Void>> fut) {

    util.add(md, md.getId(), fut);
  }

  @Override
  public Future<List<ModuleDescriptor>> getAll() {
    return util.getAll(ModuleDescriptor.class);
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    util.delete(id, fut);
  }

}
