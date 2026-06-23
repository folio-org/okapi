FROM folioci/alpine-jre-openjdk21:latest

# Install latest patch versions of packages: https://pythonspeed.com/articles/security-updates-in-docker/
USER root
RUN apk upgrade --no-cache
USER folio

# Copy your fat jar to the container
COPY okapi-core/target/okapi-core-fat.jar /usr/verticles/

# Expose this port locally in the container.
EXPOSE 9130
