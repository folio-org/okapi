package org.folio.okapi.util;

import io.vertx.core.Vertx;
import org.folio.okapi.bean.LaunchDescriptor;
import org.folio.okapi.bean.Ports;

public class ModuleHandleFactory {

  public static ModuleHandle create(Vertx vertx, LaunchDescriptor desc,
          Ports ports, int port) {
    ModuleHandle mh = null;
    if (desc.getDockerImage() == null) {
      mh = new ProcessModuleHandle(vertx, desc, ports, port);
    } else if (desc.getDockerImage() != null) {
      mh = new DockerModuleHandle(vertx, desc, ports, port);
    }
    return mh;
  }
}
