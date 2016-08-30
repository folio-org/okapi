###
# vert.x docker example using a Java verticle packaged as a fatjar
# To build:
#  docker build -t okapi-test-module .
# To run:
#   docker run -t -i -p 8080:8080 okapi-test-module
###

FROM java:8

ENV VERTICLE_FILE okapi-test-module-fat.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

EXPOSE 8080

# Copy your fat jar to the container
COPY target/$VERTICLE_FILE $VERTICLE_HOME/

# Launch the verticle
WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["java -jar $VERTICLE_FILE"]
