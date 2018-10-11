## 2.18.0 2018-10-11

 * OKAPI-646 Behavior change for dependency resolution for modules with "multiple" interfaces
 * OKAPI-636 Deployment leaves a process behind if can not connect to port
 * OKAPI-633 Upgrade to Vert 3.5.3
 * OKAPI-635 Return Auth filter error to caller without relying on pre/post filter
 * OKAPI-639 Pass request IP, timestamp, and method to pre/post filter
 * OKAPI-640 Fix extremely long startup time for Okapi on folio-snapshot-stable
 * OKAPI-641 Fix install: Modules order affects dependency resolution
 * OKAPI-643 Queries with double quotes crash
 * OKAPI-645 Wrong error code used in proxy service
 * OKAPI-647 Fix tight loop in module dependency resolution
 * OKAPI-648 Okapi dependency resolution omitting required interfaces
 * OKAPI-653 Document that X-Okapi-Url may end with a path like https://folio.example.com/okapi
 * OKAPI-654 Fix Set chunked based on server not client response
 * OKAPI-656 Add support for additional token header
 * OKAPI-657 Pass matching pathPattern to handler
 * OKAPI-658 Module descriptor pull performance in cluster mode
 * OKAPI-664 Optimize shared map usage

## 2.17.0 2018-08-02

 * OKAPI-615 Regression with pathPattern for tenant interface
 * OKAPI-619 Pass module HTTP response code to POST filter
 * OKAPI-622 Remove redundant test in MultiTenantTest
 * OKAPI-623 SQ fixes for ProcessModuleHandle
 * OKAPI-625 ProcessModuleHandle coverage
 * OKAPI-627 Duplicated Okapi filter header
 * OKAPI-620 Language dependent messages (i18N)
 * OKAPI-632 Filter phase error handling
 * OKAPI-634 Return Auth filter error to handler

## 2.16.1 2018-07-05

 * OKAPI-617 Request-only pre/post filter should not change previous HTTP status code
 * OKAPI-616 okapi-test-auth-module accepted requests without auth token
 * Updates in docs

## 2.16.0 2018-06-28

 * OKAPI-609 Module purge (remove persistent data for module)
 * OKAPI-612 Fix `DELETE /_/discovery/modules` broken

## 2.15.0 2018-06-25

 * OKAPI-595 Undeploy all modules operation in one operation:
     `DELETE /_/discovery/modules`
 * OKAPI-603 clean up operation (tenant interface that is called
   when a module is disabled).
 * OKAPI-608 Fix Discovery API allows registry of module that has not
   been created
 * OKAPI-611 Upgrade to Vert.x 3.5.2

## 2.14.1 2018-06-04

 * OKAPI-599 Fix binary data in okapi.log
 * OKAPI-601 Faster undeploy (shutdown)
 * OKAPI-602 Upgrade to Vert.x 3.5.1
 * OKAPI-604 Mention module ID on install/upgrade interface mismatch

## 2.14.0 2018-05-25

 * OKAPI-593 Fix X-Okapi-Filter header missing for (some?) auth filters
 * OKAPI-591 Add pre- and post-handler filters for reporting
 * OKAPI-594 Disable WAIT lines

## 2.13.1 2018-05-02

 * OKAPI-589 Fix GET /_/proxy/tenants/{tenants}/modules empty list

## 2.13.0 2018-05-01

 * OKAPI-588 Extend /_/proxy/tenants/<t>/modules with full parameter

## 2.12.2 2018-04-20

 * OKAPI-585 Fix WAIT msg logged when it shouldn't (/saml/check)
 * OKAPI-586 Include Module Id in okapi.log
 * OKAPI-587 Fix msg IllegalArgumentException: end must be greater or
   equal than start

## 2.12.1 2018-04-12

 * OKAPI-576 Invoke only first handler for multiple matches
 * OKAPI-579 Discovery delete of unknown module should return 404
 * OKAPI-580 Fix supertenant keeps old version of Okapi if enabled and
   downgrading
 * OKAPI-581 Fix /discovery/modules/serviceId/instanceId may throw exception

## 2.12.0 2018-04-10

 * OKAPI-578 Fix Unit tests hang
 * OKAPI-577 Discovery delete with serviceId only
   delete /_/discovery/modules/serviceId undeploys all modules with
   serviceId

