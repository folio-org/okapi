package org.folio.okapi.common;

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
