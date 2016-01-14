

Okapi — a multitenant API Gateway
=================================


System requirements
-------------------

The Okapi software has the following compile-time dependencies:

* Java 8

* Apache Maven 3

In addition, the test suite must be able to bind to ports 9130-9134 to succeed.

*Note: If tests fail, the API Gateway may be unable in some cases to shut down
microservices that it has spawned, and they may need to be terminated
manually.*


Starting Okapi
--------------

To build and run:

    $ mvn install
    $ mvn exec:exec

Okapi listens on port 9130.


