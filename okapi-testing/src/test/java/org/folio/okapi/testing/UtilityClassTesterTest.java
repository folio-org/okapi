package org.folio.okapi.testing;

import static org.junit.Assert.fail;

import java.lang.reflect.Constructor;

import org.junit.Test;

public class UtilityClassTesterTest {
  @Test
  public void selfTest() {
    UtilityClassTester.assertUtilityClass(UtilityClassTester.class);
  }

  public void assertMessage(Class<?> clazz, String message) {
    try {
      UtilityClassTester.assertUtilityClass(clazz);
    } catch (AssertionError e) {
      if (e.getMessage().contains(message)) {
        return;
      }
      e.printStackTrace();
      fail(clazz.getName() + " throws exception, but it does not contain '"
          + message + "': " + e.getMessage());
    }
    fail(clazz.getName() + " must throw exception containing '" + message + "'");
  }

  @Test
  public void notFinal() {
    assertMessage(NotFinal.class, "class must be final");
  }

  @Test
  public void noConstructor() {
    assertMessage(NoConstructor.class, "constructor");
  }

  @Test
  public void twoConstructors() {
    assertMessage(TwoConstructors.class, "number of constructors");
  }

  @Test
  public void publicConstructor() {
    assertMessage(PublicConstructor.class, "constructor must be private");
  }

  @Test
  public void constructorDoesntThrowException() {
    assertMessage(ConstructorWithoutException.class, "must throw UnsupportedOperationException");
  }

  @Test
  public void constructorThrowsWrongException() {
    assertMessage(ConstructorThrowsWrongException.class, "must throw UnsupportedOperationException");
  }

  @Test(expected = AssertionError.class)
  public void constructorIsAccessible() throws SecurityException, NoSuchMethodException {
    final Constructor<?> constructor = ConstructorIsAccessible.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    UtilityClassTester.assertNonAccessible(constructor);
  }

  @Test
  public void nonStaticMethod() {
    assertMessage(NonStaticMethod.class, "method must be static");
    assertMessage(NonStaticMethod.class, "one()");
  }

  @Test
  public void nonStaticMethod2() {
    assertMessage(NonStaticMethod2.class, "method must be static");
    assertMessage(NonStaticMethod2.class, "six()");
  }

  @Test(expected = InternalError.class)
  public void internalError() {
    UtilityClassTester.assertUtilityClass(Class.class);
  }
}

class NotFinal {
  private NotFinal() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }
};

final class NoConstructor {
}

final class TwoConstructors {
  private TwoConstructors() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }
  private TwoConstructors(String message) {
    throw new UnsupportedOperationException("Cannot instantiate utility class. " + message);
  }
}

final class PublicConstructor {
  public PublicConstructor() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }
}

final class ConstructorWithoutException {
  private ConstructorWithoutException() {
  }
}

final class ConstructorThrowsWrongException {
  private ConstructorThrowsWrongException() {
    throw new RuntimeException("Cannot instantiate utility class.");
  }
}

final class ConstructorIsAccessible {
  private ConstructorIsAccessible() {  // only the test code can invoke setAccessible(true)
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }
}

final class NonStaticMethod {
  private NonStaticMethod() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  public int one() {
    return 1;
  }
}

final class NonStaticMethod2 {
  private NonStaticMethod2() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  static public int four() {
    return 4;
  }

  static public int five() {
    return 5;
  }

  public int six() {
    return 6;
  }
}
