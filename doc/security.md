# Okapi Security Model


The Okapi security model consists of two parts: authentication and authorization.
Authentication is responsible for assuring that our user is who they claim to be.
Authorization is responsible for checking that they are allowed do that action.
Related to authorization, we have a whole lot of permission bits, and a system
to manage them. Okapi does not really care if those are handled in a separate
module, or as part of the authorization module. The permissions can come from
two sources: Either they are granted for the user, or they are granted for a module.

As far as Okapi sees the permissions, they are simple strings like "patron.read",
or "patron.read.sensitive". Those strings have no meaning for Okapi, but we will
have to develop some naming guidelines. On one hand, the modules should define
their permissions on a very granular level, to allow fine-grained control. But
on the other hand, the management of these should be kept simple. This can be
done by having the permissions module expand user roles into grouped permissions,
and those into fine-grained permissions. For example, a 'sysadmin' role might expand
into a list that contains 'patron.admin', which again could expand into a list
that contains 'patron.read', 'patron.update', and 'patron.create'. All that
should happen in the permissions module.

The Okapi source tree contains a small module `okapi-test-auth` that does both parts,
in a minimal dummy fashion, suitable for testing Okapi itself. It is not intended
to be a real security module at all. In real life, the authentication and
authorization modules will likely be implemented as multiple separate modules.

## Simplified Overview

Skipping all messy details, this is more or less what happens when a user wants
to make use of Okapi:

* The first thing the user does is to point their browser to a login screen.
* They enter their credentials.
* The authorization module verifies these.
* It issues a token, and returns that to the user.
* The user passes this token in all requests to Okapi.
* Okapi calls the authorization module to verify that we have a valid user, and
that this user has permission to make the request.
* The modules can make further calls to other modules, via Okapi. Again they
pass the token on, and Okapi passes it to the authorization module to validate
it. Occasionally Okapi can pass on an improved token, if the module has special
permissions.


## Authentication

Obviously we will need different authentication mechanisms, from SAML, OAuth,
and LDAP, to regular usernames and passwords, down to IP authentication, and
even some pseudo-authentication that just says that this is an unidentified
member of the general public.

There should usually be (at least) one authentication module enabled for each
tenant. The user's first request should be to /login, which gets routed to the
right authentication module. It will get some parameters, for example tenant,
username, and password. It will verify these, talking to
what ever backend is proper. When it is satisfied, it will get a JWT token
by invoking the authorization module, which is the module really concerned
with these tokens. In any case, a token is returned to the client. The token
will always contain the userId, and the tenant where the user belongs, but
may contain many other things too. It is cryptographically signed, so its
contents can not be modified without detection.

The module may also receive some permission data from its back end, and issue
an update request to the authorization module (or permissions, if that is a
separate module).

The client will pass this token in all the requests it makes, and the authorization
module will verify it every time.

Unfortunately, there is a bit of a chicken-and-egg problem here. The authentication
module needs to make requests to other modules, for example to look up the user
in the database, or to one that talks to an LDAP server. In order to do that, it
needs to pass on the JWT that it is just about to create. The solution is that it is
allowed to ask the authorization module to produce a temporary JWT for an
unidentified user, where only the tenant is specified. In order to do this
securely, it will have to use a shared secret API key that only the authentication
and authorization modules know.

## Authorization
This is where Okapi gets a bit more involved, and where things get technical.

When a request comes in to Okapi (excluding the /login and other requests that
are open for anyone), Okapi looks at the tenant, and figures out the pipeline
of modules it is going to call. This should include the authorization module at
(or near) the beginning.

At the same time, Okapi looks at the permission bits for all the modules involved,
from their moduleDescriptors. There are three kinds of permissions:

* permissionsRequired. These are strictly necessary to call the module.
* permissionsDesired. These are not necessary, but if present, the module may
do some extra operations (for example, show sensitive data about a patron).
* modulePermissions. Unlike the two others, these are permissions granted to a
module, which it can make use of, when making further calls to other modules.

As noted before, Okapi does not check any permissions. It just collects the sum
of `permissionsRequired` and `permissionsDesired`, and passes those to the
authorization module for checking. It also collects the `modulePermissions` for
each module in the pipeline, and passes these to the authorization module.

