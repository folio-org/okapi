###
# vert.x docker example using a Java verticle packaged as a fatjar
# To build:
#  docker build -t okapi-test-module .
# To run:
#   docker run -t -i -p 8080:8080 okapi-test-module
###

FROM java:8

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

EXPOSE 8080

# Copy your fat jar to the container
COPY target/*-fat.jar $VERTICLE_HOME/module.jar

# Launch the verticle
WORKDIR $VERTICLE_HOME
ENTRYPOINT ["java", "-jar", "module.jar"]
