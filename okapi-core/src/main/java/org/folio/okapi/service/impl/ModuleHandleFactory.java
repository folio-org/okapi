package org.folio.okapi.service.impl;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.bean.Ports;
import org.folio.okapi.service.ModuleHandle;

public class ModuleHandleFactory {

  private ModuleHandleFactory() {
    throw new IllegalAccessError("ModuleHandleFactory");
  }

  public static ModuleHandle create(Vertx vertx, LaunchDescriptor desc, String id,
    Ports ports, String moduleHost, int port, JsonObject config) {

    ModuleHandle mh = null;
    if (desc.getDockerImage() == null) {
      mh = new ProcessModuleHandle(vertx, desc, ports, port);
    } else if (desc.getDockerImage() != null) {
      mh = new DockerModuleHandle(vertx, desc, id, ports, moduleHost, port, config);
    }
    return mh;
  }
}
