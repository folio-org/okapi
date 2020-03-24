package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A way to map arbitrary Json. Used for Docker arguments etc.
 */
public class AnyDescriptor {

  private Map<String, Object> properties = new LinkedHashMap<>();

  @JsonAnyGetter
  public Map<String, Object> properties() {
    return properties;
  }

  @JsonAnySetter
  public void set(String fieldName, Object value) {
    this.properties.put(fieldName, value);
  }
}
