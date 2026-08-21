package org.folio.okapi.bean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.vertx.core.json.JsonObject;
import org.folio.okapi.util.JsonDecoder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EnvEntryTest {

  @ParameterizedTest
  @CsvSource(textBlock = """
      {"env":[{"required": true  }]}, true
      {"env":[{"required": false }]}, false
      {"env":[{"required": null  }]},
      """)
  void required(String moduleDescriptor, Boolean expected) {
    var md = JsonDecoder.decode(moduleDescriptor, ModuleDescriptor.class);
    assertThat(md.getEnv()[0].getRequired(), is(expected));

    var jsonObject = JsonObject.mapFrom(md);
    var env = jsonObject.getJsonArray("env").getJsonObject(0);
    assertThat(env.getBoolean("required"), is(expected));
    if (null == expected) {
      // suppress "required" property if null to be backwards-compatible
      assertThat(env.containsKey("required"), is(false));
    }
  }
}
