 * new property pathPattern which is an alternative to path in
   routingEntries/handlers/filters. OKAPI-274
 * routingEntries (handler) may be given for an interface OKAPI-269
   This is the preferred way of declaring handlers (that implement
   and interface). Filters, on other other hand, do not implement
   and interface and stays in the usual global (non-interface specific
   place).
 * Permission loading interface /_/tenantPermissions OKAPI-268
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

### TODO

 * Interface with Consul for Clustering, Service Discovery, etc.
 * Header merging (#33)


