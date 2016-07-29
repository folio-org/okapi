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
package okapi.common;

import okapi.common.ErrorType;
import io.vertx.core.AsyncResult;

/**
 * Like vert.x' AsyncResult, but with our enum ErrorType. to distinguish between
 * internal and user errors, etc.
 *
 * @author heikki
 */
public interface ExtendedAsyncResult<T> extends AsyncResult<T> {

  ErrorType getType();
}
