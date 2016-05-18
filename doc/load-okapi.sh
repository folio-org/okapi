#!/bin/bash
#
# A simple script to put some load on Okapi
# Assumes we have set things up as in the guide, or via okapi-examples.sh
#

cat > /tmp/okapi.tst <<END
Simple POST request to the sample module
END

while true
do

  for I in 1 2 3 4 5 6 7 8 9 10
  do

    curl -s -w '\n' -D -  \
      -H "X-Okapi-Tenant: other" \
      -H "X-Okapi-Token: other:peter:04415268d4170e95ec497077ad4cba3c" \
      http://localhost:9130/sample

    curl -s -w '\n' -D -  \
      -H "Content-type: application/json" \
      -H "X-Okapi-Tenant: other" \
      -H "X-Okapi-Token: other:peter:04415268d4170e95ec497077ad4cba3c" \
      -X POST -d @/tmp/okapi.tst \
      http://localhost:9130/sample

  done

  curl -s -w '\n' -D -  \
    -H "X-Okapi-Tenant: our" \
    http://localhost:9130/UNKNOWN-REQUEST

  curl -s -w '\n' -D -  \
    -H "X-Okapi-Tenant: other" \
    -H "X-Okapi-Token: other:peter:BAD-TOKEN" \
    http://localhost:9130/sample

  sleep 0.01

done

