package org.folio.okapi.bean;

/**
 * Association of a module to a tenant. This encapsulates the id of the module.
 * Each tenant has a list of such associations, listing what modules have been
 * enabled for it.
 *
 */
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.*;


@JsonInclude(Include.NON_NULL)
public class TenantModuleDescriptor {

  private String id; // For practical reasons, the UI folks prefer this to be
  // called 'id'. It is the id of a module.
  private String from;

  enum Action {
    enable, disable, uptodate, suggest, conflict
  }

  private Action action;

  private String message;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getFrom() {
    return from;
  }

  public void setFrom(String id) {
    this.from = id;
  }

  public String getAction() {
    if (action == null) {
      return null;
    } else {
      return action.name();
    }
  }

  public void setAction(String action) {
    this.action = Action.valueOf(action);
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
