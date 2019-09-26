## To compile
```
mvn clean install
```
## To run
```
java -jar target/*-fat.jar
```
## To run with Docker
Containers for Okapi are built on the [default folio docker image](https://github.com/folio-org/folio-tools/tree/master/folio-java-docker/). Java options can be passed to Okapi at runtime using the `JAVA_OPTIONS` environment variable. Arguments passed to the container will be passed to Okapi. For example, the command below sets the log_level option in the `JAVA_OPTIONS` variable, and passes the `dev` argument to start Okapi in development mode.
```
docker run -p 9130:9130 -e JAVA_OPTIONS="-Dloglevel=DEBUG" \
  folioorg/okapi dev
```
More information on using the default base image is available in the [folio-tools](https://github.com/folio-org/folio-tools/tree/master/folio-java-docker/openjdk8#startup-script-run-javash) repository.