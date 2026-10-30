package org.folio.okapi.bean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.vertx.core.json.JsonObject;
import org.folio.okapi.util.JsonDecoder;
import org.junit.jupiter.api.Test;

class EventsTest {

  @Test
  void producesAndConsumesEvents() {
    var json = """
        {"id":"mod-a-1.0.0",
         "producesEvents":[{"event":"mod-a.created.v1"}],
         "consumesEvents":[{"event":"mod-b.updated.v1","optional":true},{"event":"mod-c.x.v1"}]}
        """;
    var md = JsonDecoder.decode(json, ModuleDescriptor.class);
    assertThat(md.getProducesEvents()[0].getEvent(), is("mod-a.created.v1"));
    assertThat(md.getConsumesEvents()[0].getOptional(), is(true));
    assertThat(md.getConsumesEvents()[1].getOptional(), is(nullValue()));

    var out = JsonObject.mapFrom(md);
    assertThat(out.getJsonArray("producesEvents").getJsonObject(0).getString("event"),
        is("mod-a.created.v1"));
    assertThat(out.getJsonArray("consumesEvents").getJsonObject(1).containsKey("optional"),
        is(false));
  }

  @Test
  void absentEventsAreNotSerialized() {
    var out = JsonObject.mapFrom(new ModuleDescriptor("mod-a-1.0.0"));
    assertThat(out.containsKey("producesEvents"), is(false));
    assertThat(out.containsKey("consumesEvents"), is(false));
  }
}
