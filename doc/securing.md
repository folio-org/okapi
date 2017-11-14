# Securing Okapi Installation

This is a quick guide on how to use the FOLIO auth complex to secure an Okapi
installation. We hope that this process will be automated at some point.

## Running the examples

There are many curl examples. As with the Okapi guide, you can extract the
sample data into /tmp and run the curl commands using this one-liner.
First ensure that Okapi and the required modules are ready as explained in these initial sections, then do:

```
perl -n -e 'print if /^```script/../^```$/;' okapi/doc/securing.md | sed '/```/d' | sh -x
```

All the shell commands assume you are in your top-level directory, for example
`~/folio`. All modules are assumed to reside under it, each in its own directory.

## Okapi itself

### Getting Okapi

This script assumes you have a fresh Okapi instance running, with nothing
installed in it. If you do not, you can get one up by running the following.

```
git clone git@github.com:folio-org/okapi.git
cd okapi
#git checkout "v2.0.2"  # Or "master" if you want the latest, or any other tag
mvn clean install
cd ..
```

The build takes a while. (It will go faster if you append `-DskipTests` to the
mvn command line)

Check that near the end there is a line that says

```
[INFO] BUILD SUCCESS
```

### Initializing a fresh database

You need to have a database to store all these things in, and you should start
with clean state.

Create a database user:

```
  sudo -u postgres -i
   createuser -P -s okapi   # When it asks for a password, enter okapi25
   createdb -O okapi okapi
```

Note the -s, that gives the user superuser rights, which are needed in the
tenant interface calls. If you have followed the Okapi guide, you probably
have created the okapi user, but without the superuser bit. You can give the
superuser privs with something like

```
sudo -u postgres psql -c "ALTER USER okapi  WITH SUPERUSER"
```

TODO: Set up the same way as Wayne does. See https://github.com/folio-org/folio-ansible/blob/master/roles/postgresql/tasks/main.yml

<!--
Marc's example:
CREATE ROLE module_admin_user PASSWORD ‘somepassword’ SUPERUSER CREATEDB INHERIT LOGIN;
-->

See the
[Storage](https://github.com/folio-org/okapi/blob/master/doc/guide.md#storage)
section of the Okapi guide for details of setting up your database. Then run

```
java -Dstorage=postgres -jar okapi/okapi-core/target/okapi-core-fat.jar initdatabase
```

### Starting Okapi

```
java -Dstorage=postgres -jar okapi/okapi-core/target/okapi-core-fat.jar dev
```

In the end you should have Okapi running in a console window, and listening on
port 9130. You can verify it is running by listing the tenants:

```script
curl -w '\n' -D - http://localhost:9130/_/proxy/tenants
```

```
HTTP/1.1 200 OK
Content-Type: application/json
X-Okapi-Trace: GET okapi-2.0.1-SNAPSHOT /_/proxy/tenants : 200 450us
Content-Length: 105

[ {
  "id" : "supertenant",
  "name" : "supertenant",
  "description" : "Okapi built-in super tenant"
} ]
```

Note on the curl command line options:
* `-w '\n'` causes curl to output a newline after the operation
* `-D -` causes curl to output the response headers to stdout


## Required modules

This script requires the following modules.
* mod-permissions
* mod-users
* mod-login
* mod-authtoken

The following commands fetch and compile the modules. We use the tip of the
master branches here, but beware, things may change under your feet. You can
add a `git checkout` to get to a known good version.

```
git clone git@github.com:folio-org/mod-permissions.git
cd mod-permissions
git checkout master # "v4.0.4" is way too old
mvn clean install
cd ..

git clone git@github.com:folio-org/mod-users.git
cd mod-users
git checkout master # maybe v14.2.0 works
mvn clean install
cd ..

git clone git@github.com:folio-org/mod-login.git
git checkout master # v3.1.0 is way too old
mvn clean install
cd ..

