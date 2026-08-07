# Error responses in FOLIO

APIs that supply JSON in "success" (2xx) responses with a content-type header of "application/json" must likewise supply JSON in "error" (4xx, 5xx) responses with a content-type header of "application/json".

The response object must contain either a top-level property `message` or a top-level property `errors` that contains an array of objects with `message` properties. That is, a response object must be shaped like this:
```
{ message: "", ...}
```
or like this:
```
{ errors: [{ message: "", ...}, ...]}
```
These are the only required properties. Additional properties at any level are optional.
