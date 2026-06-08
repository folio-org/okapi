package org.folio.okapi.common;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.vertx.core.buffer.Buffer;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class OkapiStringUtilTest {
  @Test
  void trimTrailingSlashes() {
    Assert.assertEquals(null, OkapiStringUtil.trimTrailingSlashes(null));
    Assert.assertEquals("", OkapiStringUtil.trimTrailingSlashes(""));
    Assert.assertEquals("", OkapiStringUtil.trimTrailingSlashes("/"));
    Assert.assertEquals("", OkapiStringUtil.trimTrailingSlashes("//"));
    Assert.assertEquals("", OkapiStringUtil.trimTrailingSlashes("///"));
    Assert.assertEquals("a", OkapiStringUtil.trimTrailingSlashes("a"));
    Assert.assertEquals("a", OkapiStringUtil.trimTrailingSlashes("a/"));
    Assert.assertEquals("a", OkapiStringUtil.trimTrailingSlashes("a//"));
    Assert.assertEquals("a", OkapiStringUtil.trimTrailingSlashes("a///"));
    Assert.assertEquals("a/b/c", OkapiStringUtil.trimTrailingSlashes("a/b/c"));
    Assert.assertEquals("a/b/c", OkapiStringUtil.trimTrailingSlashes("a/b/c/"));
    Assert.assertEquals("a/b/c", OkapiStringUtil.trimTrailingSlashes("a/b/c//"));
    Assert.assertEquals("a/b/c", OkapiStringUtil.trimTrailingSlashes("a/b/c///"));
    Assert.assertEquals("/a//b///c", OkapiStringUtil.trimTrailingSlashes("/a//b///c"));
    Assert.assertEquals("/a//b///c", OkapiStringUtil.trimTrailingSlashes("/a//b///c/"));
    Assert.assertEquals("/a//b///c", OkapiStringUtil.trimTrailingSlashes("/a//b///c//"));
    Assert.assertEquals("/a//b///c", OkapiStringUtil.trimTrailingSlashes("/a//b///c///"));
    Assert.assertEquals("/abc/x-y-z/123/c", OkapiStringUtil.trimTrailingSlashes("/abc/x-y-z/123/c"));
    Assert.assertEquals("/abc/x-y-z/123/c", OkapiStringUtil.trimTrailingSlashes("/abc/x-y-z/123/c/"));
    Assert.assertEquals("/abc/x-y-z/123/c", OkapiStringUtil.trimTrailingSlashes("/abc/x-y-z/123/c//"));
    Assert.assertEquals("/abc/x-y-z/123/c", OkapiStringUtil.trimTrailingSlashes("/abc/x-y-z/123/c///"));
    Assert.assertEquals("\n\r\t ", OkapiStringUtil.trimTrailingSlashes("\n\r\t /"));
  }

  @Test
  void removeLogCharacters() {
    Assert.assertEquals(null, OkapiStringUtil.removeLogCharacters(null));
    Assert.assertEquals("", OkapiStringUtil.removeLogCharacters(""));
    Assert.assertEquals("x", OkapiStringUtil.removeLogCharacters("x"));
    Assert.assertEquals("___", OkapiStringUtil.removeLogCharacters("\r\n\t"));
    Assert.assertEquals("|", OkapiStringUtil.removeLogCharacters("|"));
  }

  @ParameterizedTest
  @CsvSource(textBlock = """
      3,    ,
      3, abc,     abc
      3, abcd,    a…d
      6, abcdef,  abcdef
      6, abcdefg, abc…fg
      """)
  void trim(int maxLength, String s, String expected) {
    assertThat(OkapiStringUtil.trim(s, maxLength), is(expected));
    var buffer = s == null ? null : Buffer.buffer(s);
    assertThat(OkapiStringUtil.trim(buffer, maxLength), is(expected));
  }

  @ParameterizedTest
  @CsvSource(textBlock = """
                  ,
      abc         , abc
      'a\nb\rc\td', a_b_c_d
      """)
  void sanitizeForLog(String s, String expected) {
    assertThat(OkapiStringUtil.sanitizeForLog(s), is(expected));
    var buffer = s == null ? null : Buffer.buffer(s);
    assertThat(OkapiStringUtil.sanitizeForLog(buffer), is(expected));
  }

  @Test
  void sanitizeForLog() {
    var s = "a".repeat(100) + "b" + "\n".repeat(100);
    var expected = "a".repeat(100) + "…" + "_".repeat(99);
    assertThat(OkapiStringUtil.sanitizeForLog(s), is(expected));
    assertThat(OkapiStringUtil.sanitizeForLog(Buffer.buffer(s)), is(expected));
  }
}
