# A modest proposal for error formats in FOLIO

> **NOTE.** This is a non-normative proposal, not at this stage a description of how things are actually done.
> Thu 12 Jan 16:59:27 GMT 2017

In almost all cases, when a server-side module needs to report an error to the client that invoked it, the HTTP status code conveys enough machine-readable information for the client software to decide what do (abort, retry, fail, ignore, etc.) But in a small number of cases, more expressive diagnostics are of use.

## Example

For example, one could imagine a client submitting an edit which fails validation on the server side: then the response could contain a list of validation-failure objects, each containing a fieldName, maybe the associated fieldValue that failed, and an explanation of why it was rejected:

```
400 Bad Request
Content-Type: application/json

{
  "errorCode": "ValidationFailed",
  "message": "Some submitted fields contained invalid values",
  "details": [
    {
      "fieldName": "phone",
      "fieldValue": "01279 504 468",
      "explanation": "value must not contain spaces"
    },
    {
      "fieldName": "email",
      "fieldValue": "demon.co.uk!n4!mirk",
      "explanation": "UUCP-style mail addresses are not supported"
    }
  ]
}
```

A clever UI could use this to annotate its edit page and guide the user.

## Proposal

We suggest that FOLIO modules may return errors to clients in either
of two formats: a simple plain-text message, or a structured JSON object.

* HTTP status is king -- always convey information via that first.
* When the status is 4xx or 5xx, the response body contains additional error information.
* Additional error information may be `text/plain` or `application/json`.
* When it is `application/json`, it must always be an object containing an `errorCode` key whose value is an opaque code taken from a vocabulary that we will establish (possibly namespaced by module).
* The object must also contain a `message` key whose value is a human-readable string like the one that would have been used if the content had been `text/plain`.
* Additional keys within the JSON structure, if any, are dependent on the `errorCode`: documentation of each code includes a specification of what additional fields are included, and their types and meaning.

## Consequences

If this convention is adopted, then any back-end module can easily upgrade a given error report from the basic text/plain form to a superset JSON error object as and when needed.

When receiving an error response, it is the responsibility of the client to check the content-type and be prepared to handle either plain text or JSON. If the client runs into a JSON error that it doesn't know how to handle, then at least it knows to use the `message` element of the object as the error-message. More sophisticated handling can be added on the client side as and when.

It might make sense if there are error codes not covered by HTTP but needed across a variety of modules to provide a set of standard error codes but require that any codes not on the list, use a module-based namespace encoding.

Since we have no immediate need for structured error information, there is little gain _now_ from adopting this approach. But it will be a big win down the line when we decide we need more expressive power in diagnostics. The ugrade path is very painless, as individual errors on the server side can be upgraded as and when; and no such change will require a corresponding UI change, the additional information will just be there for the UI to use when it's ready.

So upgrading the format of an error reported by service doesn't break anything.

## Special case: multiple errors

If for some reason a service wants to report multiple unrelated errors in a single response, it could do so using the error-code `MultipleErrors` and providing a `details` array each of whose elements is an error object like the top-level one. (I am not sure there is really a need for this, but it's worth pointing out that this simple proposal does cover that complex case.)

