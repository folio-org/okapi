/*
 * Copyright (C) 2016 Index Data
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
package raml;

import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.core.RamlValidator;
import guru.nidi.ramltester.core.Validation;
import static guru.nidi.ramltester.junit.RamlMatchers.validates;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class RamlTest {

  public RamlTest() {
  }

  @BeforeClass
  public static void setUpClass() {
  }

  @AfterClass
  public static void tearDownClass() {
  }

  @Before
  public void setUp() {
  }

  @After
  public void tearDown() {
  }

  @Test
  public void testOkapiRaml() {
    RamlDefinition api = RamlLoaders.fromFile("src/main/raml").load("okapi.raml");

    // Don't check Validation.DESCRIPTION
    RamlValidator v = api.validator().withChecks(Validation.URI_PARAMETER, Validation.PARAMETER, Validation.EMPTY);

    Assert.assertThat(v.validate(), validates());
  }
}
