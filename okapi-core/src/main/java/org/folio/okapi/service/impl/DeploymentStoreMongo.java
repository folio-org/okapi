package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.mongo.MongoClient;
import java.util.List;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.common.ExtendedAsyncResult;
import org.folio.okapi.service.DeploymentStore;


public class DeploymentStoreMongo implements DeploymentStore {

  private static final String COLLECTION = "okapi.deployments";
  private final MongoUtil<DeploymentDescriptor> util;

  public DeploymentStoreMongo(MongoClient cli) {
    this.util = new MongoUtil<>(COLLECTION, cli);
  }

  @Override
  public void insert(DeploymentDescriptor dd, Handler<ExtendedAsyncResult<Void>> fut) {
    util.add(dd, dd.getInstId(), fut);
  }

  @Override
  public void delete(String id, Handler<ExtendedAsyncResult<Void>> fut) {
    util.delete(id, fut);
  }

  @Override
  public void init(boolean reset, Handler<ExtendedAsyncResult<Void>> fut) {
    util.init(reset, fut);
  }

  @Override
  public Future<List<DeploymentDescriptor>> getAll() {
    return util.getAll(DeploymentDescriptor.class);
  }
}
