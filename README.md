# sling
Sling core and modules

Java 8 and Maven/mvn are required for compilation.
The test suite must be able to bind to ports 9130 thru 9133 to succeed.

In some cases, if tests fail, the API gateway will not be able to shut down
the spawned micro services. In this case, you'll have to manually kill them.

