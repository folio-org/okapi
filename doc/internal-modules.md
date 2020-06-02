# Internal modules
Okapi-344


This is a working document, specifying what needs to be done to get the internal
services to behave like modules, and what the implications will be. This is a
working document, always under construction. When the job is all done, lots of
this has become irrelevant, and the rest should be included in the real
documentation.


## Overview:
* Instead of Okapi just serving things like `/_/modules`, make it use a proper
  pipeline for such.
* That implies that we can have auth checks in the pipeline, and finally have
  access control for Okapi administration.
* Since we will have access control, each request needs to be made on behalf
  of a tenant, by a logged-in user.
* There is a start-up problem: We need to act on behalf of a tenant in order
  to create the first tenant. The solution is to have a super-tenant appear
  magically when Okapi starts up.
* Likewise, the internal modules must appear when Okapi starts up, so we can
  use them for creating modules and tenants.
* There can be other start-up problems with users, needs a bit more thought.
* Since the request to enable a module for a tenant is now made with a proper
  auth token, that service can make further requests to Okapi, for example to
  load test data to other modules.


## How to get there
* Make sure Okapi creates the super tenant and internal ModuleDescriptors at
startup, if needed.
* Define the "internal" module type.
* Make proxy route requests to the internal type.
* Make sure one endpoint works with the internals, maybe `/_/tenants`.
* The rest of the services.


## Open questions


### Permissions
Since we will have access control, we need to design which permissions are
needed for all the operations. Things to consider:
* The UI may need to make some calls, for example for the `about` page. Most
users should be allowed to see that, maybe it could be left totally unprotected?
* Make an explicit list of the permissions for various operations.
* We can not make the ModulePermissions call for the internal modules, since
we don't have mod-perms available at the time. What we can do instead, we can
create a separate module, okapi-permsets, just for loading the permission sets.

### Starting up a fresh installation
There is a bit of a chicken-and-egg problem when starting from fresh. The order
of things is critical. We should not enable login checks before we have created
enough data, so that we can log in.

Here is my idea of how things could work:

* Okapi starts up, sees that its database is empty, so creates MDs for its internal services and a super-tenant
* Admin installs and deploys mod-perms, mod-login, and mod-authtoken
* Admin enables mod-perms and mod-login.
* Admin posts credentials for the superuser into mod-login, and initial permissions to mod-perms
* Admin enables mod-authtoken. Now the system is locked.
* Admin logs in with the credentials just posted.

Admin installs and deploys the rest of the modules needed for the installation:

* Admin creates a real tenant.
* Admin enables mod-perms and mod-login for the new tenant.
* Admin posts credentials for the tenants super-user.
* Admin enables mod-authtoken for the tenant. Now the tenant is locked.
* Admin may log in as the super-user for that tenant, and enable the rest of relevant modules.

Of course, the steps above should be scripted. Possibly in two scripts, one to
set up the super-tenant and its superuser, and another to be run for every
tenant we wish to create.

### Deploy-only mode
Even in deploy-only mode, Okapi now needs to have some proxying enabled, in
order to to reach the deployment endpoints. In that mode, we will not need to
participate in the shared maps of tenants and modules, since no real proxying
needs to be done.

### Default to super-tenant
If a request comes in to Okapi without any X-Okapi-Tenant header (and without
any auth token, from which the tenant could be deduced), we must not fail it
immediately. Instead we must run as the super-tenant. That way, all the examples
in our guide will work, and it will be possible to start up an installation.

### Calling an internal module
The code in ProxyService is fairly complex, with buffered and streamed requests
and responses. The internal type module interfaces must be compatible with that.
I am currently (29-Jun-2017) looking into this... I think we need to make the
interface trickery in ProxyService, so that the individual services will just
receive a request buffer, headers, and produce a response buffer.





