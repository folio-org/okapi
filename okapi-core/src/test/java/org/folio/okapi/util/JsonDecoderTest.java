package org.folio.okapi.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import io.vertx.core.json.DecodeException;
import org.junit.jupiter.api.Test;

class JsonDecoderTest {

  @Test
  void decode() {
    DecodeException e = assertThrowsExactly(DecodeException.class,
        () -> JsonDecoder.decode("{}", String[].class));
    assertThat(e.getMessage(), is(
    "Failed to decode:Cannot deserialize value of type `java.lang.String[]` from Object value (token `JsonToken.START_OBJECT`)\n at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 1]"

    ));
    assertThat(e.getCause().getMessage(), startsWith("Cannot deserialize value of type"));
  }

  @Test
  void decodeWithLocation() {
    DecodeException e = assertThrowsExactly(DecodeException.class,
        () -> JsonDecoder.decode("\n\n\n\n\n\n\n\n\n         {}", String[].class));
    assertThat(e.getMessage(), is(
     "Failed to decode:Cannot deserialize value of type `java.lang.String[]` from Object value (token `JsonToken.START_OBJECT`)\n at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 10, column: 10]"
        ));
    assertThat(e.getCause().getMessage(), startsWith("Cannot deserialize value of type"));
  }
}
