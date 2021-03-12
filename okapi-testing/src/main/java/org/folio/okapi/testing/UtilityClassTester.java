package org.folio.okapi.testing;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Unit test tool for utility classes.<p />
 * Also helps to get 100% code coverage because it invokes the private constructor.
 */
public final class UtilityClassTester {
  private UtilityClassTester() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  /**
   * Assert that the private constructor throws an UnsupportedOperationException.
   * @param constructor  Constructor of a utility class
   */
  @SuppressWarnings({
      "squid:S1166",  /* suppress "Either log or rethrow this exception" */
      "squid:S3011",  /* suppress "Changing accessibility is security sensitive" and
                         suppress "Make sure that this accessibility update is safe here."
                         This is save because
                         - it runs during unit tests only, and
                         - it invokes the constructor only, no write access, no i/o.
                         There is no other way to test whether a private constructor
                         throws an Exception.
                       */
  })
  private static void assertInvocationException(Constructor<?> constructor) {
    try {
      constructor.setAccessible(true);

      // This invocation gives 100% code coverage for the private constructor and
      // also checks that it throws the required exception.
      constructor.newInstance();
    } catch (Exception e) {
      if (e.getCause() instanceof UnsupportedOperationException) {
        return;   // this is the required exception
      }
    }
    throw new AssertionError(
        "Private constructor of utiliy class must throw UnsupportedOperationException "
        + "to fail unintended invocation via reflection.");
  }

  static void assertNonAccessible(Constructor<?> constructor) {
    assertTrue("constructor must be non-accessible", ! constructor.isAccessible());
  }

  /**
   * Assert that the clazz has these utility class properties:
   * Class is final, has only one constructor that is private and
   * throws UnsupportedOperationException when invoked, and all methods are static.
   * @param clazz  utility class to check
   */
  public static void assertUtilityClass(final Class<?> clazz) {
    try {
      assertTrue("class must be final", Modifier.isFinal(clazz.getModifiers()));
      assertTrue("number of constructors is 1", clazz.getDeclaredConstructors().length == 1);
      final Constructor<?> constructor = clazz.getDeclaredConstructor();
      assertTrue("constructor must be private", Modifier.isPrivate(constructor.getModifiers()));
      assertNonAccessible(constructor);
      assertInvocationException(constructor);
      for (final Method method : clazz.getMethods()) {
        if (method.getDeclaringClass().equals(clazz)) {
          assertTrue("method must be static - " + method, Modifier.isStatic(method.getModifiers()));
        }
      }
    } catch (Exception e) {
      throw new InternalError(e);
    }
  }

  // Avoid using org.junit.Assert because JUnit5 does not have it.
  private static void assertTrue(String message, boolean actual) {
    if (! actual) {
      throw new AssertionError(message);
    }
  }
}
