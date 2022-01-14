package org.folio.okapi.util;

import io.vertx.core.MultiMap;
import io.vertx.core.json.DecodeException;
import java.util.Optional;
import org.folio.okapi.common.ModuleId;

public class ModuleVersionFilter {
  Boolean npmSnapshot;
  Boolean preRelease;

  public ModuleVersionFilter() {
    this(null, null);
  }

  ModuleVersionFilter(Boolean preRelease, Boolean npmSnapshot) {
    this.preRelease = preRelease;
    this.npmSnapshot = npmSnapshot;
  }

  /**
   * Construct from HTTP query parameters "npmSnapshot", "preRelease".
   * @param params HTTP parameters
   */
  public ModuleVersionFilter(MultiMap params) {
    Optional<Boolean> o = getParamVersionFilter(params, "npmSnapshot");
    npmSnapshot = o.isEmpty() ? null : o.get();

    o = getParamVersionFilter(params, "preRelease");
    preRelease = o.isEmpty() ? null : o.get();
  }

  /**
   * Check if module ID matches preRelease/npmSnapshot spec.
   * @param idThis module ID
   * @return true if module should be included; false it should be filtered away.
   */
  public boolean matchesModule(ModuleId idThis) {
    if (idThis.hasPreRelease()) {
      return preRelease == null || preRelease;
    } else if (idThis.hasNpmSnapshot()) {
      return npmSnapshot == null || npmSnapshot;
    } else {
      return ((npmSnapshot == null || !npmSnapshot)
          && (preRelease == null || !preRelease));
    }
  }

  /**
   * Lookup boolean preRelease/npmSnapshot parameter in HTTP request.
   * @param params HTTP server request parameters
   * @param name name of query parameter
   * @return returns false for "false", true for "only", and
   *     Optional.empty() for "true" and null/undefined
   * @throws DecodeException on invalid value
   */
  static Optional<Boolean> getParamVersionFilter(MultiMap params, String name) {
    String v = params.get(name);
    if (v == null || "true".equals(v)) {
      return Optional.empty();
    } else if ("false".equals(v)) {
      return Optional.of(false);
    } else if ("only".equals(v)) {
      return Optional.of(true);
    }
    throw new DecodeException("Expected \"true\", \"false\", \"only\" or undefined/null "
        + "for parameter " + name + ", but got: " + v);
  }


}
