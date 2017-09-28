package raml;

import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.core.RamlValidator;
import guru.nidi.ramltester.core.Validation;
import static guru.nidi.ramltester.junit.RamlMatchers.validates;
import org.junit.Assert;
import org.junit.Test;

public class RamlTest {
  @Test
  public void testOkapiRaml() {
    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml");

    // Don't check Validation.DESCRIPTION
    RamlValidator v = api.validator().withChecks(Validation.URI_PARAMETER, Validation.PARAMETER, Validation.EMPTY);

    Assert.assertThat(v.validate(), validates());
  }
}
