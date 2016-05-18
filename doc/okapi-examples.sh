#!/bin/bash
#
# Simple script to set up Okapi with modules, tenants, etc
# Pasted from the guide.md

# You should be in the main okapi directory, typically ~/proj/okapi


#########
# Okapi itself


# Start up Okapi itself - in a different console window:
# java -jar okapi-core/target/okapi-core-fat.jar dev

# See that Okapi is running
curl -w '\n' http://localhost:9130/_/proxy/tenants


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
  http://localhost:9130/_/deployment/modules

# Get deployment details for the sample
curl -s -o /tmp/samplediscovery.json \
  http://localhost:9130/_/deployment/modules/localhost-9131

# And post it to the discovery
curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/samplediscovery.json  \
  http://localhost:9130/_/discovery/modules

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

curl -w '\n' -X POST -D -   \
    -H "Content-type: application/json"   \
    -d @/tmp/sampleproxy.json \
   http://localhost:9130/_/proxy/modules


#########
# The auth module

cat > /tmp/authdeploy.json <<END
{
  "srvcId" : "auth",
  "descriptor" : {
    "exec" : "java -Dport=%p -jar okapi-auth/target/okapi-auth-fat.jar"
   }
}
END

curl -w '\n' -D - -s \
  -X POST \
  -o /tmp/authdiscovery.json \
  -H "Content-type: application/json" \
  -d @/tmp/authdeploy.json  \
  http://localhost:9130/_/deployment/modules


curl -w '\n' -D -  \
  -X POST \
  -H "Content-type: application/json" \
  -d @/tmp/authdiscovery.json  \
  http://localhost:9130/_/discovery/modules

cat > /tmp/authmodule.json <<END
{
  "id" : "auth",
  "name" : "auth",
  "provides" : [ {
    "id" : "auth",
    "version" : "3.4.5"
  } ],
  "requires" : [ {
    "id" : "sample",
    "version" : "1.2.0"
  } ],
  "routingEntries" : [ {
    "methods" : [ "*" ],
    "path" : "/",
    "level" : "10",
    "type" : "request-response"
  }, {
    "methods" : [ "POST" ],
    "path" : "/login",
    "level" : "20",
    "type" : "request-response"
  } ]
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/authmodule.json  \
  http://localhost:9130/_/proxy/modules


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
  http://localhost:9130/_/proxy/tenants

cat > /tmp/tenant2.json <<END
{
  "id" : "other",
  "name" : "otherlibrary",
  "description" : "The Other Library"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/tenant2.json  \
  http://localhost:9130/_/proxy/tenants

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
  http://localhost:9130/_/proxy/tenants/our/modules

curl -w '\n' -D -  \
  -H "X-Okapi-Tenant: our" \
  http://localhost:9130/sample


################
# Enabling sample and auth for tenant 2

cat > /tmp/enabletenant2a.json <<END
{
  "id" : "sample-module"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/enabletenant2a.json  \
  http://localhost:9130/_/proxy/tenants/other/modules

cat > /tmp/enabletenant2b.json <<END
{
  "id" : "auth"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/enabletenant2b.json  \
  http://localhost:9130/_/proxy/tenants/other/modules

# Logging in as tenant2
cat > /tmp/login.json <<END
{
  "tenant": "other",
  "username": "peter",
  "password": "peter-password"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -H "X-Okapi-Tenant: other" \
  -d @/tmp/login.json  \
  http://localhost:9130/login


curl -w '\n' -D -  \
  -H "X-Okapi-Tenant: other" \
  -H "X-Okapi-Token: other:peter:04415268d4170e95ec497077ad4cba3c" \
  http://localhost:9130/sample