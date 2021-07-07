# Okapi — a multitenant API Gateway

Copyright (C) 2015-2021 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## System requirements

The Okapi software has the following compile-time dependencies:

* Java 11

* Apache Maven 3.3.x or higher

The test suite has these additional dependencies:

* Docker, for details see https://www.testcontainers.org/supported_docker_environment/

* Ports 9230-9239 must be free

*Note: If tests fail, the API Gateway may be unable in some cases to
shut down microservices that it has spawned, and they may need to be
terminated manually.*

## Quick start

To build and run:

    $ mvn install
    $ mvn exec:exec

Okapi listens on port 9130.

To build without running the test suite:

    $ mvn install -DskipTests

## Developers

When running unit tests, property `testStorage` controls what storage
to use. It has a default value of `inmemory,postgres,mongo`.
Tests will complete faster by specifying one storage type only.

For example:

    $ mvn -DtestStorage=inmemory install

When filing bug reports with unit test output, always use Maven
option `-B` to avoid control characters in output.

## Docker image

At Docker Hub:

* https://hub.docker.com/r/folioorg/okapi released versions
* https://hub.docker.com/r/folioci/okapi snapshot versions

See [Automation/Docker
Hub](https://dev.folio.org/guides/automation/#docker-hub) for details.

Docker images are the primary distribution model for FOLIO modules.
To run the images you will need the Docker Engine or Docker Desktop
runtime.

## Ubuntu package

Import the FOLIO signing key and add the [FOLIO apt
repository](https://repository.folio.org/packages/ubuntu/) for
Ubuntu 20.04 LTS (Focal Fossa):

    wget -q -O - https://repository.folio.org/packages/debian/folio-apt-archive-key.asc | sudo apt-key add -
    sudo add-apt-repository "deb https://repository.folio.org/packages/ubuntu/ focal/"

## okapi-curl-env

[okapi-curl-env](https://github.com/folio-org/folio-tools/tree/master/okapi-curl-env) is a shell script
that wraps the curl command line with some sugar to make it easier to interact with Okapi.

## Documentation

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
