# Requirements for FOLIO modules

<!-- md2toc -l 2 module-requirements.md -->
* [Introduction](#introduction)
* [1. To run under Okapi](#1-to-run-under-okapi)
* [2. To run on Index Data/EBSCO CI](#2-to-run-on-index-dataebsco-ci)
* [3. Best practice](#3-best-practice)
* [See also](#see-also)


## Introduction

In the early days of FOLIO, most server-side modules were written in Java, taking advantage of facilities provided by [the RAML Module Builder (RMB)](https://github.com/folio-org/raml-module-builder). As a result, much of modules' behaviour was uniform by default and by convention, which made it easy for Okapi to run them and easy for CI systems to configure and deploy them. But more recently, other modules are being created, such as [mod-graphql](https://github.com/folio-org/mod-graphql) which is written in [Node.js](https://nodejs.org/en/).

As we start to build more FOLIO modules, not all of them based on RMB – and as we start to see third-parties creating modules in potentially any language using any tools -– we need to tersely and explicitly summarise what requirements a piece of software must meet to function as a FOLIO module.

This document summarises these requirements in three sections:
1. What you need to do to run under Okapi.
2. What you need to do to run on Index Data/EBSCO CI.
3. Best practice (health-check WSAPIs, etc.)

A piece of software that meets the first set of requirements is a FOLIO module, but not necessarily one that works well in deployments. Software that also meets the second set of requirements can be started and run by the initial set of [Continuous Integration](https://en.wikipedia.org/wiki/Continuous_integration) environments. The best FOLIO modules will also implement the optional best practices described in the third section.

**Note.**
This document is intended to be short, so that is is easy to comply and to assess compliance. Therefore it does not discuss more general software-engineering best practices such as version control, GitHub flow, use of code-quality tools, versioning, release procedures, etc.


## 1. To run under Okapi

* Provide a web service
* Do not use the `/_/` space in the web service.
* XXX What else?


## 2. To run on Index Data/EBSCO CI

* Provide a `ModuleDescriptor.json`
* Provide an `ExternalDeploymentDescriptor.json`
* Provide a `TenantAssociationDescriptor.json`
* Provide health-check endpoint `/admin/health`. Should have responce code `200`
* XXX What else?


## 3. Best practice

* Provide a `Dockerfile`
* Provide a `Jenkinsfile`
* Provide a `.travis.yml`
* XXX What else?


## See also

* [OKAPI-564](https://issues.folio.org/browse/OKAPI-564), the issue to write this document.
* [STCOR-180](https://issues.folio.org/browse/STCOR-180), the issue to write a similar document for client-side modules.


