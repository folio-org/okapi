package org.folio.okapi.bean;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterfaceDescriptorTest {
  @Test
  void construct() {
    InterfaceDescriptor d = new InterfaceDescriptor("int", "1.2.3");
    assertThat(d.getId()).isEqualTo("int");
    assertThat(d.getVersion()).isEqualTo("1.2.3");
    assertThat(d.getScope()).isNull();
    assertThat(d.getScopeArray()).isEmpty();
    assertThat(d.getHandlers()).isNull();
    assertThat(d.isRegularHandler()).isTrue();
    assertThat(d.getInterfaceType()).isNull();
    assertThat(d.isType("foo")).isFalse();
  }

  @Test
  void compare() {
    InterfaceDescriptor int123 = new InterfaceDescriptor("int", "1.2.3");
    InterfaceDescriptor int12 = new InterfaceDescriptor("int", "1.2");
    InterfaceDescriptor int13 = new InterfaceDescriptor("int", "1.3");
    InterfaceDescriptor int21 = new InterfaceDescriptor("int", "2.1");
    InterfaceDescriptor d12 = new InterfaceDescriptor("d", "1.2");
    assertThat(d12.compare(int12)).isEqualTo(-4);
    assertThat(int12.compare(d12)).isEqualTo(4);
    assertThat(int123.compare(int123)).isZero();
    assertThat(int123.compare(int12)).isEqualTo(1);
    assertThat(int12.compare(int123)).isEqualTo(-1);
    assertThat(int13.compare(int12)).isEqualTo(2);
    assertThat(int12.compare(int13)).isEqualTo(-2);
    assertThat(int12.compare(int21)).isEqualTo(-3);
    assertThat(int21.compare(int12)).isEqualTo(3);
  }

  @Test
  void isCompatible() {
    InterfaceDescriptor req = new InterfaceDescriptor("int", "1.2.3 2.0");
    InterfaceDescriptor int12 = new InterfaceDescriptor("int", "1.2");
    InterfaceDescriptor int13 = new InterfaceDescriptor("int", "1.3");
    InterfaceDescriptor int20 = new InterfaceDescriptor("int", "2.0");
    InterfaceDescriptor int21 = new InterfaceDescriptor("int", "2.1");
    InterfaceDescriptor int30 = new InterfaceDescriptor("int", "3.0");
    InterfaceDescriptor other20 = new InterfaceDescriptor("other", "2.0");

    assertThat(int12.isCompatible(req)).isFalse();
    assertThat(int13.isCompatible(req)).isTrue();
    assertThat(int20.isCompatible(req)).isTrue();
    assertThat(int21.isCompatible(req)).isTrue();
    assertThat(int30.isCompatible(req)).isFalse();
    assertThat(other20.isCompatible(req)).isFalse();
  }
}
