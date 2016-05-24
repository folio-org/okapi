/*
 * Copyright (C) 2015 Index Data
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
import okapi.bean.ModuleDescriptor;
import okapi.util.ExtendedAsyncResult;

/**
 *
 *
 */
public interface ModuleStore {

  void delete(String id, Handler<ExtendedAsyncResult<Void>> fut);

  void get(String id, Handler<ExtendedAsyncResult<ModuleDescriptor>> fut);

  void getAll(Handler<ExtendedAsyncResult<List<ModuleDescriptor>>> fut);

  void insert(ModuleDescriptor md, Handler<ExtendedAsyncResult<String>> fut);

  void update(ModuleDescriptor md, Handler<ExtendedAsyncResult<String>> fut);

  void listIds(Handler<ExtendedAsyncResult<List<String>>> fut);

}
