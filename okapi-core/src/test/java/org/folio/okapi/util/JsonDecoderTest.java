package org.folio.okapi.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import io.vertx.core.json.DecodeException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JsonDecoderTest {

  @ParameterizedTest
  @CsvSource({
    "'{}', 'line: 1, column: 1]'",
    "'\n\n\n\n\n\n\n\n\n         {}', 'line: 10, column: 10]'",
  })
  void decode(String json, String expectedLocation) {
    DecodeException e = assertThrowsExactly(DecodeException.class,
        () -> JsonDecoder.decode(json, String[].class));
    assertThat(e.getMessage(), containsString("Cannot deserialize value of type `java.lang.String[]`"));
    assertThat(e.getMessage(), containsString(expectedLocation));
    assertThat(e.getCause().getMessage(), containsString("Cannot deserialize value of type `java.lang.String[]`"));
    assertThat(e.getCause().getMessage(), containsString(expectedLocation));
  }
}
