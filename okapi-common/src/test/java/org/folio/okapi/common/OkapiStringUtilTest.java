package org.folio.okapi.common;

import org.junit.Test;
import org.junit.Assert;

public class OkapiStringUtilTest {
  @Test
  public void trimTrailingSlashes() {
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
  public void removeLogCharacters() {
    Assert.assertEquals(null, OkapiStringUtil.removeLogCharacters(null));
    Assert.assertEquals("", OkapiStringUtil.removeLogCharacters(""));
    Assert.assertEquals("x", OkapiStringUtil.removeLogCharacters("x"));
    Assert.assertEquals("___", OkapiStringUtil.removeLogCharacters("\r\n\t"));
    Assert.assertEquals("|", OkapiStringUtil.removeLogCharacters("|"));
  }

}
