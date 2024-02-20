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
    assertThat(e.getMessage(), is("Expected `[` but found `{` when trying to deserialize "
        + "an array of java.lang.String at line: 1, column: 1"));
    assertThat(e.getCause().getMessage(), startsWith("Failed to decode:Cannot deserialize"));
  }

  @Test
  void decodeWithLocation() {
    DecodeException e = assertThrowsExactly(DecodeException.class,
        () -> JsonDecoder.decode("\n\n\n\n\n\n\n\n\n         {}", String[].class));
    assertThat(e.getMessage(), is("Expected `[` but found `{` when trying to deserialize "
        + "an array of java.lang.String at line: 10, column: 10"));
    assertThat(e.getCause().getMessage(), startsWith("Failed to decode:Cannot deserialize"));
  }
}
