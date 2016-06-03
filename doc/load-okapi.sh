#!/bin/bash
#
# A simple script to put some load on Okapi
# Assumes we have set things up as in the guide, or via okapi-examples.sh
#
OKAPI=${1:-"http://localhost:9130"}

cat > /tmp/okapi.tst <<END
Simple POST request to the sample module
END


while true
do

  for I in 1 2 3 4 5 6 7 8 9 10
  do

    curl -s -w '\n' -D -  \
      -H "X-Okapi-Tenant: our" \
      $OKAPI/sample

    curl -s -w '\n' -D -  \
      -H "X-Okapi-Tenant: other" \
      -H "X-Okapi-Token: other:peter:04415268d4170e95ec497077ad4cba3c" \
      $OKAPI/sample

    curl -s -w '\n' -D -  \
      -H "Content-type: application/json" \
      -H "X-Okapi-Tenant: other" \
      -H "X-Okapi-Token: other:peter:04415268d4170e95ec497077ad4cba3c" \
      -X POST -d @/tmp/okapi.tst \
      $OKAPI/sample

  done

  curl -s -w '\n' -D -  \
    -H "X-Okapi-Tenant: our" \
    $OKAPI/UNKNOWN-REQUEST


  curl -s -w '\n' -D -  \
    -H "X-Okapi-Tenant: other" \
    -H "X-Okapi-Token: other:peter:BAD-TOKEN" \
    $OKAPI/sample

  sleep 0.1
  date

done

