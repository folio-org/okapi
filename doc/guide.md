# Okapi Guide and Reference

This is the guide and reference to the Okapi: a gateway for
managing and running microservices.

## Compilation and Installation

The latest source of the software can be found at
[GitHub](https://github.com/sling-incubator/okapi).

Build Requirements are

 * Apache Maven 3.0.5 or later.
 * Java 8 JDK
 * [Git](https://git-scm.com)

With these available, build with:

```
git clone https://github.com/sling-incubator/okapi.git
cd okapi
mvn install
```

The install rule also runs a few tests. Tests should not fail.
If they do, please report it and turn to `mvn install -DskipTests`.

If successful, the output of `mvn install` should have this line near
the end:
```
[INFO] BUILD SUCCESS
```

The okapi directory contains a few sub modules. These are

 * `okapi-core`: the gateway server
 * `okapi-auth`: a simple module demonstrating authentication
 * `okapi-sample-module`: a module mangling HTTP content

These two modules are used in tests for okapi-core so they must be build
before okapi-core tests are performed.

The result for each module and okapi-core is a combined jar file
with all necessary components combined - including Vert.x. The listening
port is adjusted with property `port`.

For example, to run the okapi-auth module and listen on port 8600, use:

```
cd okapi-auth
java -Dport=8600 -jar target/okapi-auth-fat.jar
```

In the same way, to run the okapi-core, use:
```
cd okapi-core
java -Dport=8600 -jar target/okapi-core-fat.jar
```

A Maven rule to run the gateway, is part of the `pom.xml`, in the
main directory.

```
mvn exec:exec
```
This will start the okapi-core and make it listen on its default port: 9130.

For remote debugging you can use
```
mvn exec:exec@debug
```
This command format requires Maven >= 3.3.1. Will listen for debugging client at port 5005.

## Using Okapi

These examples show how to use Okapi from the command line, using the `curl`
http client. You should be able to copy and paste the command(s) to your
command line from this document.


### Example modules

The Okapi is all about invoking modules, so we need to have a few to play with.
It comes with two dummy modules that demonstrate different things.

#### Okapi-sample-module

Is a very simple module. If you make a GET request to it, it will reply "It
works". If you POST something to it, it will reply with "Hello" followed by
what ever you posted.

Normally Okapi will be starting and stopping these modules for you, but let's
take a quick look at this one, mostly to see how to use curl.

Open a console window, navigate to the okapi project root and issue the command

```
java -jar okapi-sample-module/target/okapi-sample-module-fat.jar

```
This starts the sample module listening on port 8080.


Now open another console window, and try to access the
sample module with

```
curl -w '\n' http://localhost:8080/sample
```
It should tell you that it works. The option "`-w '\n'`" is just to
make curl output an extra newline, because the responses do not necessarily
end in newlines.



Now let's try to POST something to the sample module. In real life this
would be a JSON structure, but for now a simple text string will do.

```
echo "Testing Okapi" > okapi.txt
curl -w '\n' -X POST -d okapi.txt http://localhost:8080/sample

```
Again we have the -w option to get a newline in the output, and this
time we add `-X POST` to make it a post request, and `-d okapi.txt`
to specify what we want to post.

The test module should respond with
    Hello Testing Okapi
which is our test data, with a "Hello" prepended to it.


That is enough about the sample module, go back to the window where you
left it running, and kill it with a Ctrl-C. it should not have produced
any output after the initial messages.



#### Okapi-auth-module

Okapi itself does not do authentication, it delegates that to a module.
We do not have such a module yet, but we have a very trivial dummy
module that can be used to demonstrate how it works.

The dummy module supports two functions: /login is, as its name implies,
a login function that takes a username and password, and if acceptable,
returns a token in a HTTP header. Any other path goes through the check
function that checks that we have a valid token in the HTTP request
headers.  The token is simply the username and tenant-id concatenated
with a checksum.

We will see examples of this when we get to play with Okapi itself. If
you want, you can start the module directly as with the sample module.

TODO - make a real example of this too


### Running Okapi itself

Now we are ready to start Okapi.

```
java -jar okapi-core/target/okapi-core-fat.jar

```
It lists its PID and says it `succeeded deploying verticle`.
That means it is running, and listening on the default port
which happens to be 9130.


At the moment Okapi does not know of any module or tenant. But it does
have its own web services enabled. We can verify both by asking Okapi
to list modules and tenants.
```
curl -w '\n' http://localhost:9130/_/modules
curl -w '\n' http://localhost:9130/_/tenants
```
Both of these return an empty list, as a JSON structure:
    [ ]

### Deploying modules

So we need to tell Okapi that we want to work with some modules.

#### Deploying the sample module
So, we need to tell Okapi that we want to be using the sample module. So we
create a JSON structure, and POST it to Okapi

```
cat > /tmp/samplemodule.json <<END
{
  "name" : "sample-module",
  "descriptor" : {
    "cmdlineStart" : "java -Dport=%p -jar okapi-sample-module/target/okapi-sample-module-fat.jar",
    "cmdlineStop" : null
   },
   "routingEntries" : [ {
      "methods" : [ "GET", "POST" ],
      "path" : "/sample",
      "level" : "30",
      "type" : "request-response"
    } ]
}
END

```
The module descriptor tells Okapi that it needs to start the given process to
deploy the module. The routingEntries tell that the module is interested in
GET and POST requests to the /sample path and nothing else, and that the module
is supposed to provide a full response. The level is used to for finding out the
order in which the request will be sent to multiple modules, as will be seen later.

```
curl -w '\n' -X POST -D - \
  -H "Content-type: Application/Json" \
  --data-binary @/tmp/samplemodule.json  \
  http://localhost:9130/_/modules

```

Note that we need to add the Content-Type header, otherwise curl will try to
be helpful and say something about it being url-encoded, which will confuse
the Java libraries and result in a "500 - Internal Error".

We also added the "-D -" option that makes curl to display all response
headers.

You should see something like this
```
HTTP/1.1 201 Created
Location: /_/modules/sample-module
Content-Length: 0

```


If you repeat the same request, you should now get an error
```
HTTP/1.1 400 Bad Request
Content-Length: 37

module sample-module already deployed

```
which seems quite reasonable.

If you look at the output of
    ps axf | grep -C4 okapi
you should see that okapi-core has spawned a new process for the
okapi-sample-module, and that it has been assigned port 9131. You can try to
access it directly if you like, just as before.

#### Deploying the auth module
This is similar to the sample module. First we create the JSON structure for it:

```
cat > /tmp/authmodule.json <<END
{
  "name" : "auth",
  "descriptor" : {
    "cmdlineStart" : "java -Dport=%p -jar okapi-auth/target/okapi-auth-fat.jar",
    "cmdlineStop" : null
  },
  "routingEntries" : [ {
    "methods" : [ "*" ],
    "path" : "/",
    "level" : "10",
    "type" : "request-response"
  }, {
    "methods" : [ "POST" ],
    "path" : "/login",
    "level" : "20",
    "type" : "request-response"
  } ]
}
END
```

Here we have two routing entries. The second says that this module is
interested in POST requests to the /login path. This is what we use for
actually logging in.

The first routing entry says that this module is interested in seeing
any request at all, and on a pretty low level (10) too, which means that
any request should go through the auth module before being directed to
the module that does the actual work. In this way, supporting modules
like authentication or logging can be tied to some or all requests.

Then we deploy it as before

```
curl -w '\n' -X POST -D - \
  -H "Content-type: Application/Json" \
  --data-binary @/tmp/authmodule.json  \
  http://localhost:9130/_/modules

```
And should see

```
HTTP/1.1 201 Created
Location: /_/modules/auth
Content-Length: 0
```

Now we have two modules, as can be seen with

```
curl -w '\n' -D -  http://localhost:9130/_/modules
```

but we still can not use them. We need to have some
tenants too.

### Creating tenants
For this example we create two tenants. These are simple requests.

```
cat > /tmp/tenant1.json <<END
{
  "name" : "ourlibrary",
  "description" : "Our Own Library"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: Application/Json" \
  --data-binary @/tmp/tenant1.json  \
  http://localhost:9130/_/tenants
```

```
cat > /tmp/tenant2.json <<END
{
  "name" : "otherlibrary",
  "description" : "The Other Library"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: Application/Json" \
  --data-binary @/tmp/tenant2.json  \
  http://localhost:9130/_/tenants
```

Again, we can list them with
```
curl -w '\n' http://localhost:9130/_/tenants
```

### Enabling a module for a tenant

There is still one step before we can use our modules. We need to tell which
tenants have which modules enabled. For our own library we enable the sample
module, without enabling the auth module.

```
cat > /tmp/enabletenant1.json <<END
{
  "module" : "sample-module"
}
END
curl -w '\n' -X POST -D - \
  -H "Content-type: Application/Json" \
  --data-binary @/tmp/enabletenant1.json  \
  http://localhost:9130/_/tenants/ourlibrary/modules
```

### Using a module
Finally we should be able to make use of the module, as a regular tenant.
```
curl -w '\n' -D -  http://localhost:9130/sample
```
But of course Okapi can not know which tenant it is that is wanting to use our
sample module, so it can not allow such, and returns a 403 forbidden.

We need to pass the tenant in our request:
```
curl -w '\n' -D -  \
  -H "X-Okapi-Tenant: ourlibrary" \
  http://localhost:9130/sample
```
and indeed we get back a note saying that it works.


### Enabling both modules for the other tenant

Our other tenant needs to use /sample as well, but it needs to be authenticated
to be allowed to do so. So we need to enable both sample-module and auth for it:

```
cat > /tmp/enabletenant2a.json <<END
{
  "module" : "sample-module"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: Application/Json" \
  --data-binary @/tmp/enabletenant2a.json  \
  http://localhost:9130/_/tenants/otherlibrary/modules

cat > /tmp/enabletenant2b.json <<END
{
  "module" : "auth"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: Application/Json" \
  --data-binary @/tmp/enabletenant2b.json  \
  http://localhost:9130/_/tenants/otherlibrary/modules
```
You can list the enabled modules with
```
curl -w '\n' -D -    \
    -H "X-Okapi-Tenant: otherlibrary"   \
    http://localhost:9130/_/tenants/otherlibrary/modules
```

### Authentication problems

If the other library tries to use our sample module:
```
curl -w '\n' -D -  \
  -H "X-Okapi-Tenant: otherlibrary" \
  http://localhost:9130/sample
```
it fails with
```
HTTP/1.1 401 Unauthorized
Content-Length: 39
X-Okapi-Trace: GET auth:401 42451us
Transfer-Encoding: chunked

Auth.check called without X-Okapi-Token
```

which is true enough. In order to get that token, we need to invoke the
/login service first.


```
cat > /tmp/login.json <<END
{
  "tenant": "otherlibrary",
  "username": "peter",
  "password": "peter36"
}
END
curl -w '\n' -X POST -D - \
  -H "Content-type: Application/Json" \
  -H "X-Okapi-Tenant: otherlibrary" \
  --data-binary @/tmp/login.json  \
  http://localhost:9130/login
```
This returns us a header
    X-Okapi-Token: otherlibrary:peter:ca9b9b7beca02fa9f95dd7a2a6fb65d4

Now we can add that header to the request, and see if things finally work:

```
curl -w '\n' -D -  \
  -H "X-Okapi-Tenant: otherlibrary" \
  -H "X-Okapi-Token: otherlibrary:peter:ca9b9b7beca02fa9f95dd7a2a6fb65d4" \
  http://localhost:9130/sample
```

it works!



## Developing Modules

