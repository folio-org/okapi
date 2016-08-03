# Okapi Security Model


The Okapi security model consists of two parts: authentication and authorization.
Authentication is responsible for assuring that our user is who they claim to be.
Authorization is responsible for checking that they are allowed do that action.
Related to authorization, we have a whole lot of permission bits, and a system
to manage them. Okapi does not really care if those are handled in a separate
module, or as part of the authorization module. The permissions can come from
two sources: Either they are granted for the user, or they are granted for a module.4

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

The Okapi source tree contains a small module `okapi-auth` that does both parts,
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
what ever backend is proper. When it is satisfied, it will generate a JWT
token, either by itself, or possibly by invoking the authorization module, which
is the module really concerned with these tokens. In any case, a token is
returned to the client. The token will always contain the userid, and the
tenant where the user belongs, but may contain many other things too. It is
cryptographically signed, so its contents can not be modified without detection.

The module may also receive some permission data from its back end, and issue
an update request to the authorization module (or permissions, if that is a
separate module).

The client will pass this token in all the requests it makes, and the authorization
module will verify it every time.


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




