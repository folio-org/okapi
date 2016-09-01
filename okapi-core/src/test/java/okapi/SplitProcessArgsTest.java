package okapi;

import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import org.folio.okapi.util.SplitProcessArgs;
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
