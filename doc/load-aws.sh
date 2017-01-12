#!/bin/bash
#
# A script to put different kind of loads on our AWS installation, to feed some
# numbers to Grafana.

# Assumes we have set things up with okapi-examples, or similar
#OKAPI="http://localhost:9130"
OKAPI="http://okapi-test.aws.indexdata.com:9130"
#OKAPI="http://okapi-test1.aws.indexdata.com:9130"

MODULE=${1:-'testb'}  # module to invoke, default: okapi-test-module (testb)
TENANT=${2:-'testlib'}     # tenant to invoke for
SLEEP=${3:-1}       # delay between invocations (sec)
TOKEN=${4:-"X-Okapi-Token: testlib:peter:6f9e37fbe472e570a7e5b4b0a28140f8"}
#echo " M='$MODULE' T='$TENANT' S='$SLEEP' T='$TOKEN'"

# Stop file - touch this to stop all instances of this script
STOP=aws.stop
rm -f $STOP

while [ ! -f $STOP ]
do

  echo
  date
  curl  -w '\n' -D - \
    -H "X-Okapi-Tenant: $TENANT" \
    -H "$TOKEN" \
    $OKAPI/$MODULE

  sleep $SLEEP

done