The authorization module first checks that we have a valid token, and that its
signature still matches its contents. Then it extracts the user and tenant IDs
from the token, and looks up the permissions granted to this user. It also checks
if there are module permissions in the token, and if so, adds those to the list
of user permissions. Next it checks that all `permissionsRequired` are indeed present
in the list of permissions. If any one is missing, it fails the whole request.
The authorization module also walks through the `permissionsDesired`, and checks
which of those are present. It reports those in a special header, so the following
modules can look at them, and decide to modify their behavior.

Finally, the authorization module looks at the `modulePermissions` it received.
For each module listed there, it creates a new JWT that contains all the same
stuff as the original, plus the permissions granted for that module. It signs
the JWT, and returns them all in the X-Okapi-Module-Tokens header to Okapi. If
the original token contained some module permissions, the authorization module
also generates a new token without those, to be used for all the modules that
do not get special permissions. This will be more or less identical to the user's
original token.

Okapi checks whether it received any module tokens, and stores these in the pipeline
it is executing, so it can pass such a special token to those modules that have
special permissions. The rest of the modules get a clean token.

The following modules do not know, nor care, if they receive the original token
from the UI, or a token specially crafted for them. They just pass it on in any
request to other modules, and Okapi will verify that.

As mentioned above, the authorization module can also be invoked without any JWT,
which is a special case the authentication module needs. In that case it will
require a secret API key to be passed on with the request, and will provide a
simple JWT for a given tenant, but without any username.

## Examples of data flow

Some practical examples of how the data flows between the various parts of the
system. We start with the easier cases, and work our way up to the most complex:
logging in. 

### Simple request: Date
Assume that the UI wishes to display the current date. Our user has already
logged in to the system, so we know the userId ("joe") and tenantId ("ourlib"). 
We also have a JWT token for authorization. Its internal details are not really
relevant, in these examples I assume it looks like "xx-joe-ourlib-xx". The UI
knows the address of the Okapi server to talk to. In these examples we use
http://folio.org/okapi. We know that a calendar module ("cal") has been enabled
for this tenant, and it has a service endpoint /date to return the current date.
This service is open to everyone.

Summary:
1.1: The UI makes a request
1.3: Okapi calls the auth module, which verifies the JWT
1.5: Okapi calls the cal module to get the date

And now for the details:

1.1: The UI makes a request to Okapi with some extra headers:
 * GET http://folio.org/okapi/date
 * X-Okapi-Tenant: ourlib
 * X-Okapi-Token: xx-joe-ourlib-xx

1.2: Okapi receives the request:
 * It checks that we know about tenant "ourlib".
 * It builds a list of modules that serve /date, and that are enabled for "ourlib".
 * This list will typically consist of two modules: first "auth", and then the
actual "cal" module.
 * Okapi notes that the request contains an X-Okapi-Token. It saves the token 
for future use. 
 * Okapi checks what permissions are required and desired for these services. In 
this example there are none. It creates two extra headers: 
X-Okapi-Permissions-Required and X-Okapi-Permissions-Desired, both with an empty 
list.
 * Okapi checks if any permissions have been granted to any of the modules. Again,
this is not the case. So it creates an X-Okapi-Module-Permissions without any
content.
 * Okapi checks where the auth module is running (which node, on which port).
 * Okapi passes the request to the auth module with these extra headers:
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-joe-ourlib-xx
     * X-Okapi-Permissions-Required: []
     * X-Okapi-Permissions-Desired: []
     * X-Okapi-Module-Permissions: {}

1.3: The auth module receives the request:
 * It checks that we have an X-Okapi-Token header.
 * Verifies the signature of the token.
 * Extracts the tenantId and userId from it.
 * Verifies that the tenantId matches the one in X-Okapi-Tenant header.
 * Sees that no permissions are required, and no module permissions, so it
creates an empty X-Okapi-Permissions header.
 * Sees that there were no special module permissions involved, so it creates
an empty X-Okapi-Module-Tokens header.
 * It sends an OK response to Okapi with the new headers.
     * X-Okapi-Permissions: []
     * X-Okapi-Module-Tokens: {}