## 2.11.1 2018-04-09

 * OKAPI-571 Fix /_/proxy/tenants returns empty list (regression since 2.8.4)
 * OKAPI-573 Fix install and deploy=true calls tenant init before ready
 * OKAPI-565 Review and catalog Okapi docs
 * OKAPI-568 Allow underscore in md2toc generated ToC links
 * OKAPI-569 Security update PostgresSQL 9.6.8
 * OKAPI-572 Harmonize use of RamlLoaders in unit tests
 * OKAPI-575 Fix distortion in logs from Docker containers
 * OKAPI-544 Fix X-Okapi-trace header status 200 (when it is really 204)

## 2.11.0 2018-03-23

 * OKAPI-558 id and name are not required while creating a tenant
 * OKAPI-559 -enable-metrics takes a parameter
 * OKAPI-561 Docker pull not working (anymore)
 * OKAPI-563 OkapiClient setOkapiUrl and request with Buffer

## 2.10.0 2018-03-15

 * OKAPI-442 Mechanism to disable dependency checks during module descriptor registration
 * OKAPI-551 Fix documentation about chunked
 * OKAPI-552 POST _/deployment/modules returns 500 error with invalid
   descriptor
 * OKAPI-553 Consider to use 400 over 500 when /env payload has missing
   required field
 * OKAPI-554 POST _/deployment/modules returns 500 error when all ports
   are in use
 * OKAPI-555 Fix Version service should return 0.0.0 when unknown
 * OKAPI-556 Add OkapiClient.setHeaders
 * OKAPI-557 Fix pull takes too long

## 2.9.4 2018-03-09

 * OKAPI-550 Fix X-Okapi-Permissions missing
 * OKAPI-547 Fix Callback endpoint stack overflow error (for double slash)
 * OKAPI-548 Fix Unchecked call warnings (compilation phase)

## 2.9.3 2018-03-05

 * OKAPI-293 Maven build fails when building from release distributions
 * OKAPI-522 Fix Upgrade and install POST permissions mismatch
 * OKAPI-541 Add requirement of Git 2 in Okapi documentation
 * OKAPI-542 Check RAML and code match
 * OKAPI-543 RAML: Make module name optional (not required)
 * OKAPI-545 Do not require Git for "mvn install"
 * OKAPI-546 Change name of internal module to "Okapi"

## 2.9.2 2018-02-26

 * OKAPI-539 Fix Unit test ProcessModuleHandleTest fails on Java 9
 * OKAPI-538 Fix unable to enable modules for tenant after mod-authtoken
   is enabled
 * OKAPI-537 Update to RestAssured 3.0.7
 * OKAPI-536 Test fails in v2.9.1 (Java 9)
 * OKAPI-535 Pass auth-headers only to an auth filter
 * OKAPI-533 Clean up response headers
 * OKAPI-528 Create a section about what is expected from a module
 * OKAPI-527 Document what headers modules are supposed to return

## 2.9.1 2018-02-22

 * OKAPI-534 Fix null pointer with tenant init and mod-auth

## 2.9.0 2018-02-21

 * OKAPI-531 Add timing for OkapiClient
 * OKAPI-530 Expose CQLUtils (to be used by mod-codex-mux)
 * OKAPI-529 Make SemVer and ModuleId classes public
 * OKAPI-526 fix SQ warnings
 * OKAPI-515 Fix 2nd call during tenant may fail

## 2.8.4 2018-02-19

 * OKAPI-523 Fix test-auth closes connection
 * OKAPI-521 Test cluster mode in unit tests
 * OKAPI-520 Use CompositeFuture rather than recursion
 * OKAPI-517 SQ fixes for ProdyService
 * OKAPI-516 Fix Install problem with Okapi internal and external
   deployed modules

## 2.8.3 2018-01-30

 * OKAPI-514 Pass X-Okapi headers when invoking deploy on a node
 * OKAPI-512 Fix incorrect tenant for tenant init (system call)

## 2.8.2 2018-01-29

 * OKAPI-508 Fix WAIT log lines for completed operation
 * OKAPI-504 Add "-a" (automatic) mode to md2toc

## 2.8.1 2018-01-19

 * OKAPI-402 The cluster gets confused when deleting nodes
 * OKAPI-435 proxy should load balance over available modules
 * OKAPI-443 Upgrade to Vert.x 3.5.0
 * OKAPI-500 Log requests that take a long time

## 2.8.0 2018-01-15

 * OKAPI-494 Fix module upgrade (for tenant): module not removed.
 * OKAPI-495 It should be possible to get all interfaces for a tenant
   in one go.
 * OKAPI-496 Limit by interface type (when returning interfaces)

## 2.7.1 2018-01-09

 * OKAPI-489 Fix unexpected results when using tenant install with
  "multiple"-type interfaces

