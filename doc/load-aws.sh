#!/bin/bash
#
# A script to put different kind of loads on our AWS installation, to feed some
# numbers to Grafana

# Assumes we have set things up with okapi-examples, or similar
#OKAPI="http://localhost:9130"
OKAPI="http://okapi-test.aws.indexdata.com:9130"
#OKAPI="http://okapi-test1.aws.indexdata.com:9130"


MODULE=${1:-sample}  # module to invoke
TENANT=${2:-our}     # tenant to invoke for
SLEEP=${3:-1}       # delay between invocations (sec)
TOKEN=${4:-"X-Okapi-Token: other:peter:04415268d4170e95ec497077ad4cba3c"}
#echo " M='$MODULE' T='$TENANT' S='$SLEEP' T='$TOKEN'"

# Stop file - touch this to stop all instances of this script
STOP=aws.stop
rm -f $STOP


while [ ! -f $STOP ]
do

  echo
  date
  curl  -w '\n' -D -  \
      -H "X-Okapi-Tenant: $TENANT" \
      -H "$TOKEN" \
      $OKAPI/$MODULE

  sleep $SLEEP

done
