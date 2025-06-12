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

  /**
   * Create module handle.
   * @param vertx Vert.x handle
   * @param desc launch descriptor
   * @param id module ID
   * @param ports ports to be available
   * @param moduleHost module host override (for Docker)
   * @param port port to be in use for module
   * @param config configuration
   * @return module handle
   */
  public static ModuleHandle create(Vertx vertx, LaunchDescriptor desc, String id,
                                    Ports ports, String moduleHost, int port, JsonObject config) {
    ModuleHandle mh = null;
    if (desc.getDockerImage() == null) {
      mh = new ProcessModuleHandle(vertx, desc, id, ports, port, config);
    } else if (desc.getDockerImage() != null) {
      mh = new DockerModuleHandle(vertx, desc, id, ports, moduleHost, port, config);
    }
    return mh;
  }
}