## 2.7.0 2018-01-08

 * OKAPI-486 Allow git properties file to be given for ModuleVersionReporter
 * OKAPI-487 Use OkapiClient.close
 * OKAPI-488 Further test coverage of ProxyService
 * OKAPI-490 Fix Okapi fails to respond after multiple POST requests
   returning 4xx status
 * OKAPI-491 Fix ThreadBlocked if Okapi is downgraded

## 2.6.1 2017-12-30

 * OKAPI-485 Fix Okapi internal module 0.0.0

## 2.6.0 2017-12-29

 * OKAPI-483 Add ModuleVersionReporter utility

## 2.5.1 2017-12-24

 * OKAPI-482 Fix incorrect permission for disabling module for tenant

## 2.5.0 2017-12-22

 * OKAPI-481 Pass X-Okapi-Filter to module so that a module can
   distinguish between phases when called as a handler or filter
 * OKAPI-480 New routing type request-response-1.0 which uses normal
   non-chunked encoding and sets Content-Length
 * OKAPI-459 Run test on port range 9230-9239

## 2.4.2 2017-12-14

 * OKAPI-473 Long wait if test module fat jar is missing
 * OKAPI-474 X-Okapi-Module-ID is passed to callee
 * OKAPI-476 Improve error reporting for ProxyRequestResponse
 * OKAPI-477 Missing timing info for RES log entry
 * OKAPI-478 High socket count in folio/testing
 * OKAPI-479 Redirect container logs to logger (rather than standard error)

## 2.4.1 2017-11-24

 * Minor Refactoring (SQ reports) OKAPI-472
 * Fix Okapi unit test timeout error OKAPI-471

## 2.4.0 2017-11-17

 * Persistent deployment OKAPI-423
 * Make okapiPerformance run again OKAPI-468

## 2.3.2 2017-11-14

 * Fix Posting module list to install endpoint results in huge
   number of headers OKAPI-466
 * Fix cast warnings OKAPI-467

## 2.3.1 2017-11-10

 * Fix Okapi fails to restart after pull operation OKAPI-461
 * Fix reload permission(set)s when enabling mod-perms OKAPI-388
 * Load modulePermissions of already-enabled modules OKAPI-417
 * Allow env option to skip Mongo and Postgres unit tests OKAPI-460
 * Fix securing.md examples can not run OKAPI-462
 * Fix "All modules shut down" repeats too many times OKAPI-463

## 2.3.0 2017-11-02

 * Auto-deploy for install/upgrade operation OKAPI-424
 * Docker: Okapi port substitution in dockerArgs - solves OKAPI-458
 * Script/documentation on how to secure Okapi FOLIO-913
   See doc/securing.md

## 2.2.0 2017-10-31

 * Rename the supertenant (used to be okapi.supertenant) OKAPI-455
   Strictly speaking a breaking change but nobody was using it.
 * More testing of new XOkapiClient code OKAPI-457

## 2.1.0 2017-10-26

 * Extend okapi.common ErrorType with FORBIDDEN type OKAPI-454
 * Allow re-posting identical ModuleDescriptor OKAPI-437
 * 404 Not Found Errors do not correctly report protocol OKAPI-441
 * Simplify code for Internal Modules and more coverage OKAPI-445
 * Test and improve error handling of pull OKAPI-446
 * Simplify TenantManager OKAPI-447
 * TenantStore simplifications OKAPI-448
 * Test header module OKAPI-449
 * Remove unused code from LogHelp OKAPI-450
 * RestAssured : use log ifValidationFails OKAPI-452
 * Strange logformat OKAPI-453
 * Test docker - even if not present OKAPI-454

## 2.0.2 2017-10-23

 * When Okapi relays HTTP responses chunked-encoding is disabled
   for 204 responses OKAPI-440
 * More coverage for Process Handling cmdlineStart and Stop OKAPI-444

## 2.0.1 2017-10-12

 * Fix handling of missing id when posting a module OKAPI-438
 * More coverage from 58.1 to 77.8 OKAPI-423/OKAPI-431
   Still missing completely is the DockerModuleHandle.
 * Fix issues as reported by SonarQube ("A")
   OKAPI-420/OKAPI-421/OKAPI-432/OKAPI-430
 * Remove TODOs in NEWS OKAPI-428
 * Update RAML with proper version OKAPI-426
 * Fix initdatabase/purgedatabase starts cluster mode OKAPI-414

