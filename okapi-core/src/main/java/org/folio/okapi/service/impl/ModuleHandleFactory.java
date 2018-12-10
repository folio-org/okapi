package org.folio.okapi.service.impl;

import io.vertx.core.Vertx;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.bean.Ports;
import org.folio.okapi.service.ModuleHandle;

public class ModuleHandleFactory {

  private ModuleHandleFactory() {
    throw new IllegalAccessError("ModuleHandleFactory");
  }

  public static ModuleHandle create(Vertx vertx, LaunchDescriptor desc, String id,
    Ports ports, int port) {

    ModuleHandle mh = null;
    if (desc.getDockerImage() == null) {
      mh = new ProcessModuleHandle(vertx, desc, ports, port);
    } else if (desc.getDockerImage() != null) {
      mh = new DockerModuleHandle(vertx, desc, id, ports, port);
    }
    return mh;
  }
}
