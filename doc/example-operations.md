# Examples of complete operations in Folio

## Contents

* [Simple operation: view and edit a patron](#simple-operation-view-and-edit-a-patron)
    * [Phase 1: reading the patron record](#phase-1-reading-the-patron-record)
    * [Phase 2: reading the list of patron-types](#phase-2-reading-the-list-of-patron-types)
    * [Phase 3: updating the record](#phase-3-updating-the-record)
* [Complex operation: acquisitions](#complex-operation-acquisitions)


In the meeting of 12 June 2016, we whiteboarded some complete
operations, to see how they would look in Folio. This document walks
through the API calls involved in those operations.

We may create clean diagrams at some point in the future. For now, we
use ugly photos of the whiteboards.

## Simple operation: view and edit a patron

<img src="example-operation-whiteboard.jpeg"
width="400" height="516"
alt="photo of the completed view-and-edit-patron whiteboard"/>

We assume that the client has already performed a search or otherwise
identified patron record that he is interested. Then what happens?

### Phase 1: reading the patron record

* UI issues a request `GET /patrons/23` to Okapi.
* Okapi analyses which registered modules subscribe to that operation:
  The Auth and Patron modules will respond.
* Okapi determines from module metadata what permissions are required
  for the operation: the Patron module requires `viewPatrons`.
* Okapi now invokes the request on each appropriate module in turn: in
  this case, just Auth and Patron. First, authentication:
    * Okapi issues a request `GET /patrons/23` to Auth.
    * Auth determines who the logged-in user is, and what his
      permissions are.
    * If the user's permissions do not include `viewPatrons`, Auth
      returns 403 forbidden, and Okapi breaks off, returning the 403
      code to the client.
* If all is well, the Auth module allows the process to continue, and
  Okapi continues to the Patron module.
    * Okapi issues a request `GET /patrons/23` to Patron.
    * Patron requests the data from its storage module. NOTE: at
      present, we are not sure whether this is a dedicated Patron
      Storage module, or a generic storage module accessed via a
      Patron interface. But that doesn't matter for our present
      purposes.
    * The storage module retrieves patron record 23 and returns it to
      the Patron module.
* At this point, the Patron module, which understands business rules
  in its domain, has an opportunity to modify the record before
  returning it to Okapi. For example, it might decide that
  date-of-birth information is sensitive, and should be removed from
  the record unless the user has the `viewPatronSensitive` permission.
* The Patron module returns the (perhaps modified) record to Okapi.
* Okapi returns the (perhaps modified) record to the UI.
* The UI displays the patron record on the screen.

### Phase 2: reading the list of patron-types

When the UI displays an edit form for patrons, it needs to offer a
dropdown for patron-type (librarian, faculty, student, etc.) so the
user can change the type of the patron. In order to do this, it must
fetch the list of supported types from the Patron module.

This process is essentially the same as that of fetching the patron
record itself, but simpler as there is no need to filter sensitive
information.

To summarise: the UI issues a request `GET /patrons/types` to Okapi;
Okapi checks that the request is authorised; the Patron module
recieves the request and returns the list of types to Okapi, which
returns it to the UI.

### Phase 3: updating the record

XXX to be completed


## Complex operation: acquisitions

<img src="example-operation-whiteboard2.jpeg"
width="400" height="510"
alt="photo of the completed acquisitions whiteboard"/>

XXX to be completed


&nbsp;

&nbsp;