If any of the steps would have failed, the auth module would have returned an 
error response. Okapi would then stop processing its module pipeline, and 
return that error response to the UI.

1.4: Okapi received the OK response from the auth module:
 * It notes that it received an X-Okapi-Module-Tokens header. This indicates that
authorization is done, so it can remove the X-Okapi-Permissions-Required and
-Desired headers from following requests.
 * It notes that there were no tokens in that header, so it doesn't do anything
special.
 * It sees that the next module to be called is the cal module.
 * It checks where the cal module is running.
 * Send the request to the cal module with these headers:
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-joe-ourlib-xx
     * X-Okapi-Permissions: []

1.5: The cal module receives the request:
 * It looks up the current date, and formats it in a suitable way.
 * It returns that to Okapi in a OK response. There are no special headers.

1.6: Okapi receives the OK response:
 * It sees that this is the last module in the list.
 * So it returns the response to the UI.

1.7: The UI displays the date on the screen.

This may seem overly complex, but the next example(s) should show why many of
the steps are indeed necessary.

Note that there is no special reason why the request should come from the UI. It
can come from anything that can initiate a request, a cron-like job that wakes up
in the middle of the night, or a book-returning machine that saw a returned item.
In any case, this thing must have a JWT.

### More complex request: MOTD

The Message of the Day (MOTD) is a more complex request. For this example we
decide that we can have two kind of messages, one for patrons and one for staff
only. Staff members will see the staff message, if one has been posted, or the
patron one if not. Regular patrons may see the patron message only. Unidentified
members of the general public are not allowed see anything.

The message is kept in the database. The motd module needs to make a request to
the database module to retrieve it. In order to do that, it needs to have a
permission to read the motd from the database.

Summary:
 2.1: The UI makes a request
 2.3: The auth module verifies the JWT, and looks up the permissions of the user
 2.7: The request for the permissions goes through Okapi, and to the perm module, which returns the permissions
 2.9: the auth module verifies the users permissions
 2.9: It also creates a special JWT for the motd module, with its permissions
 2.11: The motd module makes a request to the db module, with the special JWT
 2.12: The auth module verifies that JWT, sees and checks the permissions
 2.15: The db module looks up the message
 2.16: The message is passed back all the way to the UI

2.1: The UI makes a request to Okapi with some extra headers:
 * GET http://folio.org/okapi/motd
 * X-Okapi-Tenant: ourlib
 * X-Okapi-Token: xx-joe-ourlib-xx

2.2: Okapi receives the request:
 * It checks that we know about tenant "ourlib".
 * It builds a list of modules that serve /motd, and that are enabled for "ourlib".
 * This list will typically consist of two modules: first "auth", and then the
actual "motd" module.
 * Okapi notes that the request contains an X-Okapi-Token. It saves the token
for future use, as before.
 * Okapi looks at the moduleDescriptors for these modules and checks what
permissions are required and desired. The auth module has no need for any
permissions. The motd module requires the "motd.show" permission, and desires
the "motd.staff" permission. It puts these values in the
X-Okapi-Permissions-Required and -Desired headers.
 * Okapi checks the moduleDescriptors to see if any permissions have been
granted to any of the modules. The motd module has "db.motd.read" permission.
Okapi puts that in the X-Okapi-Module-Permissions header.
 * Okapi checks where the auth module is running (which node, on which port).
 * Okapi passes the request to the auth module with these extra headers:
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-joe-ourlib-xx
     * X-Okapi-Permissions-Required: ["motd.show"]
     * X-Okapi-Permissions-Desired: ["motd.staff"]
     * X-Okapi-Module-Permissions: { "motd": "db.motd.read" }

2.3: The auth module receives the request:
 * It checks that we have an X-Okapi-Token header.
 * Verifies the signature of the token.
 * Extracts the tenant and userId from it.
 * Verifies that the tenantId matches the one in X-Okapi-Tenant header, as before.
 * It sees that we require and desire some permissions. Since it does not have
joe's permissions cached, it needs to get them from the permission module.
 * It makes a request to /permissions/joe. Luckily for us, the permissions module
itself does not require any special permissions for the read operation, at least
not when a user is looking up their own permissions.
* It sends the request to Okapi:
     * GET http://folio.org/okapi/permissions/joe
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-joe-ourlib-xx

