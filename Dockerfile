# Bootstrap (build and run) okapi core with Maven in a Docker
# container.  e.g. 'mvn install; mvn exec:exec'
#
# Build image:  'docker build -t okapi .'
# Start container: 'docker run -d --name okapi -p 9130:9130 okapi'
#
# Use for development or testing.  Not suitable for production

FROM maven:3.3.3-jdk-8-onbuild
CMD ["mvn","exec:exec"]

# okapi core
EXPOSE 9130
