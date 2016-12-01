#!/bin/sh
#
# Demonstrate gh 133


#########
# Okapi itself

OKAPI="http://localhost:9130"  # The usual place it runs on a single-machine setup

# Start up Okapi itself - in a different console window:
# java -jar okapi-core/target/okapi-core-fat.jar dev


########
# Deploy the sample module

cat > /tmp/sampledeploy.json <<END
{
  "srvcId" : "sample-module",
  "descriptor" : {
    "exec" : "java -Dport=%p -jar okapi-sample-module/target/okapi-sample-module-fat.jar"
   }
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/sampledeploy.json  \
  $OKAPI/_/deployment/modules

# Get deployment details for the sample
curl -s -o /tmp/samplediscovery.json \
  $OKAPI/_/deployment/modules/localhost-9131

# And tell the proxy about it
cat > /tmp/sampleproxy.json <<END
  {
    "id" : "sample-module",
    "name" : "okapi sample module",
    "provides" : [ {
      "id" : "sample",
      "version" : "1.2.3"
    } ],
    "routingEntries" : [ {
      "methods" : [ "GET", "POST" ],
      "path" : "/sample",
      "level" : "30",
      "type" : "request-response"
    } ]
  }
END

curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/sampleproxy.json \
  $OKAPI/_/proxy/modules

#############
# Tenants

cat > /tmp/tenant1.json <<END
{
  "id" : "our",
  "name" : "our library",
  "description" : "Our Own Library"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/tenant1.json  \
  $OKAPI/_/proxy/tenants

###########
# Enabling the sample for tenant1

cat > /tmp/enabletenant1.json <<END
{
  "id" : "sample-module"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/enabletenant1.json  \
  $OKAPI/_/proxy/tenants/our/modules

curl -w '\n' -D -  \
  -H "X-Okapi-Tenant: our" \
  $OKAPI/sample