2.4: Okapi receives the request, and processes it like in 1.2:
 * It checks that we know about tenant "ourlib".
 * It builds a list of modules that serve /permissions/:id, and that are
enabled for "ourlib".
 * This list will typically consist of two modules: first "auth", and then the
actual "perm" module.
 * Okapi notes that the request contains an X-Okapi-Token. It saves the token
for future use.
 * Okapi checks what permissions are required and desired for these services. It
is important that the permission module does not require permissions for a simple
lookup, or we fall into endless recursion.
 * There is no reason why the permission module itself could not have module-level
permissions, for example "db.permissions.read". For now we can assume that there
are not.
 * Okapi checks where the perm module is running (which node, on which port).
 * Okapi passes the request to the auth module with these extra headers:
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-joe-ourlib-xx
     * X-Okapi-Permissions-Required: []
     * X-Okapi-Permissions-Desired: []
     * X-Okapi-Module-Permissions: {}

2.5: The auth module verifies the JWT as in 1.3:
 * It sends an OK response to Okapi with the new headers:
     * X-Okapi-Permissions: []
     * X-Okapi-Module-Tokens: {}

2.6: Okapi receives the OK response without any special permission things, just
like in 1.4:
 * It sees that the next module to be called is the perm module.
 * It checks where the perm module is running.
 * It sends the request to the perm module with these headers:
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-joe-ourlib-xx
     * X-Okapi-Permissions: []

2.7: The permission module gets the request:
 * It finds the permissions for "joe".
 * It returns an OK response with a list of permissions like
["motd.show", "motd.staff", "what.ever.else"].

2.8: Okapi receives the OK response:
 * It sees there are no more modules in the list.
 * So it returns the response to the auth module.

2.9: The auth module receives the response from the perm module and continues its
processing:
 * It sees that we have an X-Okapi-Permissions-Required header with "motd.show".
 * It sees that the JWT it received has no special module permissions, so it uses
joe's permission list as it is.
 * It may want to cache joe's permission list for the next time it will be needed.
 * It checks that joe's permission list contains that permission. If not, it
returns an error response immediately, and Okapi returns that to the UI without
further processing.
 * It sees that we have an X-Okapi-Permissions-Desired header with "motd.staff",
so it adds an X-Okapi-Permissions header with [ "motd.staff" ].
 * Next the auth module sees that we have X-Okapi-Module-Permissions for "motd".
 * So it creates a new JWT with all the contents of the original JWT, plus a
new field "modulePermissions" with value "motd". Let's say that token will
look like xx-joe-ourlib-motd-xx
 * It puts this new JWT in the X-Okapi-Module-Tokens header.
 * Finally it returns an OK response with:
     * X-Okapi-Permissions: ["motd.staff"]
     * X-Okapi-Module-Tokens: {"motd" : "xx-joe-ourlib-motd-xx" }

Note that the module has no need to return the "motd.show" permission, since it
was strictly required. If joe had not had this permission, the auth module would
have failed, and the modt module would never be called.

2.10: Okapi received the OK response from the auth module:
 * It notes that it received an X-Okapi-Module-Tokens header. This indicates that
authorization is done, so it can remove the X-Okapi-Permissions-Required and
-Desired headers from following requests, as before.
 * It notes that there is a module token for "motd". It stores that token in its
list of modules to be called, at the motd module.
 * It sees that the next module to be called is the motd module
 * It checks where the motd module is running
 * Sees that we have a module token for motd.
 * Send the request to the motd module with these headers:
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-joe-ourlib-motd-xx
     * X-Okapi-Permissions: ["motd.staff"]

2.11: The motd module receives the request:
 * It sees that X-Okapi-Permissions contains the "motd.staff" permission. So
it decides to retrieve the staff-only motd from the database.
 * It sends a GET request to http://folio.org/okapi/db/motd/staff with headers:
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-joe-ourlib-motd-xx

2.12: Okapi receives the request, and processes it as before:
 * It checks that we know about tenant "ourlib".
 * It builds a list of modules that serve /db/motd/, and that are
enabled for "ourlib".
 * This list will typically consist of two modules: first "auth", and then the
