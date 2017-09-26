FROM openjdk:8-jre

ENV VERTICLE_FILE okapi-core-fat.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

# Copy your fat jar to the container
COPY target/$VERTICLE_FILE $VERTICLE_HOME/module.jar
COPY docker/docker-entrypoint.sh $VERTICLE_HOME/docker-entrypoint.sh

# Create user/group 'folio'
RUN groupadd folio && \
    useradd -r -d $VERTICLE_HOME -g folio -M folio && \
    chown -R folio.folio $VERTICLE_HOME && \
    chown -R folio.folio ${VERTICLE_HOME}/docker-entrypoint.sh && \
    chmod +x ${VERTICLE_HOME}/docker-entrypoint.sh

# Run as this user
USER folio

# Launch the verticle
WORKDIR $VERTICLE_HOME

# Expose this port locally in the container.
EXPOSE 9130

ENTRYPOINT ["./docker-entrypoint.sh"]
