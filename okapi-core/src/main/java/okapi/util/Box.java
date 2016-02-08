/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.util;

public class Box<T> {
  private T item;

  public Box(T item) {
    this.item = item;
  }

  public T getItem() {
    return item;
  }

  public void setItem(T item) {
    this.item = item;
  }  
}