## 2.0.0 2017-09-14

 * Remove support for property routingEntries OKAPI-289
   Replaced by handlers and filters.
 * Remove support for environment variables in top-level MD OKAPI-292
   While possible to specify, the values were never passed on by Okapi.
 * Enforce proper module ID with semantic version suffix OKAPI-406
 * Remove support for tenantInterface property in MD OKAPI-407
 * Check `_tenant` interface version OKAPI-408
   Only 1.0 supported at this time.
 * Allow full=true parameter for `/_/proxy/modules` OKAPI-409
   Allows fetch of many full MDs in one operation.
 * Remove support for top-level modulePermissions in MD OKAPI-411
 * Refactor and remove timestamp from tenant OKAPI-410 and OKAPI-412

## 1.12.0 2017-09-13

 * User-defined Okapi node names OKAPI-400
 * Make the pull operation faster especially when starting from scratch
   OKAPI-403
 * Fix possible missing %-decoding in path parameters OKAPI-405

## 1.11.0 2017-09-08

 * Install facility can do full enable (simulate=false) OKAPI-399
 * Add upgrade facility OKAPI-350
 * `preRelease` parameter for install and upgrade facilities OKAPI-397
 * Install facility for action=enable may take a module ID without
   semVer OKAPI-395 . In this case module with highest semVer is
   picked from available modules.
 * Install facility for action=disable may take a module ID without
   semVer OKAPI-396 .

## 1.10.0 2017-08-28

 * Fix pull fails with internal modules OKAPI-393
 * Fix OkapiClient doesn't send Accept header OKAPI-391
 * Separate interface versions for Okapi admin and Okapi proxy OKAPI-390
 * Internal module interface version is fixed and not changed
   with Okapi version OKAPI-387
 * Enable module for tenant may take module ID without version
   in which case latest version of module is picked. This should make
   it a little easier to make scripts for backend modules OKAPI-386
 * Fix Query parameters erased via `/_/invoke` endpoint OKAPI-384
 * Add log message for when all is closed down OKAPI-383
 * Better error message for missing jar in deployment OKAPI-382
 * Update security documentation a bit OKAPI-377
 * Set up permissions for internal module OKAPI-362
 * New install call `/_/proxy/tenant/id/install` which changes
   modules in use by tenant. Since it acts on multiple modules at once
   it can report about all necessary dependencies and conflicts . The
   operation is simulate-only at this stage, so admin users will have to
   enable modules per tenant as usual - one by one. OKAPI-349

## 1.9.0 2017-08-04

 * Add facility to sort and retrieve latest MDs OKAPI-376
   Okapi by default will sort on semVer order - descending when
   retrieving MDs with `/_/proxy/modules` . It is also possible to filter
   to a particular module prefix. This allows to pick all versions of
   a module or all versions with a particular version prefix (say same
   major version).  The filter looks like a semVer on its own.
 * Fix Vertx exception: request has already been written OKAPI-374
   (issue appeared in 1.8.0)
 * Log Module ID rather than module Name OKAPI-365
 * New feature Enable modules with dependencies for Tenant OKAPI-349 is
   merged within this release but it is subject to change. Do not use
   except if you are curious. Is is not documented and not complete.

## 1.8.1 2017-07-28

 * Okapi internal modules gets updated automatically OKAPI-364
 * Fix leak WRT socket usage OKAPI-370
 * Pass X-Okapi-User-Id to modules OKAPI-367
 * Add utilities for handling semantic versions OKAPI-366, OKAPI-371, OKAPI-372

## 1.8.0 2017-07-25

 * Refactor internal operations to go through the internal module
 * Create okapi.supertenant and the internal module when starting up, if not
   already there.
 * If no tenant specified, default to the okapi.supertenant, so we can get to
   the internal endpoints.

## 1.7.0 2017-06-28

 * Tenant ID may be passed part of path. This is to facilitate "callback"
   for some remote systems. Format is:
   `/_/invoke/tenant/{id}/module-path-and-parms` OKAPI-355
 * Fix Okapi doesn't accept Semver "pre-release" version syntax from MD 'id' field. OKAPI-356

## 1.6.1 2017-06-27

 * Fix incorrect location of upgrading module for Tenant OKAPI-351
 * Environment variable OKAPI_LOGLEVEL sets log level - Command line
   still takes precedence
 * Refactor Modules and Tenants to use shared memory OKAPI-196, OKAPI-354

## 1.6.0 2017-06-19

 * Service `/_/proxy/pull/modules` syncs remote Okapi with local one OKAPI-347
 * Fix empty routing entry throws exception OKAPI-348

## 1.5.0 2017-06-12

 * Service `/_/version` returns Okapi version OKAPI-346
 * Allow multiple versions in requires OKAPI-330
 * Minor tweak in HTTP logging to avoid double blank

## 1.4.0 2017-06-07

 * Multiple interfaces facility OKAPI-337
 * Configurable Docker pull OKAPI-345

