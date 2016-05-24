# A simple loop that deploys and undeploys a module

cat > /tmp/sample2deploy.json <<END
{
  "srvcId" : "sample-loop",
  "descriptor" : {
    "exec" : "java -Dport=%p -jar okapi-sample-module/target/okapi-sample-module-fat.jar"
   }
}
END

while true
do

  # deploy sample2
  curl -s -w '\n' -X POST -D - \
    -H "Content-type: application/json" \
    -d @/tmp/sample2deploy.json  \
    http://localhost:9130/_/deployment/modules

  # Delete that sample2 again
  curl -s -w '\n' -X DELETE -D - \
    http://localhost:9130/_/deployment/modules/localhost-9133

  sleep 0.1

done
