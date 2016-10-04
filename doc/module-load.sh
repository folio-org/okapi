#!/bin/bash
#
# A simple script to load a module into Okapi
# Assumes the current directory is the top-level directory of the module
# project.
# Assumes the ModuleDescriptor is in the current directory, or given as the
# first parameter
# If there is a Dockerfile in the current directory, creates the docker image
# first.

# Command line parameters

MODULEDESC=${1:-"./ModuleDescriptor.json"}  # Defines the module to be loaded
OKAPI=${2:-"http://localhost:9130"} # Must be running and listening on that url
TENANT=${3:-"testlib"} # to enable the module for


# Extract module name
if [ ! -f $MODULEDESC ]
then
  echo "No Module Descriptor found in '$MODULEDESC'"
  exit 1
fi

MODID=`grep '"id"' ModuleDescriptor.json  | head -1 | cut -d '"' -f4`

# Create the Docker image
if [ -f Dockerfile ]
then
  docker build -t $MODID .
fi


# Load ModuleDescriptor to Okapi
echo
echo "Declaring module $MODID..."
curl  -D - -w '\n' \
  -X POST \
  -H "Content-type: application/json"  \
  -d @$MODULEDESC \
  $OKAPI/_/proxy/modules

# Deploy the module
# Assumes the ModuleDescriptor did have a LaunchDescriptor in it
# and that we do it on localhost
echo
echo "Deploying the module..."
DEPL=/tmp/module-load-deploy-$MODID
cat >$DEPL <<END
{
  "srvcId" : "$MODID",
  "nodeId" : "localhost"
}
END

curl  -D - -w '\n' \
  -X POST \
  -H "Content-type: application/json"  \
  -d @$DEPL \
  $OKAPI/_/discovery/modules

rm $DEPL

# Check that we have the tenant defined
curl -w '\n' http://localhost:9130/_/tenants/$TENANT | grep $TENANT || (
  echo
  echo "No tenant $TENANT found, creating one"
  TEN=/tmp/module-load-tenant-$TENANTID
  cat >$TEN <<END
    {
      "id" : "$TENANT",
      "name" : "Test Library",
      "description" : "Test library called $TENANT"
    }
END

  curl  -D - -w '\n' \
    -X POST \
    -H "Content-type: application/json"  \
    -d @$TEN \
    $OKAPI/_/proxy/tenants

  echo Created $TENANT

)


# Enable the module for the tenant
echo
echo "Enabling the module '$MODID' for tenant '$TENANT'"
ENAB=/tmp/module-load-enable-$MODID
cat >$ENAB <<END
{
  "id" : "$MODID"
}
END
curl  -D - -w '\n' \
  -X POST \
  -H "Content-type: application/json"  \
  -d @$ENAB \
  $OKAPI/_/proxy/tenants/$TENANT/modules
rm $ENAB


