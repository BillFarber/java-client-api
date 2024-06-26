/*
 * Copyright (c) 2022 MarkLogic Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.marklogic.client.pojo;

import com.marklogic.client.Page;
import java.io.Closeable;

/** Enables pagination over objects retrieved from the server and deserialized by 
 * PojoRepository read and search methods.
 */
public interface PojoPage<T> extends Page<T>, Closeable {
  /** Frees the underlying resources, including the http connection. */
  @Override
  void close();
}
