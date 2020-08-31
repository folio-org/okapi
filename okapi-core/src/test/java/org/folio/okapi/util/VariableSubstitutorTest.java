package org.folio.okapi.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VariableSubstitutorTest implements WithAssertions {
  @ParameterizedTest
  @CsvSource({
      ",              ,     ,     ",
      "'',            ,     ,     ''",
      "p=%p and c=%c, ,     ,     p= and c=",
      "%,             a,    bbb,  ''",
      "%p,            a,    bbb,  a",
      "%c,            a,    bbb,  bbb",
      "%x,            a,    bbb,  ''",
      "%%,            a,    bbb,  %",
      "%%p %%c %p %c, p=%c, c=%p, %p %c p=%c c=%p",
  })
  void string(String original, String p, String c, String expected) {
    assertThat(VariableSubstitutor.replace(original, p, c)).isEqualTo(expected);
  }

  @Test
  void jsonArray() {
    JsonArray jsonArray = new JsonArray()
        .add("a %p")
        .add(new JsonObject().put("key", "b %c").put("key2", new JsonArray().add("b2 %p")))
        .add("c %c")
        .add(new JsonArray().add("d %p"))
        .add(6)
        .add("e %p");
    VariableSubstitutor.replace(jsonArray, "1234", "8.8.8.8");
    assertThat(jsonArray).isEqualTo(new JsonArray()
        .add("a 1234")
        .add(new JsonObject().put("key", "b 8.8.8.8").put("key2", new JsonArray().add("b2 1234")))
        .add("c 8.8.8.8")
        .add(new JsonArray().add("d 1234"))
        .add(6)
        .add("e 1234"));
  }

  @Test
  void jsonObject() {
    JsonObject jsonObject = new JsonObject()
        .put("a %p", "a %p")
        .put("b %p", new JsonArray().add("b %c").add(new JsonObject().put("key", "b2 %p")))
        .put("c %p", "c %p")
        .put("d %c", new JsonObject().put("key", "b %c"))
        .put("e %c", 6)
        .put("f %p", "f %p");
    VariableSubstitutor.replace(jsonObject, "1234", "8.8.8.8");
    assertThat(jsonObject).isEqualTo(new JsonObject()
        .put("a %p", "a 1234")
        .put("b %p", new JsonArray().add("b 8.8.8.8").add(new JsonObject().put("key", "b2 1234")))
        .put("c %p", "c 1234")
        .put("d %c", new JsonObject().put("key", "b 8.8.8.8"))
        .put("e %c", 6)
        .put("f %p", "f 1234"));
  }
}
