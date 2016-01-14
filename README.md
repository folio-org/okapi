# sling
Sling core and modules

Java 8 and Apache Maven 3 are required for compilation.
The test suite must be able to bind to ports 9130 thru 9134 to succeed.

In some cases, if tests fail, the API gateway will not be able to shut down
the spawned micro services. In this case, you'll have to manually kill them.

To build and run:

    $ mvn install
    $ mvn exec:exec

Listens on port 9130.
