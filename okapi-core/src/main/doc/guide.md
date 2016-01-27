# OKAPI Guide and Reference

This is the guide and reference to the OKAPI: a gateway for
managing and running microservices.

## Compilation and Installation

The latest source of the software can be found at
[GitHub](https://github.com/sling-incubator/okapi).

Build Requirements are

 * Apache Maven 3.0.5 or later.
 * Java 8 JDK
 * [Git](https://git-scm.com)

With these available, build with:

```
git clone https://github.com/sling-incubator/okapi.git
cd okapi
mvn install
```

The install rule also runs a few tests. Tests should not fail.
If they do, please report it and turn to `mvn install -DskipTests`.

The okapi directory contains a few sub modules. These are

 * `okapi-core`: the gateway server
 * `okapi-auth`: a simple module demonstrating authentication
 * `okapi-sample-module`: a module mangling HTTP content

These two modules are used in tests for okapi-core so they must be build
before okapi-core tests are performed.

The result for each module and okapi-core is a combined jar file
with all necessary components combined - including Vert.x. The listening
port is adjusted with property `port`.

For example, to run the okapi-auth module and listen on port 8600, use:

```
cd okapi-auth
java -Dport=8600 -jar target/okapi-auth-fat.jar
```

In the same way, to run the okapi-core, use:
```
cd okapi-core
java -Dport=8600 -jar target/okapi-core-fat.jar
```

A Maven rule to run the gateway, is part of the `pom.xml`, in the
main directory.

```
mvn exec:exec
```
This will start the okapi-core and make it listen on its default port: 9130.

## Using OKAPI

## Developing Modules