git clone git@github.com:folio-org/mod-authtoken.git
cd mod-authtoken
git checkout master # v1.1.0
mvn clean install
cd ..
```

## Order of operations

Basically we just need to declare, deploy, and enable the modules declared above,
and load some data into them.

Declaring and deploying the modules can be done in any order, but we have to be
careful with the order of enabling them, and loading data into them. Most of all,
we may not enable mod-authtoken, until the very end, when we have all the other
modules in place and loaded with data, or we risk that mod-authtoken will not let
us finish the process, locking us out of our own system.


### Workaround for a known bug

When a module gets enabled, Okapi loads its permissions into mod-permissions,
if such is already enabled. Unfortunately there is a bug in Okapi, so it will
not reload permissions of already-existing modules when mod-permissions is
enabled for the first time. This makes it necessary for mod-permissions to be
the first module to be installed, but that is not possible, since the internal
module is already there when Okapi starts. So its permissions will never get
into mod-permissions, and thus the superuser will never have the permissions to
use Okapi's admin functions. To work around that problem, we post the permissions
of the internal module into mod-permissions when we set that one up.
See [Okapi-388](https://issues.folio.org/browse/OKAPI-388).
<!-- TODO: Remove this paragraph when -388 gets fixed, and update the Okapi
version above -->

### Declaring the modules

The module descriptors are generated from a template during the build process.

Where ever they come from, we just need to POST them to Okapi.

```script
curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d @mod-permissions/target/ModuleDescriptor.json \
  http://localhost:9130/_/proxy/modules

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d @mod-users/target/ModuleDescriptor.json \
  http://localhost:9130/_/proxy/modules

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d @mod-login/target/ModuleDescriptor.json \
  http://localhost:9130/_/proxy/modules

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d @mod-authtoken/target/ModuleDescriptor.json \
  http://localhost:9130/_/proxy/modules
```

You can verify we have all four modules properly declared:

```script
curl -w '\n' -D -  http://localhost:9130/_/proxy/modules
```

This should list five modules, the 4 declared above, and Okapi's internal module.

### Setting up the database environment

All the modules need to know how to talk to the database. The easiest way is
to set up some environment variables, by POSTing them to Okapi.

```script
curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d '{"name":"DB_HOST", "value":"localhost"}' \
  http://localhost:9130/_/env

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d '{"name":"DB_PORT", "value":"5432"}' \
  http://localhost:9130/_/env

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d '{"name":"DB_USERNAME", "value":"okapi"}' \
  http://localhost:9130/_/env

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d '{"name":"DB_PASSWORD", "value":"okapi25"}' \
  http://localhost:9130/_/env

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d '{"name":"DB_DATABASE", "value":"okapi"}' \
  http://localhost:9130/_/env
```

### Deploying the modules

Now we need to deploy the modules. This is a bit tricky, since the
DeploymentDescriptor needs to refer to the version of the module, and we
do not want to hard code that in this script. Instead we grep the version
out of the `pom.xml` file for each module.

Also note that these commands start with a meaningless-looking cat. It is there
to trigger the script-running one-liner.

#### mod-permissions

```script
export PERMVER=`grep '<version>' mod-permissions/pom.xml | head -1 | sed 's/[^0-9.A-Z-]//g'`
cat > /tmp/deploy-perm.json <<END
{
  "srvcId": "mod-permissions-$PERMVER",
  "nodeId": "localhost",
  "descriptor": {
    "exec": "java -Dport=%p -jar mod-permissions/target/mod-permissions-fat.jar -Dhttp.port=%p"
  }
}
END

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d @/tmp/deploy-perm.json \
  http://localhost:9130/_/discovery/modules
```

#### mod-users

```script
export USERVER=`grep '<version>' mod-users/pom.xml | head -1 | sed 's/[^0-9.A-Z-]//g'`
cat > /tmp/deploy-user.json <<END
{
  "srvcId": "mod-users-$USERVER",
  "nodeId": "localhost",
  "descriptor": {
    "exec": "java -jar mod-users/target/mod-users-fat.jar -Dhttp.port=%p"
  }
}
END

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d @/tmp/deploy-user.json \
  http://localhost:9130/_/discovery/modules
```

#### mod-login

```script
export LOGINVER=`grep '<version>' mod-login/pom.xml | head -1 | sed 's/[^0-9.A-Z-]//g'`
cat > /tmp/deploy-login.json <<END
{
  "srvcId": "mod-login-$LOGINVER",
  "nodeId": "localhost",
  "descriptor": {
    "exec": "java -jar mod-login/target/mod-login-fat.jar -Dhttp.port=%p"
  }
}
END

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d @/tmp/deploy-login.json \
  http://localhost:9130/_/discovery/modules
```

#### mod-authtoken

```script
export AUTHVER=`grep '<version>' mod-authtoken/pom.xml | head -1 | sed 's/[^0-9.A-Z-]//g'`
cat > /tmp/deploy-auth.json <<END
{
  "srvcId": "mod-authtoken-$AUTHVER",
  "nodeId": "localhost",
  "descriptor": {
    "exec": "java -Dport=%p -jar mod-authtoken/target/mod-authtoken-fat.jar -Dhttp.port=%p"
  }
}
END

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -d @/tmp/deploy-auth.json \
  http://localhost:9130/_/discovery/modules
