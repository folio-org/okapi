# Disabling and undeploying unwanted FOLIO modules


## Background

FOLIO is big. [Really big](https://www.goodreads.com/quotes/14434-space-is-big-you-just-won-t-believe-how-vastly-hugely). If you're working on developing a module for it, you are likely running against a virtual machine managed by Vagrant -- but such VMs now need a great deal of memory. For example, `mod-copycat` uses enough FOLIO backend modules that it can't run against the `folio/core` Vagrant box, and needs `folio/testing`, which needs more than 10 Gb RAM if it is to avoid an orgy of paging that will render the host box all but unusable.

So to reduce the in-memory size of FOLIO, we want to identify modules that we know we don't need and shut them down.

This is a multi-stage process:

<!-- md2toc -l 2 -s 1 unwanted-modules.md -->
* [1. Identify the modules running on the tenant of interest](#1-identify-the-modules-running-on-the-tenant-of-interest)
* [2. Choose the ones that are not wanted](#2-choose-the-ones-that-are-not-wanted)
* [3. Check that disabling will not have catastrophic consequences](#3-check-that-disabling-will-not-have-catastrophic-consequences)
* [4. Disable these modules (and those that depend on them)](#4-disable-these-modules-and-those-that-depend-on-them)
* [5. Undeploy those modules including those that depend on them](#5-undeploy-those-modules-including-those-that-depend-on-them)

In the development scenario, there is almost always only a single tenant in play, `diku`, so we will make that simplifying assumption in this document. To make the examples concrete, we will also assume that Okapi is running on localhost port 9130.


## 1. Identify the modules running on the tenant of interest

You can find these at http://localhost:9130/_/proxy/tenants/diku/modules

(At the time of writing, there are exactly 100 of them!)

Ignore the `names`, and consider the `id`s, discarding the version number. For example, if the output includes `edge-ncip-1.7.0-SNAPSHOT.17`, then you are interested in the ID `edge-ncip`.


## 2. Choose the ones that are not wanted

For example, if you are working on the single-record copy-cataloguing facilities provided by mod-copycat, then you certainly need to retain `mod-inventory`, `but edge-oai-pmh` is not likely to be of interest, and `mod-circulation` may not be needed.


## 3. Check that disabling will not have catastrophic consequences

In the next step, we are going to disable a named module together with all other modules that depend on it (or, more precisely, on interfaces that it provides), and the module that depend on those, and so on. By first running that step with a `simulate=true` query, we can see the full list of modules that will be disabled:

	curl -X POST http://localhost:9130/_/proxy/tenants/diku/install?simulate=true -d '[{"id":"mod-circulation","action":"disable"}]'
	[ {
	  "id" : "mod-orders-11.2.0-SNAPSHOT.376",
	  "action" : "disable"
	}, {
	  "id" : "mod-ncip-1.6.4-SNAPSHOT.31",
	  "action" : "disable"
	}, {
	  "id" : "mod-circulation-19.3.0-SNAPSHOT.790",
	  "action" : "disable"
	} ]

If this cascade results in the removal of modules that are needed, then a rethink is called for.


## 4. Disable these modules (and those that depend on them)

If you are happy with the cascade, go ahead and disable the module:

	curl -X POST http://localhost:9130/_/proxy/tenants/diku/install -d '[{"id":"mod-circulation","action":"disable"}]'

## 5. Undeploy those modules including those that depend on them

The module and its dependents are now disabled for the `diku` tenant, but are still running and consuming resources. To undeploy them, it is necessary to DELETE them from `/_/discovery/modules` using the `id` fields reported by the disable response:

	curl -X DELETE http://localhost:9130/_/discovery/modules/mod-orders-11.2.0-SNAPSHOT.376
	curl -X DELETE http://localhost:9130/_/discovery/modules/mod-ncip-1.6.4-SNAPSHOT.31
	curl -X DELETE http://localhost:9130/_/discovery/modules/mod-circulation-19.3.0-SNAPSHOT.790

