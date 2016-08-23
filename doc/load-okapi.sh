#!/bin/bash
#
# A simple script to put some load on Okapi
# Assumes we have set things up as in the guide, or via okapi-examples.sh
#
OKAPI=${1:-"http://localhost:9130"}
MODULE=${2:-'testb'} # module to invoke, default: okapi-test-module (testb)

temp_file='/tmp/okapi-load.txt'

function cleanup {
  rm -f $temp_file
}
trap cleanup EXIT

cat > $temp_file <<END
Simple POST request to the $MODULE module.
END

while true
do

  for I in 1 2 3 4 5 6 7 8 9 10
  do

    curl -s -w '\n' -D -  \
      -H "X-Okapi-Tenant: testlib" \
      -H "X-Okapi-Token: testlib:peter:6f9e37fbe472e570a7e5b4b0a28140f8" \
      $OKAPI/$MODULE

    curl -s -w '\n' -D -  \
      -H "Content-type: application/json" \
      -H "X-Okapi-Tenant: testlib" \
      -H "X-Okapi-Token: testlib:peter:6f9e37fbe472e570a7e5b4b0a28140f8" \
      -X POST -d @$temp_file \
      $OKAPI/$MODULE

  done


  curl -s -w '\n' -D -  \
    -H "X-Okapi-Tenant: testlib" \
    -H "X-Okapi-Token: other:peter:BAD-TOKEN" \
    $OKAPI/$MODULE

  sleep 0.1
  date

done
