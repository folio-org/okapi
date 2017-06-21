
Okapi-344: Internal modules

This is a working document, specifying what needs to be done to get the internal
services to behave like modules, and what the implications will be.

Overview:
  * Instead of Okapi just serving things like `/_/modules`, make it use a proper
    pipeline for such
  * That implies that we can have auth checks in the pipeline, and finally have
    access control for Okapi administration
  * Since we will have access control, each request needs to be made on behalf
    of a tenant, by a logged-in user.
  * There is a start-up problem: We need to act on behalf of a tenant in order
    to create the first tenant. The solution is to have a super-tenant appear
    magically when Okapi starts up.
  * Likewise, the internal modules must appear when Okapi starts up, so we can
    use them for creating modules and tenants.
  * There can be other start-up problems with users, needs a bit more thought.


