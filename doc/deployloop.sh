#!/bin/bash
#
# A simple loop that deploys and undeploys a module
#
# Assumes we have set things up as in the guide, or via okapi-examples.sh
# Hence 9133 is next available port.

temp_file='/tmp/okapi-deploy-loop.txt'

function cleanup {
  rm -f "${temp_file}"
  curl -s -w '\n' -X DELETE -D - \
    http://localhost:9130/_/deployment/modules/localhost-9133
}
trap cleanup EXIT

cat > "${temp_file}" <<END
{
  "srvcId" : "test-module-loop",
  "descriptor" : {
    "exec" : "java -Dport=%p -jar okapi-test-module/target/okapi-test-module-fat.jar"
   }
}
END

while true
do

  # deploy the test module
  curl -s -w '\n' -X POST -D - \
    -H "Content-type: application/json" \
    -d @"${temp_file}"  \
    http://localhost:9130/_/deployment/modules

  # Delete that test module again
  curl -s -w '\n' -X DELETE -D - \
    http://localhost:9130/_/deployment/modules/localhost-9133

  sleep 0.1

done
