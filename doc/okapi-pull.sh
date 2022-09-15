#!/bin/sh
O=http://localhost:9130
U=${1:-https://folio-registry.dev.folio.org}
echo "{\"urls\" : [ \"$U\" ]}" >p.json
curl -s -D - -XPOST -H "Content-Type: application/json" -d@p.json $O/_/proxy/pull/modules