actual "db" module.
 * Okapi notes that the request contains an X-Okapi-Token. It saves the token
for future use.
 * Okapi checks what permissions are required and desired for these services. The
db module requires "db.motd.read"
 * For simplicity, we assume that the db module itself does not have any specific
permissions.
 * Okapi checks where the auth module is running (which node, on which port).
 * Okapi passes the request to the auth module with these extra headers:
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-joe-ourlib-motd-xx
     * X-Okapi-Permissions-Required: [ "db.motd.read" ]
     * X-Okapi-Permissions-Desired: []
     * X-Okapi-Module-Permissions: {}

2.13: The auth module receives the request:
 * It checks that we have an X-Okapi-Token header.
 * Verifies the signature of the token.
 * Extracts the tenantId and userId from it.
 * Verifies that the tenantId matches the one in X-Okapi-Tenant header, as before.
 * It sees that we require and desire some permissions. Now we do have joe's
permissions in the cache, so it uses that list.
 * It sees that the JWT has special modulePermissions in it. It adds the "db.motd.read"
to the permission list.
 * It sees that we have an X-Okapi-Permissions-Required header with "db.motd.read"
in it. Checks the permission list, and finds that the permission is there.
 * Does not see any desired permissions.
 * Does not see any module permissions for the db module.
 * Since the JWT had special module permissions in it, the auth module needs to
create a new JWT without them, for further calls to different modules. It takes
the modulePermissions out of the JWT, and signs that. The result is the same as
the original JWT, "xx-joe-ourlib-xx". It returns that in the X-Okapi-Module-Tokens
as a token for the special module "_".
 * Returns an OK response to Okapi with headers:
     * X-Okapi-Permissions: []
     * X-Okapi-Module-Tokens: { "_" : "xx-joe-ourlib-xx" }

2.14: Okapi receives the OK response from auth:
 * Sees that there is a special module token for "_". Copies that token over to
all modules that it intends to call, overwriting the token that had motd's special
permissions. In that way, those permissions do not leak to other modules.
 * Sees that there are no module tokens.
 * Sees that the next module is the db module.
 * Sends a request to the db module with:
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-joe-ourlib-xx

2.15: The db module receives the request:
 * It looks up the motd for staff.
 * Returns it in a OK response.

2.16: Okapi receives the OK response:
 * As there are no more modules in the list, it returns the OK response.

2.17: The motd module receives the OK response from the db module:
 * It builds its own OK response, with the message it got from the DB.
 * Returns that to Okapi.

2.18) Okapi receives the OK response:
 * Since the motd module was the last one in the list, it returns the response
to its caller.

2.19: The UI displays the message of the day.

### Login

NOTE: I have just come up with a simplified way to do the login. What is described
below is not exactly what has been implemented, or described elsewhere. (Remove
this note when things are up to date!)

Here is a quick outline of what happens when a user wants to log in to the system.
The tediously detailed handshakes come later.

* The UI sends a login request to Okapi. There is no JWT yet.
* Okapi passes the request to the auth module.
* The auth module sees we do not have a JWT, so it creates one.
* It also creates a JWT for the authentication module, with module-level permissions
* Okapi calls the authentication module with its JWT that gives it permission to
make other requests
* The authentication module calls the authorization module to generate a real JWT
for the user.
* This JWT gets returned to the UI, and used in future calls.

Note that there are no API keys, shared secrets, or anything else mysterious.

In this example the login is done with a simple username and password, but other
modules can check against a LDAP server, or any other authentication method.


3.1: The UI starts up, and somehow displays a login screen to the user.
 * The user enters some credentials, in this example username and password
 * Clicks on a submit button

3.2: The UI sends a request to Okapi:
 * POST http://folio.org/okapi/login
 * X-Okapi-Tenant: ourlib
 * Of course we do not have any JWT yet
Note that the URL and the tenantId must be hard coded in the UI somehow.

3.3: Okapi receives the request.
 * It checks that we know about tenant "ourlib".
 * It builds a list of modules that serve /login, and that are enabled for "ourlib".
 * This list will typically consist of two modules: first "auth", and then the
actual "login" module.
 * Okapi notes that the request contains no X-Okapi-Token header.
 * Okapi looks at the moduleDescriptors for these modules and checks what