## 1.3.0 2017-05-29

 * Pass visibility flag from MD to tenantPermissions OKAPI-341
 * Log all traffic on TRACE level OKAPI-328
 * Describe clustered operations better in the guide OKAPI-315
 * Fix OKAPI allow empty srvcId for discovery endpoint OKAPI-319
 * Fix Okapi should abort operation if cluster-host is invalid OKAPI-320
 * Log Okapi's own operations like we log proxying ops OKAPI-323
 * Okapi raml specifies "additionalProperties": false OKAPI-326
 * Fix Missing exception handler for http response read stream OKAPI-331
 * Fix dockerArgs not mentioned in RAML/JSON definition OKAPI-333
 * Fix Tenant and Permissions interface version in guide OKAPI-334
 * Fix Okapi performance test (-Pperformance) OKAPI-338

## 1.2.5 2017-04-28

 * Okapi initdatabase hangs when pg db is unavailable OKAPI-255
 * Check dependencies before calling tenant interface OKAPI-277
 * Fix ModuleDescriptor handler without method issues NPE OKAPI-308
 * Log proxy HTTP requests - with session tracking info OKAPI-311
 * Upgrade to Vert.x 3.4.1 OKAPI-313
 * initdatabase / purgedatabase updates and clarifications OKAPI-316

## 1.2.4 2017-04-20

 * Fix hang in recursive calls for parallel requests OKAPI-312 / FOLIO-516
 * Document PostrgreSQL init commands OKAPI-310

## 1.2.3 2017-04-07

 * Warn about deprecated features in ModuleDescriptors OKAPI-295
 * Inherit stderr again for deployed modules OKAPI-307

## 1.2.2 2017-04-04

 * Fix Okapi failure when invoking tenantPermissions with a v1.0
   ModuleDescriptor OKAPI-301
 * Fix tenantPermissions for the permission module itself OKAPI-304

## 1.2.1 2017-03-31

 * Fix Null diagnostic when Docker can not be reached OKAPI-299
 * Fix HTTP connection hangs after throwing error OKAPI-298
 * Fix DockerTest Unit Test may timeout OKAPI-297
 * Documentation: Link to API docs section and improve presentation
   of Instrumentation section

## 1.2.0 2017-03-24

 * Add ability to pull Docker images from a remote repository OKAPI-283
 * Allow Handlers/filters instead of routingEntries OKAPI-284
 * Allow phase instead of level inside routingEntries  OKAPI-284
 * Rewrite the Okapi guide examples OKAPI-286
 * Make RoutingEntry type optional and default to request-response OKAPI-288
 * Fix garbage character for Docker logging OKAPI-291
 * Increase wait time before warning in Unit Test for Docker OKAPI-294

## 1.1.0 2017-03-20

 * New property pathPattern which is an alternative to path in
   routingEntries/handlers/filters. OKAPI-274
 * routingEntries (handler) may be given for an interface OKAPI-269
   This is the preferred way of declaring handlers (that implement
   an interface). Filters, on the other hand, do not implement
   an interface and stays in the usual top-level (non-interface specific
   place).
 * Permission loading interface `/_/tenantPermissions` OKAPI-268
 * Define permission sets in ModuleDescriptor OKAPI-267
 * Cleaned up a few things issued by SonarQube OKAPI-279 OKAPI-280
 * Fix Okapi may hang due to standard error being buffered OKAPI-282

## 1.0.0 2017-02-27

 * Postgres storage option. Mongodb still supported
 * New commands: initdatabase and purgedatabase. Deprecated are
   properties -Dmongo_db_init and -Dpostgres_db_init
 * Docker support. Triggered in launchDescriptor with
   dockerImage property. It can be tuned further with properties
   dockerCMD and dockerArgs.
 * Tenant initialization: Okapi may call Module when it is
   associated with a module and when changing from one module to
   another (upgrade).
 * Everything else since 0.3

## 0.3 2016-05-03

 * Split of Okapi into three services: deployment, discovery and proxy
 * Hazelcast can be configured (including work with AWS)
 * Running mode must be given in command line: dev (for development)
   and cluster (for clustered mode)
 * Module version dependencies
 * Bug fixes and more tests

## 0.2 2016-03-23

 * RAML updates and verified in many tests
 * Persistent storage with MongoDB
 * Event Bus in use to synchronize a set of Okapi nodes
 * Using log4j as logger everywhere (using SLF4J to relay for all things)

## 0.1 2016-01-29

 * First code release
 * Offers simple gateway API and HTTP proxy functionality
 * On GitHub!!
 * Some documentation
 * Uses Vert.x for non-blocking services
 * Two dummy example modules - used by Unit tests



