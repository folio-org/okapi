/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;

/**
 * Encapsulating vert.x LocalMap so it looks like a ClusterWideMap
 */
public class AsyncLocalmap<K, V> implements AsyncMap<K, V> {

  LocalMap<K, V> map = null;

  public AsyncLocalmap(Vertx vertx) {
    SharedData sd = vertx.sharedData();
    this.map = sd.getLocalMap("mymap1");
  }

  @Override
  public void get(K k, Handler<AsyncResult<V>> resultHandler) {
    V v = map.get(k); // null if not found
    resultHandler.handle(new Success<>(v));
  }

  @Override
  public void put(K k, V v, Handler<AsyncResult<Void>> completionHandler) {
    map.put(k, v);
    completionHandler.handle(new Success<>());
  }

  @Override
  public void put(K k, V v, long ttl, Handler<AsyncResult<Void>> completionHandler) {
    map.put(k, v);
    completionHandler.handle(new Success<>());
  }

  @Override
  public void putIfAbsent(K k, V v, Handler<AsyncResult<V>> completionHandler) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void putIfAbsent(K k, V v, long ttl, Handler<AsyncResult<V>> completionHandler) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void remove(K k, Handler<AsyncResult<V>> resultHandler) {
    map.remove(k);
    resultHandler.handle(new Success<>());
  }

  @Override
  public void removeIfPresent(K k, V v, Handler<AsyncResult<Boolean>> resultHandler) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void replace(K k, V v, Handler<AsyncResult<V>> resultHandler) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void replaceIfPresent(K k, V oldValue, V newValue, Handler<AsyncResult<Boolean>> resultHandler) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void clear(Handler<AsyncResult<Void>> resultHandler) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void size(Handler<AsyncResult<Integer>> resultHandler) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

}
