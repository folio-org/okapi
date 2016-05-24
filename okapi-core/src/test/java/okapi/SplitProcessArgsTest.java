/*
 * Copyright (C) 2015 Index Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okapi;

import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import okapi.util.SplitProcessArgs;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class SplitProcessArgsTest {

  @Test
  public void test() {
    {
      List<String> s = SplitProcessArgs.split("a");
      assertEquals(1, s.size());
      assertEquals("a", s.get(0));
    }
    {
      List<String> s = SplitProcessArgs.split(" a");
      assertEquals(1, s.size());
      assertEquals("a", s.get(0));
    }
    {
      List<String> s = SplitProcessArgs.split("  a");
      assertEquals(1, s.size());
      assertEquals("a", s.get(0));
    }
    {
      List<String> s = SplitProcessArgs.split("a ");
      assertEquals(1, s.size());
      assertEquals("a", s.get(0));
    }
    {
      List<String> s = SplitProcessArgs.split(" a  ");
      assertEquals(1, s.size());
      assertEquals("a", s.get(0));
    }
    {
      List<String> s = SplitProcessArgs.split(" a  bc ");
      assertEquals(2, s.size());
      assertEquals("a", s.get(0));
      assertEquals("bc", s.get(1));
    }
    {
      List<String> s = SplitProcessArgs.split(" \"a \" bc ");
      assertEquals(2, s.size());
      assertEquals("a ", s.get(0));
      assertEquals("bc", s.get(1));
    }
    {
      List<String> s = SplitProcessArgs.split("\" \" a");
      assertEquals(2, s.size());
      assertEquals(" ", s.get(0));
      assertEquals("a", s.get(1));
    }
    {
      List<String> s = SplitProcessArgs.split(" \" \"a");
      assertEquals(2, s.size());
      assertEquals(" ", s.get(0));
      assertEquals("a", s.get(1));
    }
  }
}
