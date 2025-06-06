package org.folio.okapi.service;

import io.vertx.core.Future;
import java.util.List;
import java.util.SortedMap;
import org.folio.okapi.bean.Tenant;
import org.folio.okapi.bean.TenantDescriptor;

public interface TenantStore {

  Future<Boolean> delete(String id);

  Future<Boolean> updateModules(String id, SortedMap<String, Boolean> enabled);

  Future<Void> insert(Tenant t);

  Future<Void> updateDescriptor(TenantDescriptor td);

  Future<List<Tenant>> listTenants();

  Future<Void> init(boolean reset);
}