permissions are required and desired. Clearly we can not require any permissions
as this stage, the login service must be open.
 * Okapi checks the moduleDescriptors to see if any permissions have been
granted to any of the modules. The login module has at least the permissions
 "db.user.read.passwd" and "auth.newtoken", perhaps some more.
Okapi puts that in the X-Okapi-Module-Permissions header.
 * Okapi checks where the auth module is running (which node, on which port).
 * Okapi passes the request to the auth module with these extra headers:
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Permissions-Required: []
     * X-Okapi-Permissions-Desired: []
     * X-Okapi-Module-Permissions: { "login": [ "auth.newtoken", "db.user.read.passwd" }

3.4: The auth module receives the request:
 * It sees that we do not have any X-Okapi-Token
 * So it creates one, with the tenantId from X-Okapi-Tenant, but without any
userId. Let's call that "xx-unknown-ourlib-xx". It returns this as the general
token, under module "_".
 * It sees that we do not require any permissions
 * It sees that we have module permissions.
 * It creates a new JWT for the login module listed in X-Okapi-Module-Permissions,
"xx-unknown-ourlib-login-xx" with the "auth.newtoken" and "db.user.read.passwd"
permissions included in it.
 * It sends an OK response to Okapi with the headers
     * X-Okapi-Permissions: []
     * X-Okapi-Module-Tokens: { "_" : "xx-unknown-ourlib-xx",
"login" : "xx-unknown-ourlib-login-xx" }

3.5: Okapi receives the OK response from auth:
 * Sees that we have module tokens. Copies them into the list of modules it will
be calling. The "login" module will get the "xx-unknown-ourlib-login-xx", and
all the rest (which is the auth module it has already called) get the
"xx-unknown-ourlib-xx.
 * Sees that the next module is the "login"
 * Sends a request to that one with:
     * the request body that it received
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-unknown-ourlib-login-xx

3.6: The login module receives the request:
 * It sees it has a username and password. It needs to look them up in the db.
 * It creates a request
     * GET http://folio.org/okapi/db/users/joe/passwd
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-unknown-ourlib-login-xx


3.7: Okapi receives the request, and processes it much like in 2.12
 * It checks that we know about tenant "ourlib".
 * It builds a list of modules that serve /db/users/, and that are
enabled for "ourlib".
 * This list will typically consist of two modules: first "auth", and then the
actual "db" module.
 * Okapi notes that the request contains an X-Okapi-Token. It saves the token
for future use.
 * Okapi checks what permissions are required and desired for these services. The
db module requires "db.user.read.passwd"
 * For simplicity, we assume that the db module itself does not have any specific
permissions.
 * Okapi passes the request to the auth module with these extra headers:
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-unknown-ourlib-login-xx
     * X-Okapi-Permissions-Required: [ "db.motd.read" ]
     * X-Okapi-Permissions-Desired: []
     * X-Okapi-Module-Permissions: {}

3.8: The auth module receives the request:
 * It checks that we have an X-Okapi-Token header.
 * Verifies the signature of the token.
 * Extracts the tenantId from it. There is no userId.
 * Verifies that the tenantId matches the one in X-Okapi-Tenant header, as before.
 * It sees that we require some permissions. Since we do not have any user in
the JWT, it can not look up any permissions.
 * It sees that the JWT has special modulePermissions in it. It adds the
"db.user.read.passwd" and "auth.newtoken" to the (empty) permission list.
 * It sees that we have an X-Okapi-Permissions-Required header with
"db.user.read.passwd" in it. Checks the permission list, and finds that the
permission is there.
 * Does not see any desired permissions.
 * Does not see any module permissions for the db module.
 * Since the JWT had special module permissions in it, the auth module needs to
create a new JWT without them, for further calls to different modules. It takes
the modulePermissions out of the JWT, and signs that. The result is the same as
the original JWT, "xx-unknown-ourlib-xx". It returns that in the X-Okapi-Module-Tokens
as a token for the special module "_".
 * Returns an OK response to Okapi with headers:
     * X-Okapi-Permissions: []
     * X-Okapi-Module-Tokens: { "_" : "xx-unknown-ourlib-xx" }

3.9: Okapi receives the OK response from auth:
 * Sees that there is a special module token for "_". Copies that token over to
all modules that it intends to call, overwriting the token that had login's special
permissions.
 * Sees that there are no other module tokens.
 * Sees that the next module is the db module.
 * Sends a request to the db module with:
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-unknown-ourlib-xx

3.10: The db module receives the request:
 * It looks up the hash for the password for "joe".
 * Returns it in a OK response.

3.11: Okapi receives the OK response:
 * As there are no more modules in the list, it returns the OK response to the
login module

3.12: Login module receives the OK response with the hashed password
 * It verifies that it matches the hash of the password the user entered
 * At this point we have authenticated the user.
 * Next the login module needs to create a JWT for the user. It does not do it
by itself, it asks the auth module to make one. It creates a request to
     * POST http://folio.org/okapi/auth/newtoken
     * username in the payload
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-unknown-ourlib-login-xx

3.13: Okapi receives the request, and processes it much like in 3.7 above
 * It checks that we know about tenant "ourlib".
 * It builds a list of modules that serve /auth/newtoken, and that are
enabled for "ourlib".
 * This list will typically consist of two modules: first "auth", and then the
"auth" module again, with the path to /newtoken
 * Okapi notes that the request contains an X-Okapi-Token. It saves the token
for future use.
 * Okapi checks what permissions are required and desired for these services. The
auth module/newtoken requires "auth.newtoken"
 * The auth module itself does not have any specific permissions.
 * Okapi passes the request to the auth module with these extra headers:
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-unknown-ourlib-login-xx
     * X-Okapi-Permissions-Required: [ "auth.newtoken" ]
     * X-Okapi-Permissions-Desired: []
     * X-Okapi-Module-Permissions: {}

3.14: The auth module receives the request:
 * It checks that we have an X-Okapi-Token header.
 * Verifies the signature of the token.
 * Extracts the tenantId from it. Still no userId.
 * Verifies that the tenantId matches the one in X-Okapi-Tenant header, as before.
 * It sees that we require and desire some permissions. Since we do not have any
user in the JWT, it can not look up any permissions.
 * It sees that the JWT has special modulePermissions in it. It adds the
"db.user.read.passwd" and "auth.newtoken" to the (empty) permission list.
 * It sees that we have an X-Okapi-Permissions-Required header with
"auth.newtoken" in it. Checks the permission list, and finds that the
permission is there.
 * Does not see any desired permissions.
 * Does not see any module permissions for the db module.
 * Since the JWT had special module permissions in it, the auth module needs to
create a new JWT without them, for further calls to different modules. It takes
the modulePermissions out of the JWT, and signs that. The result is the same as
the original JWT, "xx-unknown-ourlib-xx". It returns that in the X-Okapi-Module-Tokens
as a token for the special module "_".
 * Returns an OK response to Okapi with headers:
     * X-Okapi-Permissions: []
     * X-Okapi-Module-Tokens: { "_" : "xx-unknown-ourlib-xx" }

3.15: Okapi receives the OK response from auth:
 * Sees that there is a special module token for "_". Copies that token over to
all modules that it intends to call, overwriting the token that had login's special
permissions.
 * Sees that there are no other module tokens.
 * Sees that the next module is the auth module again, this time with the path /newtoken
 * Sends a request to the auth module with:
     * X-Okapi-Tenant: ourlib
     * X-Okapi-Token: xx-unknown-ourlib-xx
     * the original payload with the username

3.16: The auth module receives the request:
 * It generates a JWT with the username from the request, and tenant from the header.
Let's say that is "xx-joe-ourlib-xx"
 * Returns that in a OK response

3.17: Okapi receives the OK response:
 * As there are no more modules in the list, it returns the OK response to the
login module

3.18: At this point the login module could see if it received new permissions for
the user, say from a LDAP backend, and in that case, make a request to the
permission module to update some permissions. It would need to have a permission
to do this, and the dance with Okapi would be just like the db lookup in 3.7 - 3.11
above.

3.19: The login module returns an OK response with the new JWT

3.20: Okapi sees there are no further modules to be called, and returns the OK
response to the UI

3.21: The UI stores the JWT for use in further calls.






