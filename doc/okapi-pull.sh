#!/bin/sh
O=http://localhost:9130
U=${1:-http://folio-registry.aws.indexdata.com:80}
echo "{\"urls\" : [ \"$U\" ]}" >p.json
curl -s -D - -XPOST -H "Content-Type: application/json" -d@p.json $O/_/proxy/pull/modules