```

You can see all four modules deployed with

```script
curl -w '\n' -D - http://localhost:9130/_/discovery/modules
```

### Enabling modules and loading data

First we need to enable mod-permissions for our supertenant. We do not have to
specify the version number here, Okapi will choose the latest (and only) version
we have declared.

```script
curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
   -d'{"id":"mod-permissions"}' \
   http://localhost:9130/_/proxy/tenants/supertenant/modules
```

#### Workaround: Load the permissions of the Internal Module

Because of bug OKAPI-388, the permissions for the internal module have not
been loaded in the perms module. Re-enabling that will fix that.

```script
export OKAPIVER=`grep '<version>' okapi/pom.xml | head -1 | sed 's/[^0-9.A-Z-]//g'`

cat > /tmp/re-enable.json <<END
{"id":"okapi-$OKAPIVER"}
END

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
   -d@/tmp/re-enable.json \
   http://localhost:9130/_/proxy/tenants/supertenant/modules/okapi-$OKAPIVER
```

#### Create our superuser in the perms module

```script
cat > /tmp/permuser.json <<END
{ "userId":"99999999-9999-9999-9999-999999999999",
  "permissions":[ "okapi.all" ] }
END

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -H "X-Okapi-Tenant:supertenant" \
  -d@/tmp/permuser.json \
   http://localhost:9130/perms/users
```

#### Enable mod-users

```script
curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -H "X-Okapi-Tenant:supertenant" \
  -d'{"id":"mod-users"}' \
  http://localhost:9130/_/proxy/tenants/supertenant/modules
```

Create our superuser in mod-users

```script
cat > /tmp/superuser.json <<END
{ "id":"99999999-9999-9999-9999-999999999999",
  "username":"superuser",
  "personal": {
     "lastName": "Superuser",
     "firstName": "Super"
  }
}
END

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -H "X-Okapi-Tenant:supertenant" \
  -d@/tmp/superuser.json \
   http://localhost:9130/users
```

#### mod-login

Enable the login module

```script
curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -H "X-Okapi-Tenant:supertenant" \
  -d'{"id":"mod-login"}' \
  http://localhost:9130/_/proxy/tenants/supertenant/modules
```

And create a login user

```script
cat >/tmp/loginuser.json << END
{ "username":"superuser",
  "password":"secretpassword" }
END

curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -H "X-Okapi-Tenant:supertenant" \
  -d@/tmp/loginuser.json \
   http://localhost:9130/authn/credentials
```


#### mod-authtoken

When we enable mod-authtoken, the system is finally locked up.

```script
curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -H "X-Okapi-Tenant:supertenant" \
  -d'{"id":"mod-authtoken"}' \
  http://localhost:9130/_/proxy/tenants/supertenant/modules
```

#### Log in

We can reuse the credentials we used for creating the login user.
We need to save the headers in /tmp, so we can extract the auth token

```script
curl -w '\n' -D /tmp/loginheaders -X POST  \
  -H "Content-type: application/json" \
  -H "X-Okapi-Tenant:supertenant" \
  -d@/tmp/loginuser.json \
   http://localhost:9130/authn/login

cat /tmp/loginheaders | grep -i x-okapi-token | sed 's/ //g' > /tmp/token
echo "Got token " `cat /tmp/token`
```

#### Verify that we need a token

Try to create a new tenant, without the token. Should fail.

```script
curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -H "X-Okapi-Tenant:supertenant" \
  -d'{"id":"failure","name":"not permitted"}' \
   http://localhost:9130/_/proxy/tenants
```

#### Verify that the token works

```script
curl -w '\n' -D - -X POST  \
  -H "Content-type: application/json" \
  -H "X-Okapi-Tenant:supertenant" \
  -H `cat /tmp/token` \
  -d'{"id":"testtenant","name":"Simple test"}' \
   http://localhost:9130/_/proxy/tenants
```

#### Verify that we can list the tenants

Most of the read-only operations should be allowed for any user, even
without the token.

```script
curl -w '\n' -D -  \
  -H "X-Okapi-Tenant:supertenant" \
   http://localhost:9130/_/proxy/tenants
```

This should have listed exactly two tenants.

### Cleaning up

If you need to clean up the things you have created, you need to do two things:
Drop all schemas and roles we have created, and drop and recreate the whole
database. Run something like `sudo -u postgres psql okapi` and paste the
following commands into it:

```
DROP SCHEMA supertenant_mod_login CASCADE;
DROP ROLE supertenant_mod_login;

DROP SCHEMA supertenant_mod_users CASCADE;
DROP ROLE supertenant_mod_users;

DROP SCHEMA supertenant_mod_permissions CASCADE;
DROP ROLE supertenant_mod_permissions;
\q
```

Then run the two commands below:

```
sudo -u postgres dropdb okapi
sudo -u postgres createdb -O okapi okapi
```


