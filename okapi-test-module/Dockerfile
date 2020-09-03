###
# vert.x docker example using a Java verticle packaged as a fatjar
# To build:
#   docker build -t okapi-test-module .
# To run directly:
#   docker run -t -i -p 8080:8080 okapi-test-module
# To let okapi run it:
#   curl -w '\n' -D - -d @descriptors/ModuleDescriptor-dockerImage.json http://localhost:9130/_/proxy/modules
#   curl -w '\n' -D - -d '{"srvcId":"test-basic-1.0.0", "nodeId":"localhost"}' http://localhost:9130/_/discovery/modules
###

FROM folioci/alpine-jre-openjdk11:latest

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

EXPOSE 8080

# Copy your fat jar to the container
COPY target/*-fat.jar $VERTICLE_HOME/module.jar

# Launch the verticle
WORKDIR $VERTICLE_HOME
ENTRYPOINT ["java", "-jar", "module.jar"]
