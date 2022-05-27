## 4.12.4 2022-05-27

Fixes:
 * [OKAPI-1099](https://issues.folio.org/browse/OKAPI-1099) Release Okapi v4.12.4 fixing ZipException on 64-bit systems

Okapi 4.12.1 - 4.12.3 are affected by OKAPI-1099.

## 4.12.3 2022-04-29

Fixes:
 * [OKAPI-1094](https://issues.folio.org/browse/OKAPI-1094) Update Vert.x from 4.2.6 to 4.2.7
 * [FOLIO-3484](https://issues.folio.org/browse/FOLIO-3484) Rebuild all released alpine-jre-openjdk11 containers fixing ZipException

## 4.12.2 2022-04-25

Fixes:
 * [FOLIO-3371](https://issues.folio.org/browse/FOLIO-3371) Unable to find jdeps, JAVA\_HOME is not correctly set in Debian build stage

## 4.12.1 2022-04-14

Fixes:
 * [OKAPI-902](https://issues.folio.org/browse/OKAPI-902) Update log4j2 configuration in Debian package
 * [OKAPI-1088](https://issues.folio.org/browse/OKAPI-1088) jackson-databind 2.13.2.1, Vert.x 4.2.6, log4j 2.17.2 (CVE-2020-36518)
   Move jackson-databind entry before jackson-bom
 * [OKAPI-1091](https://issues.folio.org/browse/OKAPI-1091) Exception for SemVer with component 4000001006

## 4.12.0 2021-12-20

Features:
 * [OKAPI-1055](https://issues.folio.org/browse/OKAPI-1055) automatic modules for modular JARs

Fixes:
 * [OKAPI-1056](https://issues.folio.org/browse/OKAPI-1056) Log4j 2.17.0 fixing self-referential lookups in Thread Context Map (CVE-2021-45105)
 * [OKAPI-1057](https://issues.folio.org/browse/OKAPI-1057) Vert.x 4.2.2, Netty 4.1.72.Final fixing header request smuggling (CVE-2021-43797)
 * [OKAPI-1058](https://issues.folio.org/browse/OKAPI-1058) Reject MDC lookups mitigating log4j (CVE-2021-45105)

## 4.11.0 2021-12-17

Features:
 * [OKAPI-1054](https://issues.folio.org/browse/OKAPI-1054) WebClientFactory to avoid socket leaks (okapi-common)

Fixes:
 * [OKAPI-1051](https://issues.folio.org/browse/OKAPI-1051) log4j 2.16.0: replacing temporary fix by upstream fix (CVE-2021-45046)
 * [OKAPI-1052](https://issues.folio.org/browse/OKAPI-1052) okapi-common uses only optional maven dependencies

Other:
 * [#1166](https://github.com/folio-org/okapi/pull/1166) Unused commons-lang3 removed

## 4.10.0 2021-12-13

Fixes:
 * [OKAPI-1050](https://issues.folio.org/browse/OKAPI-1050) -Dlog4j2.formatMsgNoLookups=true for Debian/Ubuntu package (CVE-2021-44228)
 * [OKAPI-1047](https://issues.folio.org/browse/OKAPI-1047) Disable JDNI by removing JdniLookup class
 * [OKAPI-1048](https://issues.folio.org/browse/OKAPI-1048) Hazelcast 4.2.2, logging log4j2
 * [OKAPI-1046](https://issues.folio.org/browse/OKAPI-1046) Log4j 2.15.0 fixing remote execution (CVE-2021-44228)
 * [OKAPI-1041](https://issues.folio.org/browse/OKAPI-1041) Fix warnings about _tenantPermissions version 2.0
 * [OKAPI-1037](https://issues.folio.org/browse/OKAPI-1037) Missing permission check, token cache and pre/post filter
 * [OKAPI-1038](https://issues.folio.org/browse/OKAPI-1038) Disable X-Okapi-Trace header by default
 * Upgrade to testcontainters 1.16.2 - makes Okapi pass tests on Apple M1
 * Upgrade to nuprocess 2.0.2
 * Upgrade to cron-utils 9.1.6

Other:

 * [OKAPI-1044](https://issues.folio.org/browse/OKAPI-1044) Upgrade to Vert.x 4.2.1
 * [OKAPI-1043](https://issues.folio.org/browse/OKAPI-1043) okapi reinstall

## 4.9.0 2021-09-22

Features/improvements:
 * [OKAPI-1020](https://issues.folio.org/browse/OKAPI-1020) Allow interface list for require/provide
 * [OKAPI-1024](https://issues.folio.org/browse/OKAPI-1024) Add facility to remove obsolete modules
 * [OKAPI-1034](https://issues.folio.org/browse/OKAPI-1034) Supply module id for some tenant errors
 * [OKAPI-1029](https://issues.folio.org/browse/OKAPI-1029) Cache CORS requests using `Access-Control-Max-Age header`

Fixes:
 * [OKAPI-1015](https://issues.folio.org/browse/OKAPI-1015) /saml/login timeout
 * [OKAPI-1016](https://issues.folio.org/browse/OKAPI-1016) Support delegate preflight request
 * [OKAPI-1023](https://issues.folio.org/browse/OKAPI-1023) Pull module descriptors with bulk/batch, fix connection timeout
 * [OKAPI-1025](https://issues.folio.org/browse/OKAPI-1025) `/_/proxy/tenants/{tenant_id}/upgrade` with body
 * [OKAPI-1028](https://issues.folio.org/browse/OKAPI-1028) GET `/_/proxy/modules` with invalid JSON body hangs
 * [OKAPI-1031](https://issues.folio.org/browse/OKAPI-1031) "apt-get install okapi" should recreate `/var/lib/okapi`

Other:
 * [OKAPI-1035](https://issues.folio.org/browse/OKAPI-1035) Upgrade to Vert.x 4.1.4

## 4.8.0 2021-05-11

Features / improvements:

 * [OKAPI-969](https://issues.folio.org/browse/OKAPI-969) API for Okapi timer task management
 * [OKAPI-998](https://issues.folio.org/browse/OKAPI-998) Extend ProxyService#proxyClientFailure error message
 * [OKAPI-917](https://issues.folio.org/browse/OKAPI-917) Okapi version discovery without permissions
 * [OKAPI-996](https://issues.folio.org/browse/OKAPI-996) Include module ID in some deployment error messages
 * [OKAPI-999](https://issues.folio.org/browse/OKAPI-999) okapi-testing with UtilityClassTester
 * [OKAPI-1000](https://issues.folio.org/browse/OKAPI-1000) schedule facility (ala cron)
 * [OKAPI-1004](https://issues.folio.org/browse/OKAPI-1004) OkapiClient futurisation for Vert.x 4
 * [OKAPI-1013](https://issues.folio.org/browse/OKAPI-1013) Configurable time zone for timers

Fixes:

 * [OKAPI-876](https://issues.folio.org/browse/OKAPI-876) CORS delegation doesn't work with preflight/OPTIONS requests
 * [OKAPI-991](https://issues.folio.org/browse/OKAPI-991) Fix `deploy_waitIterations` (was `deploy.waitIterations`)
 * [OKAPI-992](https://issues.folio.org/browse/OKAPI-992) Okapi discovery times out after 5 minutes
 * [OKAPI-1001](https://issues.folio.org/browse/OKAPI-1001) Pull: ignore module descriptors with unsupported features
 * [OKAPI-1009](https://issues.folio.org/browse/OKAPI-1009) Spurious ProxyTest.testTimer failure

Other:

 * [OKAPI-960](https://issues.folio.org/browse/OKAPI-960) Add personal data disclosure form
 * Upgrade to Vert.x 4.0.3

## 4.7.0 2021-02-26

This release includes improvements for communication with the
permissions module. It also includes a setting for controlling
how long Okapi waits for deployment readiness.

New features:
 * [OKAPI-985](https://issues.folio.org/browse/OKAPI-985) PoC refresh last strategy for \_tenantPemissions
 * [OKAPI-982](https://issues.folio.org/browse/OKAPI-982) Inform mod-permissions when module is disabled
 * [OKAPI-990](https://issues.folio.org/browse/OKAPI-990) Config deploy.waitIterations for Docker deployment

Fixes (some of which are also cherry-picked to 4.6+ series)
 * [OKAPI-987](https://issues.folio.org/browse/OKAPI-987) Modules require does not seem to take into account optional dependencies
 * [OKAPI-986](https://issues.folio.org/browse/OKAPI-986) Vert.x 4.0.2, Netty 4.1.59 (CVE-2021-21290)
 * [OKAPI-984](https://issues.folio.org/browse/OKAPI-984) Retry when slow module startup causes "connection refused"
 * [OKAPI-979](https://issues.folio.org/browse/OKAPI-979) Ensure module is logged for tenant operation failures
 * [OKAPI-978](https://issues.folio.org/browse/OKAPI-978) Exception on Docker pull
 * [OKAPI-976](https://issues.folio.org/browse/OKAPI-976) NPE in DiscoveryManager after restarting Okapi
 * [OKAPI-974](https://issues.folio.org/browse/OKAPI-974) Make permissionName a required property
 * [OKAPI-973](https://issues.folio.org/browse/OKAPI-973) DockerModuleHandle IllegalStateException checkEnded

Other:
 * [OKAPI-980](https://issues.folio.org/browse/OKAPI-980) Okapi-curl has a login facility. Is now maintained by Mike Taylor [here](https://github.com/MikeTaylor/okapi-curl)
 * [OKAPI-975](https://issues.folio.org/browse/OKAPI-975) Use GenericCompositeFuture rather than internal one

## 4.6.0 2021-01-15

New features:

 * [OKAPI-947](https://issues.folio.org/browse/OKAPI-947) Implement static permission migration. Okapi uses new \_tenantPermissions interface 2.0 if that's provided (by mod-permissions).

Other:

 * [OKAPI-971](https://issues.folio.org/browse/OKAPI-971) Fail unit tests if docker is not available (needed for testcontainers)

Fixes:

 * [OKAPI-968](https://issues.folio.org/browse/OKAPI-968) Fix Unhandled exception in DockerModuleHandle.request
 * [OKAPI-959](https://issues.folio.org/browse/OKAPI-959) Fix Metrics is missing values for module and url fields
 * [OKAPI-970](https://issues.folio.org/browse/OKAPI-970) Testcontainers 1.15.1 needed for latest docker
 * [OKAPI-967](https://issues.folio.org/browse/OKAPI-967) Fix Clustered Okapi not allowing bootstrapping of superuser
 * [OKAPI-966](https://issues.folio.org/browse/OKAPI-966) Fix Okapi systemd service fails to start after upgrade to docker-ce v20

## 4.5.0 2020-12-09

New features:

 * [OKAPI-958](https://issues.folio.org/browse/OKAPI-958) Upgrade to Vert.x 4.0.0
 * [OKAPI-875](https://issues.folio.org/browse/OKAPI-875) Asynchronous tenant interface. Okapi recognizes `_tenant`
   interface 2.0.
 * Provide utility script [`okapi-curl`](util/okapi-curl).

Fixes:

 * [OKAPI-956](https://issues.folio.org/browse/OKAPI-956) Update to default hazelcast version 4.0

## 4.4.0 2020-11-19

New features:

 * [OKAPI-825](https://issues.folio.org/browse/OKAPI-825) Switch from postgresql-embedded to testcontainers
 * [OKAPI-916](https://issues.folio.org/browse/OKAPI-916) Match path and pathPattern using hash map
 * [OKAPI-610](https://issues.folio.org/browse/OKAPI-610) Posting multiple module descriptors to Okapi
 * [OKAPI-910](https://issues.folio.org/browse/OKAPI-910) Cleanup ProxyContext logging
 * [OKAPI-930](https://issues.folio.org/browse/OKAPI-930) Upgrade the Okapi module for all tenants
 * [OKAPI-943](https://issues.folio.org/browse/OKAPI-943) GenericCompositeFuture utility
 * [OKAPI-934](https://issues.folio.org/browse/OKAPI-934) Update okapi.sh launch script to support dockerRegistries
 * [OKAPI-940](https://issues.folio.org/browse/OKAPI-940) Upgrade to 4.0.0CR1, Netty 4.1.53
 * [OKAPI-942](https://issues.folio.org/browse/OKAPI-942) Delete install job service

Fixes:

 * [OKAPI-932](https://issues.folio.org/browse/OKAPI-932) Fix Mongo related unit test failures
 * [OKAPI-935](https://issues.folio.org/browse/OKAPI-935) Fix Create container fails when pulling from custom registry
 * [OKAPI-936](https://issues.folio.org/browse/OKAPI-936) Fix Request logging can send compressed data to the filter
 * [OKAPI-937](https://issues.folio.org/browse/OKAPI-937) Fix okapi.sh --initdb option is broken
 * [OKAPI-944](https://issues.folio.org/browse/OKAPI-944) Fix Unit tests failed on Mac and AWS EC2
 * [OKAPI-945](https://issues.folio.org/browse/OKAPI-945) Fix Okapi silently downgrades modules to meet dependencies

## 4.3.0 2020-10-26

This release has Prometheus support for Micrometer metrics; Docker registry
authentication and K8s liveness and readiness prope support.

 * [OKAPI-894](https://issues.folio.org/browse/OKAPI-894) Fix Hazelcast Warnings (due to Java 9 or later)
 * [OKAPI-900](https://issues.folio.org/browse/OKAPI-900) Enable Prometheus support for Micrometer metrics
 * [OKAPI-903](https://issues.folio.org/browse/OKAPI-903) Fix Logging object reference rather than JSON
 * [OKAPI-904](https://issues.folio.org/browse/OKAPI-904) K8s compatible liveness and readiness probes
 * [OKAPI-912](https://issues.folio.org/browse/OKAPI-912) Docker pull with authenticated user (X-Registry-Auth)
 * [OKAPI-921](https://issues.folio.org/browse/OKAPI-921) Don't use tokenCache when no token is provided in request
 * [OKAPI-922](https://issues.folio.org/browse/OKAPI-922) Fix New Okapi member cannot join cluster
 * [OKAPI-923](https://issues.folio.org/browse/OKAPI-923) Fix many unit tests not executed
 * [OKAPI-924](https://issues.folio.org/browse/OKAPI-924) Fix starting postgresql-embed throws exception in unit tests
 * [OKAPI-925](https://issues.folio.org/browse/OKAPI-925) Fix Install does not honor explicit dependencies
 * [OKAPI-926](https://issues.folio.org/browse/OKAPI-926) Fix Install job status is complete but module stage is still invoke or pending
 * [OKAPI-927](https://issues.folio.org/browse/OKAPI-927) DepResolutionTest code cleanup
 * [OKAPI-928](https://issues.folio.org/browse/OKAPI-928) Move basic metrics code to okapi-common for sharing
 * [OKAPI-931](https://issues.folio.org/browse/OKAPI-931) Fix env delete does not remove from storage

## 4.2.0 2020-10-16

This release offers an important optimization: Token Caching [OKAPI-820](https://issues.folio.org/browse/OKAPI-820).
There is also support for async install/upgrade [OKAPI-874](https://issues.folio.org/browse/OKAPI-874).
The code has also gone through a major clean up with a change to use
Futurisation API - a change that modified more than 16% of the Java code.

 * [OKAPI-845](https://issues.folio.org/browse/OKAPI-845) Consider 'onfailure=continue' parameter for install/upgrade
 * [OKAPI-863](https://issues.folio.org/browse/OKAPI-863) Module and interface discovery for current tenant wo permissions
 * [OKAPI-868](https://issues.folio.org/browse/OKAPI-868) Add timer to capture metrics of top slow methods
 * [OKAPI-872](https://issues.folio.org/browse/OKAPI-872) Better structure for Okapi logs
 * [OKAPI-874](https://issues.folio.org/browse/OKAPI-874) install/upgrade: async operation (install jobs) phase 1
 * [OKAPI-882](https://issues.folio.org/browse/OKAPI-882) Fix Password leaks in log
 * [OKAPI-883](https://issues.folio.org/browse/OKAPI-883) Log4j2Plugins.dat in fat jar causes "Unrecognized format specifier"
 * [OKAPI-884](https://issues.folio.org/browse/OKAPI-884) % variable expansion in env breaks credentials
 * [OKAPI-885](https://issues.folio.org/browse/OKAPI-885) Warning issued: sun.reflect.Reflection.getCallerClass is not supported
 * [OKAPI-887](https://issues.folio.org/browse/OKAPI-887) Unit tests sporadically crash
 * [OKAPI-888](https://issues.folio.org/browse/OKAPI-888) Test warning: Corrupted STDOUT by directly writing to native stream in forked JVM 1.
 * [OKAPI-890](https://issues.folio.org/browse/OKAPI-890) Implement Token Cache
 * [OKAPI-891](https://issues.folio.org/browse/OKAPI-891) Event Bus check
 * [OKAPI-892](https://issues.folio.org/browse/OKAPI-892) Redirect stdout/stderr to log for process deployment
 * [OKAPI-893](https://issues.folio.org/browse/OKAPI-893) Check for openjdk-11 in okapi startup script fails
 * [OKAPI-896](https://issues.folio.org/browse/OKAPI-896) OkapiClient: use WebClient rather than HttpClient
 * [OKAPI-898](https://issues.folio.org/browse/OKAPI-898) Upgrade to Vert.x 4.0.0 Beta 3
 * [OKAPI-899](https://issues.folio.org/browse/OKAPI-899) Refactor Module handle to use Futurisation API
 * [OKAPI-905](https://issues.folio.org/browse/OKAPI-905) Remove permissions for proxy health service
 * [OKAPI-909](https://issues.folio.org/browse/OKAPI-909) Default log4j2 logging should be patternlayout; not json
 * [OKAPI-914](https://issues.folio.org/browse/OKAPI-914) Enable standalone schema validation of ModuleDescriptor
 * [OKAPI-915](https://issues.folio.org/browse/OKAPI-915) Okapi Docker deployment URL/port mismatch on restart

## 4.1.0 2020-08-25

 * [OKAPI-871](https://issues.folio.org/browse/OKAPI-871) PoC: structured logging with JSON in Okapi

## 4.0.0 2020-08-17

 * [OKAPI-879](https://issues.folio.org/browse/OKAPI-879) Upgrade Okapi to OpenJDK 11
 * [OKAPI-865](https://issues.folio.org/browse/OKAPI-865) Upgrade to Vert.x Beta 1
 * [OKAPI-860](https://issues.folio.org/browse/OKAPI-860) Add HTTP metrics to Okapi
 * [OKAPI-864](https://issues.folio.org/browse/OKAPI-864) Remove dropWizard metrics
 * [OKAPI-835](https://issues.folio.org/browse/OKAPI-835) Secure Okapi internal APIs
 * [OKAPI-873](https://issues.folio.org/browse/OKAPI-873) Update Netty (CVE-2019-16869) and Jackson (CVE-2019-14540)
 * [OKAPI-866](https://issues.folio.org/browse/OKAPI-866) Okapi uses excessive memory or OOM for bulk instance id download
 * [OKAPI-861](https://issues.folio.org/browse/OKAPI-861) Skip PostgresHandleTest if Docker is unavailable
 * [OKAPI-859](https://issues.folio.org/browse/OKAPI-859) Fail to enable module if tenant API has module permissions
 * [OKAPI-862](https://issues.folio.org/browse/OKAPI-862) Debian package from okapi-debian to okapi
 * [OKAPI-857](https://issues.folio.org/browse/OKAPI-857) Fix Okapi crashes on vertx-cache dir when changing user 'okapi'
 * [OKAPI-858](https://issues.folio.org/browse/OKAPI-858) okapi.sh should not put credentials into -D command line options

## 3.1.0 2020-06-15

 * [OKAPI-854](https://issues.folio.org/browse/OKAPI-854) Deprecate `postgres_user` (use `postgres_username`)
 * [OKAPI-852](https://issues.folio.org/browse/OKAPI-852) Support PATCH request
 * [OKAPI-847](https://issues.folio.org/browse/OKAPI-847) Conditionally defer CORS handling to module when invoked via passthrough API
 * [OKAPI-792](https://issues.folio.org/browse/OKAPI-792) PostgreSQL SSL CA Certificate configuration option
 * [OKAPI-787](https://issues.folio.org/browse/OKAPI-787) Support SSL connections to Postgres

## 3.0.0 2020-05-29

 * [OKAPI-767](https://issues.folio.org/browse/OKAPI-767) `permissionsRequired` required (securing APIs by default)
   All modules must explicitly define `permissionsRequired` for each
   routing entry for normal handlers.
 * [OKAPI-851](https://issues.folio.org/browse/OKAPI-851) Interface `_tenantPermission` version 1.1 makes permset;
   1.0 does not which is old behavior.
 * [OKAPI-850](https://issues.folio.org/browse/OKAPI-850) pom.xml dependency management (bom), upgrade Vert.x
   4.0 dependencies
 * [OKAPI-846](https://issues.folio.org/browse/OKAPI-846) Include `permissionsRequired` in all examples in guide.md
 * [OKAPI-807](https://issues.folio.org/browse/OKAPI-807) Module descriptors can no longer be updated. Module
   descriptors are immutable.
 * [OKAPI-843](https://issues.folio.org/browse/OKAPI-843) Update to log4j 2.13.2
 * [OKAPI-837](https://issues.folio.org/browse/OKAPI-837) Remove permissions from X-Okapi-Token (JWT) - convert
   module permissions to a permset
 * Remove locale dependency

## 2.40.0 2020-04-17

New facilities for services that enables and disables modules for tenant.

 * [OKAPI-831](https://issues.folio.org/browse/OKAPI-831) Support purge and tenantParameters for old-style
   disable/enable calls. Previously purge and tenantParameters were only
   recgoznized for install/upgrade. Now also for
   `/_/proxy/tenants/{tenant}/modules` and
   `/_/proxy/tenants/{tenant}/modules/{module}`.
 * [OKAPI-832](https://issues.folio.org/browse/OKAPI-832) Support new query parameter `invoke` which, with a value
   of `false`, completely disables Okapi calling a module (whether tenant
   POST, tenant purge or permission loading).

## 2.39.0 2020-04-16

 * [OKAPI-596](https://issues.folio.org/browse/OKAPI-596) Facility: disable all modules for tenant
   `DELETE /_/proxy/tenants/{tenant}/modules`
 * [OKAPI-752](https://issues.folio.org/browse/OKAPI-752) Provide example in documentation with two timers
 * [OKAPI-826](https://issues.folio.org/browse/OKAPI-826) PostgreSQL 10.11-3 Mac version does not exist
   on enterprisedb.com
 * [OKAPI-822](https://issues.folio.org/browse/OKAPI-822) ProxyTest.testTimer may fail
 * [OKAPI-823](https://issues.folio.org/browse/OKAPI-823) DockerModuleHandleTest may fail for insufficient
   Docker permissions
 * [OKAPI-827](https://issues.folio.org/browse/OKAPI-827) Fix disabling module fails for existing broken dependencies

## 2.38.0 2020-04-02

A lot of documentation fixes and reformating because Okapi was changed to
conform to Google code style (or close to). This included a few changes
to the public API.

Some performance improvements in various areas.

 * [OKAPI-821](https://issues.folio.org/browse/OKAPI-821) timers: use node leader rather than distributed locks
 * [OKAPI-820](https://issues.folio.org/browse/OKAPI-820) Wrong method for system call to auth and reusing clients
 * [OKAPI-818](https://issues.folio.org/browse/OKAPI-818) Avoid regular expression for Authorization header match
 * [OKAPI-817](https://issues.folio.org/browse/OKAPI-817) Faster match on routing entries
 * [OKAPI-809](https://issues.folio.org/browse/OKAPI-809) Google Code style plugin and conformance. The two public classes
   from okapi-common renamed: CQLUtil → CqlUtil and URLDecoder → UrlDecoder.
 * [OKAPI-819](https://issues.folio.org/browse/OKAPI-819) Fix Missing permissions POSTed to permissions module
 * [OKAPI-773](https://issues.folio.org/browse/OKAPI-773) Switch instance in "discovery" only if all modules
   installed/upgraded succesfully

## 2.37.0 2020-02-26

 * [OKAPI-797](https://issues.folio.org/browse/OKAPI-797)/OKAPI-799 Tenant init fails with Docker deployment
 * [OKAPI-795](https://issues.folio.org/browse/OKAPI-795) Optimization: only log large HTTP headers that may
   cause problems
 * [OKAPI-796](https://issues.folio.org/browse/OKAPI-796) Optimization: cache ModuleDescriptors
 * [OKAPI-509](https://issues.folio.org/browse/OKAPI-509) Optional dependencies. "optional" property
 * [OKAPI-788](https://issues.folio.org/browse/OKAPI-788) Upgrade to Vert.x 4 milestone 4
 * [OKAPI-790](https://issues.folio.org/browse/OKAPI-790) Upgrade PostgresQL from 9.6 to 10 in unit tests

## 2.36.0 2019-12-30

 * [OKAPI-321](https://issues.folio.org/browse/OKAPI-321) Unix domain socket for Docker communication
 * [OKAPI-743](https://issues.folio.org/browse/OKAPI-743) Configurable host for deployed containers
 * [OKAPI-779](https://issues.folio.org/browse/OKAPI-779) Upgrade to Vert.x 3.8.4
 * [OKAPI-780](https://issues.folio.org/browse/OKAPI-780) Switch from Vert's own logger to log4j2
 * [OKAPI-781](https://issues.folio.org/browse/OKAPI-781) SemVer, ModuleId: JavaDoc, unit tests, code review
 * [OKAPI-784](https://issues.folio.org/browse/OKAPI-784) CORS: Allow X-Okapi-Module-Id

## 2.35.0 2019-11-28

 * [OKAPI-778](https://issues.folio.org/browse/OKAPI-778) Avoid bundling log implementation with okapi-common

## 2.34.0 2019-11-28

 * [OKAPI-777](https://issues.folio.org/browse/OKAPI-777) ModuleId and SemVer toString updates, offer ModuleId.getSemVer
 * [OKAPI-774](https://issues.folio.org/browse/OKAPI-774) Switch from Future to Promise and others  (Vert.x 3.7 series)
 * Upgrade to Vert.x 3.8.3 from Vert.x 3.8.1
  
## 2.33.0 2019-09-26
 * [OKAPI-763](https://issues.folio.org/browse/OKAPI-763) Prevent X-Okapi-Token from being returned in some cases
   where they are simply returned by mistake. This is a workaround
   for modules that echo headers ([RMB-478](https://issues.folio.org/browse/RMB-478))
 * [OKAPI-764](https://issues.folio.org/browse/OKAPI-764) Prevent internal auth headers from being returnd by Okapi
   most notably X-Okapi-Module-Tokens

## 2.32.0 2019-09-09

 * [OKAPI-607](https://issues.folio.org/browse/OKAPI-607) Fix non graceful (500 error) handling of duplicate
   instId on first request only
 * [OKAPI-759](https://issues.folio.org/browse/OKAPI-759) Prevent Vert.x thread blocked warning
 * [OKAPI-753](https://issues.folio.org/browse/OKAPI-753) Yet another pull optimization. Works full if both
   ends operate at version 2.32.0 or later.
 * [OKAPI-754](https://issues.folio.org/browse/OKAPI-754) Fix adding descriptor may result in dependency error
 * [OKAPI-756](https://issues.folio.org/browse/OKAPI-756) Update doc to new supertenant name
 * [OKAPI-758](https://issues.folio.org/browse/OKAPI-758) Upgrade to Vert.x 3.8.1

## 2.31.0 2019-08-07

 * [OKAPI-751](https://issues.folio.org/browse/OKAPI-751) If multiple _timer interfaces are declared in the
   ModuleDescriptor only the first one works
 * [OKAPI-750](https://issues.folio.org/browse/OKAPI-750) Deleting MD from Okapi causes Vert.x warnings
 * [OKAPI-748](https://issues.folio.org/browse/OKAPI-748) Omitting action for install results in null pointer exception
 * [OKAPI-747](https://issues.folio.org/browse/OKAPI-747) Update Okapi guide re: database initialization
 * [OKAPI-746](https://issues.folio.org/browse/OKAPI-746) The modules upgrade failed in Okapi version 2.27.1 cluster
 * [OKAPI-745](https://issues.folio.org/browse/OKAPI-745) Update Permission schema to include visible flag
 * [OKAPI-744](https://issues.folio.org/browse/OKAPI-744) Dump HTTP Request headers in Okapi log

## 2.30.0 2019-06-07

 * [OKAPI-734](https://issues.folio.org/browse/OKAPI-734) Fix timer calls terminated for connection refused
 * [OKAPI-735](https://issues.folio.org/browse/OKAPI-735) Do not re-attempt HTTP connection on timers..
 * [OKAPI-737](https://issues.folio.org/browse/OKAPI-737) Fix Cannot restart Okapi when mod-authtoken is enabled on
   supertenant
 * [OKAPI-738](https://issues.folio.org/browse/OKAPI-738) Fix deployment issue (Increase internal deploy/undeploy timeout)
 * [OKAPI-739](https://issues.folio.org/browse/OKAPI-739) Adjust Okapi RES log to display request parameters
 * [OKAPI-740](https://issues.folio.org/browse/OKAPI-740) Fix "Wait for lock" when enabling module (with new timer
   feature) for supertenant

## 2.29.0 2019-05-10

 * [OKAPI-732](https://issues.folio.org/browse/OKAPI-732) Extend ModuleDescriptor with metadata and env properties
 * [OKAPI-733](https://issues.folio.org/browse/OKAPI-733) Document why getLock fails
 * [OKAPI-731](https://issues.folio.org/browse/OKAPI-731) Fixes for timer calls (scheduling). 1:request body was not
   empty as specified in manual. 2: X-Okapi-Url not set in timer call
 * [OKAPI-726](https://issues.folio.org/browse/OKAPI-726) Update guide for case when tenant header is omitted

## 2.28.0 2019-05-07

 * [OKAPI-730](https://issues.folio.org/browse/OKAPI-730) System call timers (period calls made by Okapi)
 * [OKAPI-725](https://issues.folio.org/browse/OKAPI-725) Fix Okapi does not pass tenant header for supertenant
 * [OKAPI-723](https://issues.folio.org/browse/OKAPI-723) Improve diagnostics for install
 * [OKAPI-722](https://issues.folio.org/browse/OKAPI-722) Fix Okapi not logging after initialization

## 2.27.0 2019-04-04

 * Chinese manual in directory doc-zh
 * [OKAPI-719](https://issues.folio.org/browse/OKAPI-719) Fix Discovery reports service on node that is terminated
 * [OKAPI-717](https://issues.folio.org/browse/OKAPI-717) Wait longer for Okapi system calls (HTTP request attempts)
 * [OKAPI-694](https://issues.folio.org/browse/OKAPI-694) Upgrade to Vert.x 3.7 series

## 2.26.0 2019-03-14

 * [OKAPI-710](https://issues.folio.org/browse/OKAPI-710) Okapi should wait for module to be available before
     calling tenant interface. Okapi did wait for process deployed
     modules, but now also for Docker deployed modules.
 * [OKAPI-709](https://issues.folio.org/browse/OKAPI-709) Command line argument -conf <fname> reads Okapi settings from
   JSON file; thus may prevent sensitive information (passwords) to be
   given on the command-line
 * [OKAPI-706](https://issues.folio.org/browse/OKAPI-706) okapi.all permissionSet missing
   okapi.proxy.pull.modules.post permission
 * [OKAPI-705](https://issues.folio.org/browse/OKAPI-705) confirm public module support

## 2.25.0 2019-02-13

 * [OKAPI-704](https://issues.folio.org/browse/OKAPI-704) Request-log filter not getting buffer for Okapi module
 * [OKAPI-703](https://issues.folio.org/browse/OKAPI-703) OkapiClient getStatusCode method
 * [OKAPI-702](https://issues.folio.org/browse/OKAPI-702) Hang for type headers when request-log is in use
 * [OKAPI-701](https://issues.folio.org/browse/OKAPI-701) hang for filter of type headers in some cases
 * [OKAPI-700](https://issues.folio.org/browse/OKAPI-700) Wrong response code returned for Okapi module
 * [OKAPI-699](https://issues.folio.org/browse/OKAPI-699) Missing x-okapi-trace headers for handler and pre/post filters
 * [OKAPI-668](https://issues.folio.org/browse/OKAPI-668) Module Descriptors should define a scope at the handler level.

## 2.24.0 2019-01-30

 * [OKAPI-661](https://issues.folio.org/browse/OKAPI-661) ModuleDescriptor replaces facility (renaming a module)
 * [OKAPI-697](https://issues.folio.org/browse/OKAPI-697) Fix POST ModuleDescriptor check=true too strict
 * [OKAPI-696](https://issues.folio.org/browse/OKAPI-696) npmSnapshot flag for /_/proxy/modules POST

## 2.23.0 2019-01-21

 * [OKAPI-693](https://issues.folio.org/browse/OKAPI-693) Introduce filter request-log to avoid buffering HTTP
   content in memory
 * [OKAPI-686](https://issues.folio.org/browse/OKAPI-686) Docker create container fails with null error
 * [OKAPI-687](https://issues.folio.org/browse/OKAPI-687) Report module dependences as a graph or similar
 * [OKAPI-692](https://issues.folio.org/browse/OKAPI-692) Unit tests refactor
 * Include active indicator set to true during create superuser (#745)
   Needed for "log in" step to work.

## 2.22.0 2018-12-06

 * [OKAPI-688](https://issues.folio.org/browse/OKAPI-688) tenant ref problem: own module not available at
    tenant init phase
 * [OKAPI-689](https://issues.folio.org/browse/OKAPI-689) Docker test hangs
 * [OKAPI-690](https://issues.folio.org/browse/OKAPI-690) Include version in Docker Engine API

## 2.21.0 2018-11-27

 * [OKAPI-685](https://issues.folio.org/browse/OKAPI-685) list modules that provides (or requires) a given interface

## 2.20.0 2018-11-26

 * [OKAPI-684](https://issues.folio.org/browse/OKAPI-684) better report dependency issues
 * [OKAPI-683](https://issues.folio.org/browse/OKAPI-683) Allow checking if uploaded Module Descriptor depends ONLY
   on released modules
 * [OKAPI-682](https://issues.folio.org/browse/OKAPI-682) Tenant init parameters
 * [OKAPI-665](https://issues.folio.org/browse/OKAPI-665) Report conflict when multiple modules provides
   compatible interface
 * [OKAPI-614](https://issues.folio.org/browse/OKAPI-614) Response to /_/proxy/tenants/{tenant_id}/install

## 2.19.0 2018-11-09

 * [OKAPI-681](https://issues.folio.org/browse/OKAPI-681) Implement and document the testStorage option
 * [OKAPI-680](https://issues.folio.org/browse/OKAPI-680) Update to RAML 1.0
 * [OKAPI-678](https://issues.folio.org/browse/OKAPI-678) Fix incorrect Content-Type for mixed content
 * [OKAPI-677](https://issues.folio.org/browse/OKAPI-677) Pass auth/handler headers to POST filter
 * [OKAPI-676](https://issues.folio.org/browse/OKAPI-676) Fix broken maven-surefire-plugin
 * [OKAPI-675](https://issues.folio.org/browse/OKAPI-675) CRLF at the end of each HTTP header line
 * [OKAPI-674](https://issues.folio.org/browse/OKAPI-674) Afford module to specify rewritePath in order to scope filters
 * [OKAPI-673](https://issues.folio.org/browse/OKAPI-673) Update to Vert.x 3.5.4
 * [OKAPI-671](https://issues.folio.org/browse/OKAPI-671) Fix POST request-response filter making bad requests
 * [OKAPI-666](https://issues.folio.org/browse/OKAPI-666) install: does not report about missing deps
 * [OKAPI-651](https://issues.folio.org/browse/OKAPI-651) Upgrade Hazelcast and include Hazelcast Discovery Plugin for
   Kubernetes
 * [OKAPI-241](https://issues.folio.org/browse/OKAPI-241) Use description field in JSON schemas

## 2.18.0 2018-10-11

 * [OKAPI-646](https://issues.folio.org/browse/OKAPI-646) Behavior change for dependency resolution for modules with
   "multiple" interfaces
 * [OKAPI-636](https://issues.folio.org/browse/OKAPI-636) Deployment leaves a process behind if can not connect to port
 * [OKAPI-633](https://issues.folio.org/browse/OKAPI-633) Upgrade to Vert 3.5.3
 * [OKAPI-635](https://issues.folio.org/browse/OKAPI-635) Return Auth filter error to caller without relying on pre/post
   filter
 * [OKAPI-639](https://issues.folio.org/browse/OKAPI-639) Pass request IP, timestamp, and method to pre/post filter
 * [OKAPI-640](https://issues.folio.org/browse/OKAPI-640) Fix extremely long startup time for Okapi on folio-snapshot-stable
 * [OKAPI-641](https://issues.folio.org/browse/OKAPI-641) Fix install: Modules order affects dependency resolution
 * [OKAPI-643](https://issues.folio.org/browse/OKAPI-643) Queries with double quotes crash
 * [OKAPI-645](https://issues.folio.org/browse/OKAPI-645) Wrong error code used in proxy service
 * [OKAPI-647](https://issues.folio.org/browse/OKAPI-647) Fix tight loop in module dependency resolution
 * [OKAPI-648](https://issues.folio.org/browse/OKAPI-648) Okapi dependency resolution omitting required interfaces
 * [OKAPI-653](https://issues.folio.org/browse/OKAPI-653) Document that X-Okapi-Url may end with a path like
   https://folio.example.com/okapi
 * [OKAPI-654](https://issues.folio.org/browse/OKAPI-654) Fix Set chunked based on server not client response
 * [OKAPI-656](https://issues.folio.org/browse/OKAPI-656) Add support for additional token header
 * [OKAPI-657](https://issues.folio.org/browse/OKAPI-657) Pass matching pathPattern to handler
 * [OKAPI-658](https://issues.folio.org/browse/OKAPI-658) Module descriptor pull performance in cluster mode
 * [OKAPI-664](https://issues.folio.org/browse/OKAPI-664) Optimize shared map usage

## 2.17.0 2018-08-02

 * [OKAPI-615](https://issues.folio.org/browse/OKAPI-615) Regression with pathPattern for tenant interface
 * [OKAPI-619](https://issues.folio.org/browse/OKAPI-619) Pass module HTTP response code to POST filter
 * [OKAPI-622](https://issues.folio.org/browse/OKAPI-622) Remove redundant test in MultiTenantTest
 * [OKAPI-623](https://issues.folio.org/browse/OKAPI-623) SQ fixes for ProcessModuleHandle
 * [OKAPI-625](https://issues.folio.org/browse/OKAPI-625) ProcessModuleHandle coverage
 * [OKAPI-627](https://issues.folio.org/browse/OKAPI-627) Duplicated Okapi filter header
 * [OKAPI-620](https://issues.folio.org/browse/OKAPI-620) Language dependent messages (i18N)
 * [OKAPI-632](https://issues.folio.org/browse/OKAPI-632) Filter phase error handling
 * [OKAPI-634](https://issues.folio.org/browse/OKAPI-634) Return Auth filter error to handler

## 2.16.1 2018-07-05

 * [OKAPI-617](https://issues.folio.org/browse/OKAPI-617) Request-only pre/post filter should not change previous HTTP
   status code
 * [OKAPI-616](https://issues.folio.org/browse/OKAPI-616) okapi-test-auth-module accepted requests without auth token
 * Updates in docs

## 2.16.0 2018-06-28

 * [OKAPI-609](https://issues.folio.org/browse/OKAPI-609) Module purge (remove persistent data for module)
 * [OKAPI-612](https://issues.folio.org/browse/OKAPI-612) Fix `DELETE /_/discovery/modules` broken

## 2.15.0 2018-06-25

 * [OKAPI-595](https://issues.folio.org/browse/OKAPI-595) Undeploy all modules operation in one operation:
     `DELETE /_/discovery/modules`
 * [OKAPI-603](https://issues.folio.org/browse/OKAPI-603) clean up operation (tenant interface that is called
   when a module is disabled).
 * [OKAPI-608](https://issues.folio.org/browse/OKAPI-608) Fix Discovery API allows registry of module that has not
   been created
 * [OKAPI-611](https://issues.folio.org/browse/OKAPI-611) Upgrade to Vert.x 3.5.2

## 2.14.1 2018-06-04

 * [OKAPI-599](https://issues.folio.org/browse/OKAPI-599) Fix binary data in okapi.log
 * [OKAPI-601](https://issues.folio.org/browse/OKAPI-601) Faster undeploy (shutdown)
 * [OKAPI-602](https://issues.folio.org/browse/OKAPI-602) Upgrade to Vert.x 3.5.1
 * [OKAPI-604](https://issues.folio.org/browse/OKAPI-604) Mention module ID on install/upgrade interface mismatch

## 2.14.0 2018-05-25

 * [OKAPI-593](https://issues.folio.org/browse/OKAPI-593) Fix X-Okapi-Filter header missing for (some?) auth filters
 * [OKAPI-591](https://issues.folio.org/browse/OKAPI-591) Add pre- and post-handler filters for reporting
 * [OKAPI-594](https://issues.folio.org/browse/OKAPI-594) Disable WAIT lines

## 2.13.1 2018-05-02

 * [OKAPI-589](https://issues.folio.org/browse/OKAPI-589) Fix GET /_/proxy/tenants/{tenants}/modules empty list

## 2.13.0 2018-05-01

 * [OKAPI-588](https://issues.folio.org/browse/OKAPI-588) Extend /_/proxy/tenants/<t>/modules with full parameter

## 2.12.2 2018-04-20

 * [OKAPI-585](https://issues.folio.org/browse/OKAPI-585) Fix WAIT msg logged when it shouldn't (/saml/check)
 * [OKAPI-586](https://issues.folio.org/browse/OKAPI-586) Include Module Id in okapi.log
 * [OKAPI-587](https://issues.folio.org/browse/OKAPI-587) Fix msg IllegalArgumentException: end must be greater or
   equal than start

## 2.12.1 2018-04-12

 * [OKAPI-576](https://issues.folio.org/browse/OKAPI-576) Invoke only first handler for multiple matches
 * [OKAPI-579](https://issues.folio.org/browse/OKAPI-579) Discovery delete of unknown module should return 404
 * [OKAPI-580](https://issues.folio.org/browse/OKAPI-580) Fix supertenant keeps old version of Okapi if enabled and
   downgrading
 * [OKAPI-581](https://issues.folio.org/browse/OKAPI-581) Fix /discovery/modules/serviceId/instanceId may throw exception

## 2.12.0 2018-04-10

 * [OKAPI-578](https://issues.folio.org/browse/OKAPI-578) Fix Unit tests hang
 * [OKAPI-577](https://issues.folio.org/browse/OKAPI-577) Discovery delete with serviceId only
   delete /_/discovery/modules/serviceId undeploys all modules with
   serviceId

## 2.11.1 2018-04-09

 * [OKAPI-571](https://issues.folio.org/browse/OKAPI-571) Fix /_/proxy/tenants returns empty list (regression since 2.8.4)
 * [OKAPI-573](https://issues.folio.org/browse/OKAPI-573) Fix install and deploy=true calls tenant init before ready
 * [OKAPI-565](https://issues.folio.org/browse/OKAPI-565) Review and catalog Okapi docs
 * [OKAPI-568](https://issues.folio.org/browse/OKAPI-568) Allow underscore in md2toc generated ToC links
 * [OKAPI-569](https://issues.folio.org/browse/OKAPI-569) Security update PostgresSQL 9.6.8
 * [OKAPI-572](https://issues.folio.org/browse/OKAPI-572) Harmonize use of RamlLoaders in unit tests
 * [OKAPI-575](https://issues.folio.org/browse/OKAPI-575) Fix distortion in logs from Docker containers
 * [OKAPI-544](https://issues.folio.org/browse/OKAPI-544) Fix X-Okapi-trace header status 200 (when it is really 204)

## 2.11.0 2018-03-23

 * [OKAPI-558](https://issues.folio.org/browse/OKAPI-558) id and name are not required while creating a tenant
 * [OKAPI-559](https://issues.folio.org/browse/OKAPI-559) -enable-metrics takes a parameter
 * [OKAPI-561](https://issues.folio.org/browse/OKAPI-561) Docker pull not working (anymore)
 * [OKAPI-563](https://issues.folio.org/browse/OKAPI-563) OkapiClient setOkapiUrl and request with Buffer

## 2.10.0 2018-03-15

 * [OKAPI-442](https://issues.folio.org/browse/OKAPI-442) Mechanism to disable dependency checks during module descriptor
   registration
 * [OKAPI-551](https://issues.folio.org/browse/OKAPI-551) Fix documentation about chunked
 * [OKAPI-552](https://issues.folio.org/browse/OKAPI-552) POST _/deployment/modules returns 500 error with invalid
   descriptor
 * [OKAPI-553](https://issues.folio.org/browse/OKAPI-553) Consider to use 400 over 500 when /env payload has missing
   required field
 * [OKAPI-554](https://issues.folio.org/browse/OKAPI-554) POST _/deployment/modules returns 500 error when all ports
   are in use
 * [OKAPI-555](https://issues.folio.org/browse/OKAPI-555) Fix Version service should return 0.0.0 when unknown
 * [OKAPI-556](https://issues.folio.org/browse/OKAPI-556) Add OkapiClient.setHeaders
 * [OKAPI-557](https://issues.folio.org/browse/OKAPI-557) Fix pull takes too long

## 2.9.4 2018-03-09

 * [OKAPI-550](https://issues.folio.org/browse/OKAPI-550) Fix X-Okapi-Permissions missing
 * [OKAPI-547](https://issues.folio.org/browse/OKAPI-547) Fix Callback endpoint stack overflow error (for double slash)
 * [OKAPI-548](https://issues.folio.org/browse/OKAPI-548) Fix Unchecked call warnings (compilation phase)

## 2.9.3 2018-03-05

 * [OKAPI-293](https://issues.folio.org/browse/OKAPI-293) Maven build fails when building from release distributions
 * [OKAPI-522](https://issues.folio.org/browse/OKAPI-522) Fix Upgrade and install POST permissions mismatch
 * [OKAPI-541](https://issues.folio.org/browse/OKAPI-541) Add requirement of Git 2 in Okapi documentation
 * [OKAPI-542](https://issues.folio.org/browse/OKAPI-542) Check RAML and code match
 * [OKAPI-543](https://issues.folio.org/browse/OKAPI-543) RAML: Make module name optional (not required)
 * [OKAPI-545](https://issues.folio.org/browse/OKAPI-545) Do not require Git for "mvn install"
 * [OKAPI-546](https://issues.folio.org/browse/OKAPI-546) Change name of internal module to "Okapi"

## 2.9.2 2018-02-26

 * [OKAPI-539](https://issues.folio.org/browse/OKAPI-539) Fix Unit test ProcessModuleHandleTest fails on Java 9
 * [OKAPI-538](https://issues.folio.org/browse/OKAPI-538) Fix unable to enable modules for tenant after mod-authtoken
   is enabled
 * [OKAPI-537](https://issues.folio.org/browse/OKAPI-537) Update to RestAssured 3.0.7
 * [OKAPI-536](https://issues.folio.org/browse/OKAPI-536) Test fails in v2.9.1 (Java 9)
 * [OKAPI-535](https://issues.folio.org/browse/OKAPI-535) Pass auth-headers only to an auth filter
 * [OKAPI-533](https://issues.folio.org/browse/OKAPI-533) Clean up response headers
 * [OKAPI-528](https://issues.folio.org/browse/OKAPI-528) Create a section about what is expected from a module
 * [OKAPI-527](https://issues.folio.org/browse/OKAPI-527) Document what headers modules are supposed to return

## 2.9.1 2018-02-22

 * [OKAPI-534](https://issues.folio.org/browse/OKAPI-534) Fix null pointer with tenant init and mod-auth

## 2.9.0 2018-02-21

 * [OKAPI-531](https://issues.folio.org/browse/OKAPI-531) Add timing for OkapiClient
 * [OKAPI-530](https://issues.folio.org/browse/OKAPI-530) Expose CQLUtils (to be used by mod-codex-mux)
 * [OKAPI-529](https://issues.folio.org/browse/OKAPI-529) Make SemVer and ModuleId classes public
 * [OKAPI-526](https://issues.folio.org/browse/OKAPI-526) fix SQ warnings
 * [OKAPI-515](https://issues.folio.org/browse/OKAPI-515) Fix 2nd call during tenant may fail

## 2.8.4 2018-02-19

 * [OKAPI-523](https://issues.folio.org/browse/OKAPI-523) Fix test-auth closes connection
 * [OKAPI-521](https://issues.folio.org/browse/OKAPI-521) Test cluster mode in unit tests
 * [OKAPI-520](https://issues.folio.org/browse/OKAPI-520) Use CompositeFuture rather than recursion
 * [OKAPI-517](https://issues.folio.org/browse/OKAPI-517) SQ fixes for ProdyService
 * [OKAPI-516](https://issues.folio.org/browse/OKAPI-516) Fix Install problem with Okapi internal and external
   deployed modules

## 2.8.3 2018-01-30

 * [OKAPI-514](https://issues.folio.org/browse/OKAPI-514) Pass X-Okapi headers when invoking deploy on a node
 * [OKAPI-512](https://issues.folio.org/browse/OKAPI-512) Fix incorrect tenant for tenant init (system call)

## 2.8.2 2018-01-29

 * [OKAPI-508](https://issues.folio.org/browse/OKAPI-508) Fix WAIT log lines for completed operation
 * [OKAPI-504](https://issues.folio.org/browse/OKAPI-504) Add "-a" (automatic) mode to md2toc

## 2.8.1 2018-01-19

 * [OKAPI-402](https://issues.folio.org/browse/OKAPI-402) The cluster gets confused when deleting nodes
 * [OKAPI-435](https://issues.folio.org/browse/OKAPI-435) proxy should load balance over available modules
 * [OKAPI-443](https://issues.folio.org/browse/OKAPI-443) Upgrade to Vert.x 3.5.0
 * [OKAPI-500](https://issues.folio.org/browse/OKAPI-500) Log requests that take a long time

## 2.8.0 2018-01-15

 * [OKAPI-494](https://issues.folio.org/browse/OKAPI-494) Fix module upgrade (for tenant): module not removed.
 * [OKAPI-495](https://issues.folio.org/browse/OKAPI-495) It should be possible to get all interfaces for a tenant
   in one go.
 * [OKAPI-496](https://issues.folio.org/browse/OKAPI-496) Limit by interface type (when returning interfaces)

## 2.7.1 2018-01-09

 * [OKAPI-489](https://issues.folio.org/browse/OKAPI-489) Fix unexpected results when using tenant install with
  "multiple"-type interfaces

## 2.7.0 2018-01-08

 * [OKAPI-486](https://issues.folio.org/browse/OKAPI-486) Allow git properties file to be given for ModuleVersionReporter
 * [OKAPI-487](https://issues.folio.org/browse/OKAPI-487) Use OkapiClient.close
 * [OKAPI-488](https://issues.folio.org/browse/OKAPI-488) Further test coverage of ProxyService
 * [OKAPI-490](https://issues.folio.org/browse/OKAPI-490) Fix Okapi fails to respond after multiple POST requests
   returning 4xx status
 * [OKAPI-491](https://issues.folio.org/browse/OKAPI-491) Fix ThreadBlocked if Okapi is downgraded

## 2.6.1 2017-12-30

 * [OKAPI-485](https://issues.folio.org/browse/OKAPI-485) Fix Okapi internal module 0.0.0

## 2.6.0 2017-12-29

 * [OKAPI-483](https://issues.folio.org/browse/OKAPI-483) Add ModuleVersionReporter utility

## 2.5.1 2017-12-24

 * [OKAPI-482](https://issues.folio.org/browse/OKAPI-482) Fix incorrect permission for disabling module for tenant

## 2.5.0 2017-12-22

 * [OKAPI-481](https://issues.folio.org/browse/OKAPI-481) Pass X-Okapi-Filter to module so that a module can
   distinguish between phases when called as a handler or filter
 * [OKAPI-480](https://issues.folio.org/browse/OKAPI-480) New routing type request-response-1.0 which uses normal
   non-chunked encoding and sets Content-Length
 * [OKAPI-459](https://issues.folio.org/browse/OKAPI-459) Run test on port range 9230-9239

## 2.4.2 2017-12-14

 * [OKAPI-473](https://issues.folio.org/browse/OKAPI-473) Long wait if test module fat jar is missing
 * [OKAPI-474](https://issues.folio.org/browse/OKAPI-474) X-Okapi-Module-ID is passed to callee
 * [OKAPI-476](https://issues.folio.org/browse/OKAPI-476) Improve error reporting for ProxyRequestResponse
 * [OKAPI-477](https://issues.folio.org/browse/OKAPI-477) Missing timing info for RES log entry
 * [OKAPI-478](https://issues.folio.org/browse/OKAPI-478) High socket count in folio/testing
 * [OKAPI-479](https://issues.folio.org/browse/OKAPI-479) Redirect container logs to logger (rather than standard error)

## 2.4.1 2017-11-24

 * Minor Refactoring (SQ reports) [OKAPI-472](https://issues.folio.org/browse/OKAPI-472)
 * Fix Okapi unit test timeout error [OKAPI-471](https://issues.folio.org/browse/OKAPI-471)

## 2.4.0 2017-11-17

 * Persistent deployment [OKAPI-423](https://issues.folio.org/browse/OKAPI-423)
 * Make okapiPerformance run again [OKAPI-468](https://issues.folio.org/browse/OKAPI-468)

## 2.3.2 2017-11-14

 * Fix Posting module list to install endpoint results in huge
   number of headers [OKAPI-466](https://issues.folio.org/browse/OKAPI-466)
 * Fix cast warnings [OKAPI-467](https://issues.folio.org/browse/OKAPI-467)

## 2.3.1 2017-11-10

 * Fix Okapi fails to restart after pull operation [OKAPI-461](https://issues.folio.org/browse/OKAPI-461)
 * Fix reload permission(set)s when enabling mod-perms [OKAPI-388](https://issues.folio.org/browse/OKAPI-388)
 * Load modulePermissions of already-enabled modules [OKAPI-417](https://issues.folio.org/browse/OKAPI-417)
 * Allow env option to skip Mongo and Postgres unit tests [OKAPI-460](https://issues.folio.org/browse/OKAPI-460)
 * Fix securing.md examples can not run [OKAPI-462](https://issues.folio.org/browse/OKAPI-462)
 * Fix "All modules shut down" repeats too many times [OKAPI-463](https://issues.folio.org/browse/OKAPI-463)

## 2.3.0 2017-11-02

 * Auto-deploy for install/upgrade operation [OKAPI-424](https://issues.folio.org/browse/OKAPI-424)
 * Docker: Okapi port substitution in dockerArgs - solves [OKAPI-458](https://issues.folio.org/browse/OKAPI-458)
 * Script/documentation on how to secure Okapi [FOLIO-913](https://issues.folio.org/browse/FOLIO-913)
   See doc/securing.md

## 2.2.0 2017-10-31

 * Rename the supertenant (used to be okapi.supertenant) [OKAPI-455](https://issues.folio.org/browse/OKAPI-455)
   Strictly speaking a breaking change but nobody was using it.
 * More testing of new XOkapiClient code [OKAPI-457](https://issues.folio.org/browse/OKAPI-457)

## 2.1.0 2017-10-26

 * Extend okapi.common ErrorType with FORBIDDEN type [OKAPI-454](https://issues.folio.org/browse/OKAPI-454)
 * Allow re-posting identical ModuleDescriptor [OKAPI-437](https://issues.folio.org/browse/OKAPI-437)
 * 404 Not Found Errors do not correctly report protocol [OKAPI-441](https://issues.folio.org/browse/OKAPI-441)
 * Simplify code for Internal Modules and more coverage [OKAPI-445](https://issues.folio.org/browse/OKAPI-445)
 * Test and improve error handling of pull [OKAPI-446](https://issues.folio.org/browse/OKAPI-446)
 * Simplify TenantManager [OKAPI-447](https://issues.folio.org/browse/OKAPI-447)
 * TenantStore simplifications [OKAPI-448](https://issues.folio.org/browse/OKAPI-448)
 * Test header module [OKAPI-449](https://issues.folio.org/browse/OKAPI-449)
 * Remove unused code from LogHelp [OKAPI-450](https://issues.folio.org/browse/OKAPI-450)
 * RestAssured : use log ifValidationFails [OKAPI-452](https://issues.folio.org/browse/OKAPI-452) * Strange logformat [OKAPI-453](https://issues.folio.org/browse/OKAPI-453)
 * Test docker - even if not present [OKAPI-454](https://issues.folio.org/browse/OKAPI-454)

## 2.0.2 2017-10-23

 * When Okapi relays HTTP responses chunked-encoding is disabled
   for 204 responses [OKAPI-440](https://issues.folio.org/browse/OKAPI-440)
 * More coverage for Process Handling cmdlineStart and Stop [OKAPI-444](https://issues.folio.org/browse/OKAPI-444)

## 2.0.1 2017-10-12

 * Fix handling of missing id when posting a module [OKAPI-438](https://issues.folio.org/browse/OKAPI-438)
 * More coverage from 58.1 to 77.8 [OKAPI-423](https://issues.folio.org/browse/OKAPI-423)/OKAPI-431
   Still missing completely is the DockerModuleHandle.
 * Fix issues as reported by SonarQube ("A")
   [OKAPI-420](https://issues.folio.org/browse/OKAPI-420)/OKAPI-421/OKAPI-432/OKAPI-430
 * Remove TODOs in NEWS [OKAPI-428](https://issues.folio.org/browse/OKAPI-428)
 * Update RAML with proper version [OKAPI-426](https://issues.folio.org/browse/OKAPI-426)
 * Fix initdatabase/purgedatabase starts cluster mode [OKAPI-414](https://issues.folio.org/browse/OKAPI-414)

## 2.0.0 2017-09-14

 * Remove support for property routingEntries [OKAPI-289](https://issues.folio.org/browse/OKAPI-289)
   Replaced by handlers and filters.
 * Remove support for environment variables in top-level MD [OKAPI-292](https://issues.folio.org/browse/OKAPI-292)
   While possible to specify, the values were never passed on by Okapi.
 * Enforce proper module ID with semantic version suffix [OKAPI-406](https://issues.folio.org/browse/OKAPI-406)
 * Remove support for tenantInterface property in MD [OKAPI-407](https://issues.folio.org/browse/OKAPI-407)
 * Check `_tenant` interface version [OKAPI-408](https://issues.folio.org/browse/OKAPI-408)
   Only 1.0 supported at this time.
 * Allow full=true parameter for `/_/proxy/modules` [OKAPI-409](https://issues.folio.org/browse/OKAPI-409)
   Allows fetch of many full MDs in one operation.
 * Remove support for top-level modulePermissions in MD [OKAPI-411](https://issues.folio.org/browse/OKAPI-411)
 * Refactor and remove timestamp from tenant [OKAPI-410](https://issues.folio.org/browse/OKAPI-410) and [OKAPI-412](https://issues.folio.org/browse/OKAPI-412)

## 1.12.0 2017-09-13

 * User-defined Okapi node names [OKAPI-400](https://issues.folio.org/browse/OKAPI-400)
 * Make the pull operation faster especially when starting from scratch
   [OKAPI-403](https://issues.folio.org/browse/OKAPI-403)
 * Fix possible missing %-decoding in path parameters [OKAPI-405](https://issues.folio.org/browse/OKAPI-405)

## 1.11.0 2017-09-08

 * Install facility can do full enable (simulate=false) [OKAPI-399](https://issues.folio.org/browse/OKAPI-399)
 * Add upgrade facility [OKAPI-350](https://issues.folio.org/browse/OKAPI-350)
 * `preRelease` parameter for install and upgrade facilities [OKAPI-397](https://issues.folio.org/browse/OKAPI-397)
 * Install facility for action=enable may take a module ID without
   semVer [OKAPI-395](https://issues.folio.org/browse/OKAPI-395) . In this case module with highest semVer is
   picked from available modules.
 * Install facility for action=disable may take a module ID without
   semVer [OKAPI-396](https://issues.folio.org/browse/OKAPI-396) .

## 1.10.0 2017-08-28

 * Fix pull fails with internal modules [OKAPI-393](https://issues.folio.org/browse/OKAPI-393)
 * Fix OkapiClient doesn't send Accept header [OKAPI-391](https://issues.folio.org/browse/OKAPI-391)
 * Separate interface versions for Okapi admin and Okapi proxy [OKAPI-390](https://issues.folio.org/browse/OKAPI-390)
 * Internal module interface version is fixed and not changed
   with Okapi version [OKAPI-387](https://issues.folio.org/browse/OKAPI-387)
 * Enable module for tenant may take module ID without version
   in which case latest version of module is picked. This should make
   it a little easier to make scripts for backend modules [OKAPI-386](https://issues.folio.org/browse/OKAPI-386)
 * Fix Query parameters erased via `/_/invoke` endpoint [OKAPI-384](https://issues.folio.org/browse/OKAPI-384)
 * Add log message for when all is closed down [OKAPI-383](https://issues.folio.org/browse/OKAPI-383)
 * Better error message for missing jar in deployment [OKAPI-382](https://issues.folio.org/browse/OKAPI-382)
 * Update security documentation a bit [OKAPI-377](https://issues.folio.org/browse/OKAPI-377)
 * Set up permissions for internal module [OKAPI-362](https://issues.folio.org/browse/OKAPI-362)
 * New install call `/_/proxy/tenant/id/install` which changes
   modules in use by tenant. Since it acts on multiple modules at once
   it can report about all necessary dependencies and conflicts . The
   operation is simulate-only at this stage, so admin users will have to
   enable modules per tenant as usual - one by one. [OKAPI-349](https://issues.folio.org/browse/OKAPI-349)

## 1.9.0 2017-08-04

 * Add facility to sort and retrieve latest MDs [OKAPI-376](https://issues.folio.org/browse/OKAPI-376)
   Okapi by default will sort on semVer order - descending when
   retrieving MDs with `/_/proxy/modules` . It is also possible to filter
   to a particular module prefix. This allows to pick all versions of
   a module or all versions with a particular version prefix (say same
   major version).  The filter looks like a semVer on its own.
 * Fix Vertx exception: request has already been written [OKAPI-374](https://issues.folio.org/browse/OKAPI-374)
   (issue appeared in 1.8.0)
 * Log Module ID rather than module Name [OKAPI-365](https://issues.folio.org/browse/OKAPI-365)
 * New feature Enable modules with dependencies for Tenant [OKAPI-349](https://issues.folio.org/browse/OKAPI-349) is
   merged within this release but it is subject to change. Do not use
   except if you are curious. Is is not documented and not complete.

## 1.8.1 2017-07-28

 * Okapi internal modules gets updated automatically [OKAPI-364](https://issues.folio.org/browse/OKAPI-364)
 * Fix leak WRT socket usage [OKAPI-370](https://issues.folio.org/browse/OKAPI-370)
 * Pass X-Okapi-User-Id to modules [OKAPI-367](https://issues.folio.org/browse/OKAPI-367)
 * Add utilities for handling semantic versions [OKAPI-366](https://issues.folio.org/browse/OKAPI-366), [OKAPI-371](https://issues.folio.org/browse/OKAPI-371), [OKAPI-372](https://issues.folio.org/browse/OKAPI-372)

## 1.8.0 2017-07-25

 * Refactor internal operations to go through the internal module
 * Create okapi.supertenant and the internal module when starting up, if not
   already there.
 * If no tenant specified, default to the okapi.supertenant, so we can get to
   the internal endpoints.

## 1.7.0 2017-06-28

 * Tenant ID may be passed part of path. This is to facilitate "callback"
   for some remote systems. Format is:
   `/_/invoke/tenant/{id}/module-path-and-parms` [OKAPI-355](https://issues.folio.org/browse/OKAPI-355)
 * Fix Okapi doesn't accept Semver "pre-release" version syntax from MD 'id' field. [OKAPI-356](https://issues.folio.org/browse/OKAPI-356)

## 1.6.1 2017-06-27

 * Fix incorrect location of upgrading module for Tenant [OKAPI-351](https://issues.folio.org/browse/OKAPI-351)
 * Environment variable OKAPI_LOGLEVEL sets log level - Command line
   still takes precedence
 * Refactor Modules and Tenants to use shared memory [OKAPI-196](https://issues.folio.org/browse/OKAPI-196), [OKAPI-354](https://issues.folio.org/browse/OKAPI-354)

## 1.6.0 2017-06-19

 * Service `/_/proxy/pull/modules` syncs remote Okapi with local one [OKAPI-347](https://issues.folio.org/browse/OKAPI-347)
 * Fix empty routing entry throws exception [OKAPI-348](https://issues.folio.org/browse/OKAPI-348)

## 1.5.0 2017-06-12

 * Service `/_/version` returns Okapi version [OKAPI-346](https://issues.folio.org/browse/OKAPI-346)
 * Allow multiple versions in requires [OKAPI-330](https://issues.folio.org/browse/OKAPI-330)
 * Minor tweak in HTTP logging to avoid double blank

## 1.4.0 2017-06-07

 * Multiple interfaces facility [OKAPI-337](https://issues.folio.org/browse/OKAPI-337)
 * Configurable Docker pull [OKAPI-345](https://issues.folio.org/browse/OKAPI-345)

## 1.3.0 2017-05-29

 * Pass visibility flag from MD to tenantPermissions [OKAPI-341](https://issues.folio.org/browse/OKAPI-341)
 * Log all traffic on TRACE level [OKAPI-328](https://issues.folio.org/browse/OKAPI-328)
 * Describe clustered operations better in the guide [OKAPI-315](https://issues.folio.org/browse/OKAPI-315)
 * Fix OKAPI allow empty srvcId for discovery endpoint [OKAPI-319](https://issues.folio.org/browse/OKAPI-319)
 * Fix Okapi should abort operation if cluster-host is invalid [OKAPI-320](https://issues.folio.org/browse/OKAPI-320)
 * Log Okapi's own operations like we log proxying ops [OKAPI-323](https://issues.folio.org/browse/OKAPI-323)
 * Okapi raml specifies "additionalProperties": false [OKAPI-326](https://issues.folio.org/browse/OKAPI-326)
 * Fix Missing exception handler for http response read stream [OKAPI-331](https://issues.folio.org/browse/OKAPI-331)
 * Fix dockerArgs not mentioned in RAML/JSON definition [OKAPI-333](https://issues.folio.org/browse/OKAPI-333)
 * Fix Tenant and Permissions interface version in guide [OKAPI-334](https://issues.folio.org/browse/OKAPI-334)
 * Fix Okapi performance test (-Pperformance) [OKAPI-338](https://issues.folio.org/browse/OKAPI-338)

## 1.2.5 2017-04-28

 * Okapi initdatabase hangs when pg db is unavailable [OKAPI-255](https://issues.folio.org/browse/OKAPI-255)
 * Check dependencies before calling tenant interface [OKAPI-277](https://issues.folio.org/browse/OKAPI-277)
 * Fix ModuleDescriptor handler without method issues NPE [OKAPI-308](https://issues.folio.org/browse/OKAPI-308)
 * Log proxy HTTP requests - with session tracking info [OKAPI-311](https://issues.folio.org/browse/OKAPI-311)
 * Upgrade to Vert.x 3.4.1 [OKAPI-313](https://issues.folio.org/browse/OKAPI-313)
 * initdatabase / purgedatabase updates and clarifications [OKAPI-316](https://issues.folio.org/browse/OKAPI-316)

## 1.2.4 2017-04-20

 * Fix hang in recursive calls for parallel requests [OKAPI-312](https://issues.folio.org/browse/OKAPI-312) / [FOLIO-516](https://issues.folio.org/browse/FOLIO-516)
 * Document PostrgreSQL init commands [OKAPI-310](https://issues.folio.org/browse/OKAPI-310)

## 1.2.3 2017-04-07

 * Warn about deprecated features in ModuleDescriptors [OKAPI-295](https://issues.folio.org/browse/OKAPI-295)
 * Inherit stderr again for deployed modules [OKAPI-307](https://issues.folio.org/browse/OKAPI-307)

## 1.2.2 2017-04-04

 * Fix Okapi failure when invoking tenantPermissions with a v1.0
   ModuleDescriptor [OKAPI-301](https://issues.folio.org/browse/OKAPI-301)
 * Fix tenantPermissions for the permission module itself [OKAPI-304](https://issues.folio.org/browse/OKAPI-304)

## 1.2.1 2017-03-31

 * Fix Null diagnostic when Docker can not be reached [OKAPI-299](https://issues.folio.org/browse/OKAPI-299)
 * Fix HTTP connection hangs after throwing error [OKAPI-298](https://issues.folio.org/browse/OKAPI-298)
 * Fix DockerTest Unit Test may timeout [OKAPI-297](https://issues.folio.org/browse/OKAPI-297)
 * Documentation: Link to API docs section and improve presentation
   of Instrumentation section

## 1.2.0 2017-03-24

 * Add ability to pull Docker images from a remote repository [OKAPI-283](https://issues.folio.org/browse/OKAPI-283)
 * Allow Handlers/filters instead of routingEntries [OKAPI-284](https://issues.folio.org/browse/OKAPI-284)
 * Allow phase instead of level inside routingEntries  [OKAPI-284](https://issues.folio.org/browse/OKAPI-284)
 * Rewrite the Okapi guide examples [OKAPI-286](https://issues.folio.org/browse/OKAPI-286)
 * Make RoutingEntry type optional and default to request-response [OKAPI-288](https://issues.folio.org/browse/OKAPI-288)
 * Fix garbage character for Docker logging [OKAPI-291](https://issues.folio.org/browse/OKAPI-291)
 * Increase wait time before warning in Unit Test for Docker [OKAPI-294](https://issues.folio.org/browse/OKAPI-294)

## 1.1.0 2017-03-20

 * New property pathPattern which is an alternative to path in
   routingEntries/handlers/filters. [OKAPI-274](https://issues.folio.org/browse/OKAPI-274)
 * routingEntries (handler) may be given for an interface [OKAPI-269](https://issues.folio.org/browse/OKAPI-269)
   This is the preferred way of declaring handlers (that implement
   an interface). Filters, on the other hand, do not implement
   an interface and stays in the usual top-level (non-interface specific
   place).
 * Permission loading interface `/_/tenantPermissions` [OKAPI-268](https://issues.folio.org/browse/OKAPI-268)
 * Define permission sets in ModuleDescriptor [OKAPI-267](https://issues.folio.org/browse/OKAPI-267)
 * Cleaned up a few things issued by SonarQube [OKAPI-279](https://issues.folio.org/browse/OKAPI-279) [OKAPI-280](https://issues.folio.org/browse/OKAPI-280)
 * Fix Okapi may hang due to standard error being buffered [OKAPI-282](https://issues.folio.org/browse/OKAPI-282)

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



