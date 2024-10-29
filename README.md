# Okapi — a multitenant API Gateway

Copyright (C) 2015-2024 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## System requirements

The Okapi software has the following compile-time dependencies:

* Java 17

* Apache Maven 3.8.x or higher

The test suite has these additional dependencies:

* Docker, for details see https://java.testcontainers.org/supported_docker_environment/

* Ports 9230-9239 must be free

*Note: If tests fail, the API Gateway may be unable in some cases to
shut down microservices that it has spawned, and they may need to be
terminated manually.*

## Quick start

Before buliding, make sure your `$JAVA_HOME` environment variable is set correctly.

This can be done by one of the two ways described below:

1. For e.g., on Debain(and Debian-based) distros, run the following command to set the
`$JAVA_HOME` for the current session:

```
  $ export JAVA_HOME=`readlink -f /usr/bin/javac | sed "s:bin/javac::"`
```


2. Or, you can add the following at the end of the ``~/.profile`` for persistently setting ``$JAVA_HOME`` :

```
  $ echo export JAVA_HOME=`readlink -f /usr/bin/javac | sed "s:bin/javac::"` >> ~/.profile
```

Then run:

```
  $ source ~/.profile
```


You may need to log out and log in again or reboot for these changes to take effect.


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

See [Automation/Docker Hub](https://dev.folio.org/guides/automation/#docker-hub) for details.

Docker images are the primary distribution model for FOLIO modules.
To run the images you will need the Docker Engine or Docker Desktop
runtime.

## Issue tracker

See project [OKAPI](https://issues.folio.org/browse/OKAPI) at the
[FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker)

## Documentation

* [Okapi Guide and Reference](doc/guide.md)
* API: [RAML and schemas](okapi-core/src/main/raml) and generated [API documentation](https://dev.folio.org/reference/api/#okapi)
* [Documentation index](doc/index.md)
* [Contributing guidelines](CONTRIBUTING.md)
* [Securing](doc/securing.md) in the doc directory shows how to enable
  security-related modules.
* [Folio Sample Modules](https://github.com/folio-org/folio-sample-modules). There is
a decent README, and some minimal sample modules to get started with
* Other FOLIO Developer documentation is at
  [dev.folio.org](https://dev.folio.org/)

## Code analysis

[SonarQube analysis](https://sonarcloud.io/project/overview?id=org.folio.okapi%3Aokapi)
