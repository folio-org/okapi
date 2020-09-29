package org.folio.okapi.service.impl;

import io.vertx.core.Future;
import io.vertx.ext.mongo.MongoClient;
import java.util.List;
import org.folio.okapi.bean.DeploymentDescriptor;
import org.folio.okapi.service.DeploymentStore;

public class DeploymentStoreMongo implements DeploymentStore {

  private static final String COLLECTION = "okapi.deployments";
  private final MongoUtil<DeploymentDescriptor> util;

  public DeploymentStoreMongo(MongoClient cli) {
    this.util = new MongoUtil<>(COLLECTION, cli);
  }

  @Override
  public Future<Void> insert(DeploymentDescriptor dd) {
    return util.add(dd, dd.getInstId());
  }

  @Override
  public Future<Boolean> delete(String id) {
    return util.delete(id);
  }

  @Override
  public Future<Void> init(boolean reset) {
    return util.init(reset);
  }

  @Override
  public Future<List<DeploymentDescriptor>> getAll() {
    return util.getAll(DeploymentDescriptor.class);
  }
}
