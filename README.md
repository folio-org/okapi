Okapi — a multitenant API Gateway
=================================

Copyright (C) 2015-2019 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

System requirements
-------------------

The Okapi software has the following compile-time dependencies:

* Java 8

* Apache Maven 3.3.x or higher

In addition, the test suite must be able to bind to ports 9230-9239 to
succeed.

*Note: If tests fail, the API Gateway may be unable in some cases to
shut down microservices that it has spawned, and they may need to be
terminated manually.*

Quick start
-----------

To build and run:

    $ mvn install
    $ mvn exec:exec

Okapi listens on port 9130.

Documentation
-------------

* [Okapi Guide and Reference](doc/guide.md)
* [Documentation index](doc/index.md)
* [Contributing guidelines](CONTRIBUTING.md)
* [Securing](doc/securing.md) in the doc directory shows how to enable
  security-related modules.
* [Folio Sample
Modules](https://github.com/folio-org/folio-sample-modules). There is
a decent README, and some minimal sample modules to get started with
* Other FOLIO Developer documentation is at
  [dev.folio.org](https://dev.folio.org/)
* See project [OKAPI](https://issues.folio.org/browse/OKAPI) at the
[FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker)
