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
package okapi.service;

import io.vertx.core.Handler;
import java.util.List;
import okapi.bean.Tenant;
import okapi.bean.TenantDescriptor;
import okapi.util.ExtendedAsyncResult;

/**
 *
 * @author heikki
 */
public interface TenantStore {

  void delete(String id, Handler<ExtendedAsyncResult<Void>> fut);

  void enableModule(String id, String module, long timestamp, Handler<ExtendedAsyncResult<Void>> fut);

  void disableModule(String id, String module, long timestamp, Handler<ExtendedAsyncResult<Void>> fut);

  void get(String id, Handler<ExtendedAsyncResult<Tenant>> fut);

  void insert(Tenant t, Handler<ExtendedAsyncResult<String>> fut);

  void update(Tenant t, Handler<ExtendedAsyncResult<String>> fut);

  void updateDescriptor(String id, TenantDescriptor td, Handler<ExtendedAsyncResult<Void>> fut);

  void listIds(Handler<ExtendedAsyncResult<List<String>>> fut);

  void listTenants(Handler<ExtendedAsyncResult<List<Tenant>>> fut);
  // TODO - Add list parameters, like which fields, start, and maxrecs

}
