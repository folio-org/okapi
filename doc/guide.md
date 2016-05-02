# Okapi Guide and Reference

This is the guide and reference to Okapi: a gateway for
managing and running microservices.

## Table of Contents

* [Introduction](#introduction)
* [Architecture](#architecture)
* [Compiling and Running](#compiling-and-running)
* [Using Okapi](#using-okapi)
* [Reference](#reference)

## Introduction

This document aims to provide an overview of concepts that relate to Okapi and
the entire ecosystem around it (e.g core vs modules) as well as details of the 
implementation and usage of Okapi: by presenting concrete web service
endpoints and details of request processing - handling of request and
response entities, status codes, error conditions, etc.

Okapi is an implementation of a couple different patterns commonly used within
the microservice architecture. The most central of them is the so called "API
Gateway" pattern which is implemented by the core Okapi 'proxy' service. 
Conceptually, the API Gateway is a server that is a single entry point into 
the system. It is similar to the [Facade pattern](http://en.wikipedia.org/wiki/Facade_pattern) from object-oriented design. Per the [standard
definition](https://www.nginx.com/blog/building-microservices-using-an-api-gateway/),
which Okapi follows quite closely, _the API Gateway encapsulates the
internal system architecture and provides a unified API that may be
tailored to each client; it might also include core responsibilities
such as authentication, monitoring, load balancing, caching, request
shaping and management, and static response handling_: from the Message
Queue design pattern to allow broadcasting of requests to multiple
services (initially synchronously and eventually, possibly,
asynchronously) and returning a final response. Finally, Okapi
facilitates communication between services by acting as a Service
Discovery tool: service A wanting to talk to service B only needs to
know its HTTP interface since Okapi will inspect the registry of
available services to locate the physical instance of the service.

Okapi is designed to be configurable and extensible - it allows one to
expose new, or enrich existing, web service endpoints without a need
for programmatic changes to the software itself. Registration of new
services ('modules' as seen form Okapi) happens by making calls to the Okapi 
core web services. It is envisioned that the registration, and associated core
management tasks, will be performed by the Service Provider
administrator. This configurability and extensibility is necessary to
allow for app store features in which services or groups of services
('applications') can be enabled or disabled per tenant on demand.

## Architecture

Web service endpoints in Okapi can be, roughly, divided into two
parts: (1) general modules and tenant management APIs, sometimes
referred to as 'core' - initially part of Okapi itself but potentially
separable into their own services  - and (2) endpoints for accessing
module-provided, business-logic specific interfaces, e.g. Patron
management or Circulation. This document will discuss the former in
detail and offer a general overview of allowed formats and styles for
the latter.

The specification of the core Okapi web services, in its current form,
is captured in [RAML](http://raml.org/) (RESTful API Modeling
Language). See the [Reference](#Reference) section.  The
specification, however, aims to make very few assumptions about the
actual API endpoints exposed by specific modules, which are basically
left undefined.  The goal is to allow for different styles and formats
of those APIs (RESTful vs RPC and JSON vs XML, etc.) with only the
basic requirement of a common transport protocol (HTTP). It is
envisioned that the transport protocol assumption may be lifted or
worked around for some special cases (e.g. the ability to integrate
non-HTTP, binary protocols, such as a truly asynchronous protocol for
operation similar to a message queue).

### Okapi's own Web Services

As mentioned, Okapi's own web services provide the basic functionality
to set up, configure and enable modules and manage tenants. The core
endpoints are:

 * `/_/deployment`
 * `/_/discovery`
 * `/_/proxy`

The special prefix `/_` is used to to distinguish the routing for Okapi
internal web services from the extension points provided by modules.

The `/_/deployment` endpoint is responsible for deploying modules.
In a clustered environment there should be one instance of it running on
every node. It will be responsible for starting processes on that node,
and allocating network addresses for the various service modules.

The `/_/discovery` endpoint manages the mapping from service IDs to network
addresses on the cluster. Information is posted to it, and the proxy service
will query it to find where the needed modules are actually available.

The `/_/proxy` endpoint is for configuring the proxying service, including
which modules we know of, how their requests are to be routed, which tenants
we know about, and which modules are enabled for which tenants.

These three parts are coded as separate services, so that it will be possible
to use alternative deployment and discovery methods, if the chosen clustering
system offers such.


#### What are 'modules'?

Modules in the Okapi ecosystem are defined in terms of their _behavior_ (or, in other words, _interface contract_)  rather than their _contents_, 
meaning there is no exact definition of a module as a package or an archive, 
e.g with the underlying file structure standardized. 
Those details are left to the particular module implementation (as noted before, Okapi server-side modules can utilize any technology stack). 

As much any piece of software that manifests the following traits can become 
an Okapi module:

* it is an HTTP network server that communicates using a REST-styled web service protocol with JSON payload

* it comes with a descriptor file, namely the `ModuleDescriptor.json`, which 
declares the basic module meta-data (id, name, etc), module's dependencies 
on other modules (interface identifiers to be precise) and reports all 
"provided" interfaces

* `ModuleDescriptor.json` has a list off all `routes` (HTTP paths and methods) 
that a given module handles, this gives Okapi necessary information to proxy traffic to the module (this is similar to a simplified RAML specification)

* it follows versioning rules defined in the chapter _Versioning and dependencies_

* WIP: it provides interfaces required for monitoring and instrumentation

As you can see none of those requirements specifically state rules for 
deployment and, as such, it would be entirely possible to integrate 
a third party web service (e.g a  API of a publicly accessible internet service) as an Okapi module. That is, assuming the endpoint style and versioning 
semantics are a close match for what is required in Okapi.

Okapi, however, includes additional services (for service deployment and  discovery) that allows to actually execute, run and monitor services natively on a cluster managed by Okapi. Those _native modules_ require an additional descriptor 
file, the `DeploymentDescriptor.json`, that captures all low-level information
about how to run the module. Also, `native modules` must be packaged according
to one of the packaging options supported by Okapi's deployment service: at 
this point it means providing the executable (and all dependencies) on each 
node or using on a self-contained Docker image to distribute the executable
from a centralized place.


#### API guidelines

Okapi's own web services must, and other modules should, adhere to these
guidelines as far as practically possible.

 * No trailing slashes
 * Always expect and return proper JSON objects, or lists of such
 * The primary key should always be called 'id'

We try to make the Okapi code exemplary, so that it would serve well as
an example for other module developers to emulate.


#### Core Okapi Web Service Authentication and Authorization

Access to the core services (all resources under the `/_/` path) is
granted to the Service Provider (SP) administrator, as the
functionality provided by those services spans multiple tenants. The
details of authentication and authorization of the SP administrators
are to be defined at a later stage and will most likely be provided by
an external module that can hook into a specific Service Provider
authn/authz system.

### Deployment and Discovery

Getting a service available for a tenant is a multi-step process:

 * The module gets deployed. That means, a process is started on some nodes
that offers a web service on some network address.
 * The service id and network address are POSTed to the discovery module, so
Okapi can find out where the service is running.
 * The service ID and routing entries are posted to the proxy module, so Okapi
can know where to route incoming requests
 * The service ID is enabled for some tenants.

We assume some external management program will be making these requests.  It
can not be a proper Okapi module itself, because it needs to be running before
any modules have been deployed. For testing, see the curl command-line examples
later in this document.

### Request Processing

Any number of modules can request registration on a single URI
path. Okapi will then forward the requests to those modules in an
order controlled by the level integer setting in the module
registration configuration.

Although Okapi accepts both HTTP 1.0 and HTTP 1.1 requests, it uses HTTP 1.1 with
chunked encoding to make the connections to the modules.

We envision that different kinds of modules will carry different level
values: e.g. authentication and authorization will have the highest
possible priority, next the actual business logic processing unit,
followed by metrics, statistics, monitoring, logging, etc.

The module metadata also controls how the request is forwarded to
consecutive modules in a pipeline and how the responses are being
processed. Currently, we have three kinds of request processing by
modules (controlled by the type parameter in the module registration
configuration). The possible values are:

 * `headers` - the module is interested in headers/parameters only,
and it can inspect them and perform an action based on the
presence/absence of headers/parameters and their corresponding
value. The module is not expected to return any entity in the
response, but only a status code to control the further chain of
execution or, in the case of an error, an immediate termination. The
module may return certain response headers that will be merged into
the complete response header list according to the header manipulation
rules below.

 * `request-only` - the module is interested in the full client
request: header/parameters and the entity body attached to the
request. It does not produce a modified version or a new entity in the
response but performs an associated action and returns optional
headers and a status code to indicate further processing or
termination. In cases when an entity is returned, Okapi will discard
it and continue forwarding the original request body to the subsequent
modules in the pipeline.

 * `request-response` - the module is interested in both
headers/parameters and the request body. It is also expected that the
module will return an entity in the response. This may be e.g. a
modified request body, in which case the module acts as a filter. The
returned response may then be forwarded on to the subsequent modules
as the new request body. Again, the chain of processing or termination
is controlled via the response status codes, and the response headers
are merged back into the complete response using the rules described
below.

### Status Codes

Continuation or termination of the pipeline is controlled by a status
code returned by an executed module. Standard [HTTP status
code](https://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html) ranges
are accepted in Okapi:

 * 2xx range: OK return codes; if a code in this range is
returned by a module, Okapi continues execution of the pipeline and
forwards information to the consecutive modules according to the rules
described above.

 * 3xx range: Redirect codes. The pipeline is terminated.

 * 4xx-5xx range: user request errors or internal system errors; if a
code in this range is returned by a module, Okapi immediately
terminates the entire chain and returns the code back to the caller.

### Header Merging Rules

Since Okapi forwards the response from a previous module on to the
next module (e.g.  for additional filtering/processing) in the
pipeline, certain initial request headers become invalid - e.g. when a
module converts the entity to a different content type or changes its
size. Invalid headers need to be updated, based on the module’s
response header values, before the request can be forwarded to the
next module. At the same time Okapi collects response headers in order
to produce a final response that is sent back to the original client
when the processing pipeline completes.

Both sets are modified according to the following rules:

 * Any headers that provide metadata about the request entity body
(e.g.  Content-Type, Content-Length, etc.) are merged from the last
response back into the request.

 * An additional set of special debug and monitoring headers is merged
from the last response into the current request (in order to forward
them to the next module).

 * A list of headers that provide metadata about the response entity
body is merged to the final response header set.

 * An additional set of special headers (debug, monitoring) or any
other headers that should be visible in the final response is merged
into the final response header set.

### Versioning and Dependencies

Modules can provide one or more interfaces, and can consume interfaces
provided by other modules. The interfaces have versions, and dependencies
can require given versions. Okapi will check dependencies and versions
whenever a module is deployed, and also when a module is enabled for a tenant.

Note that we can have multiple modules providing the same interface. These
can be deployed in Okapi all right, but only one such module can be enabled
for any given tenant at a given time. For example, we can have two ways to
manage our patrons, one based on a local database, one talking to an external
system. The installation can know both, but each tenant must choose one or
the other.


#### Version numbers

We use a 3-part versioning scheme, like 3.1.41

The first number is the major version of the interface. It needs to be
incremented whenever making a change that is not strictly backwards
compatible, for example removing functionality or changing semantics.
Okapi will require that the major version number matches exactly what
is required.

The second number is the minor version of the interface. It needs to be
incremented whenever compatible changes are made, for example adding new
functionality or optional fields.  Okapi will check that the module
providing the service does provide at least the required minor number.

The third number is the software version. It should be incremented on changes
that do not affect the interface, for example fixing bugs.

If a module requires an interface 3.1.41, it will accept:
* 3.1.41  - same version
* 3.1.68  - same interface, later software version
* 3.2.8   - Higher minor version, compatible interfaces.

But it will reject:
* 2.2.2   - Different major version
* 4.4.4   - Different major version
* 3.0.99  - Lesser minor version
* 3.1.27  - Too small software version


### Open Issues

#### Security

There is extensive work going on in parallel to Okapi development, to
establish and define security requirements for the entire SLING
platform.

Generally, within a microservice architecture, each module can decide
to handle authentication and authorization separately. Obviously, this
means a lot of duplication, and so a better option is to use Okapi to
serve as a protection between the modules and the outside world, as
well as between the modules themselves, and to provide a Single Sign
On (SSO) facilities. As such, Okapi may be able to provide fairly
effective coarse-grained authentication, e.g. it may prevent access to
modules from non- authenticated users.

For authorization, it is common to place users in groups or assign
them roles. Okapi may, optionally, be used to allow/disallow access to
a module based on a role, but finer grained permissions may be left to
the module itself. For example a staff member may be able to access a
specific module – but what they can actually do within the module
(their specific role) is up to the module.  Making that the
responsibility of the gateway would probably result in a system that
is difficult to manage.

Even though within the SLING perimeter we can assume a certain level
of trust between modules, for module-to-module authentication and
authorization, we will still want Okapi to serve as a watchdog to
prevent the services from escalating their privileges or engaging in
malicious behavior. We are investigating various
authentication/authorization mechanisms as potential candidates to
include in Okapi, such as OpenID Connect, SAML and HMAC-over-HTTP.

#### Caching

Okapi can provide an additional caching layer between modules,
especially in busy, read-heavy, multi-module pipelines. We plan to
follow standard HTTP mechanisms and semantics in this respect, and
implementation details will be established within the coming months.

### Instrumentation and Analytics

In a microservices architecture, monitoring is key to ensure robustness
and health of the entire system. The way to provide useful monitoring
is to include well defined instrumentation points (“hooks”) before and
after each step of execution of the request processing
pipeline. Besides monitoring, instrumentation is crucial for the
ability to quickly diagnose issues in the running system (“hot”
debugging) and discovering performance bottlenecks (profiling). We are
looking at established solutions in this regard: e.g. JMX,
Dropwizard Metrics, Graphite, etc.

A multi-module system may provide a wide variety of metrics and an
immense amount of measurement data. Only a fraction of this data can
be analyzed at runtime, most of it must be captured for analysis at a
later stage. Capturing and storing data in a form that lends itself to
an effortless post factum analysis is essential for analytics and we
are looking into integration between open and popular solutions and
Okapi.

#### Response Aggregation

There is no direct support for response aggregation in Okapi at the
moment, as Okapi assumes sequential execution of the pipeline and
forwards the last response to the next module in the pipeline. In this
mode, it is entirely possible to implement an aggregation module that
will communicate with multiple modules (via Okapi, to retain the
provided authentication and service discovery) and combine the
responses. In further releases a more generic approach to response
aggregation will be evaluated.

#### Asynchronous messaging

At present, Okapi assumes and implements HTTP as the transport
protocol between modules, both on the front-end and within the
system. HTTP is based on a request-response paradigm and does not
directly include asynchronous messaging capabilities.  It is, however,
entirely possible to model an asynchronous mode of operation on top of
HTTP, e.g. using a polling approach or HTTP extensions like
websockets. We anticipate that for future releases of Okapi we will
investigate the asynchronous approach in depth and provide support for
some open messaging protocols (e.g. STOMP).

## Implementation

We have a rudimentary implementation of Okapi in place. The examples below
are supposed to work with the current implementation.

### Missing features

 * Header merging


## Compiling and Running

The latest source of the software can be found at
[GitHub](https://github.com/sling-incubator/okapi). At the moment the
repository is not publicly visible.

Build Requirements are

 * Apache Maven 3.1.1 or later.
 * Java 8 JDK
 * [Git](https://git-scm.com)

With these available, build with:

```
git clone git@github.com:sling-incubator/okapi.git
cd okapi
mvn install
```

The install rule also runs a few tests. Tests should not fail.
If they do, please report it and in the meantime fall back to
```
mvn install -DskipTests
```

If successful, the output of `mvn install` should have this line near
the end:
```
[INFO] BUILD SUCCESS
```

The okapi directory contains a few sub modules. These are

 * `okapi-core`: the gateway server itself
 * `okapi-auth`: a simple module demonstrating authentication
 * `okapi-sample-module`: a module mangling HTTP content

These two modules are used in tests for okapi-core so they must be built
before okapi-core tests are performed.

The result for each module and okapi-core is a combined jar file
with all necessary components combined - including Vert.x. The listening
port is adjusted with property `port`.

For example, to run the okapi-auth module and listen on port 8600, use:

```
cd okapi-auth
java -Dport=8600 -jar target/okapi-auth-fat.jar
```

In the same way, to run the okapi-core, supply its jar file. We must also
add a command as argument which tells what okapi-core must do. When playing
with okapi on a single node, we use the `dev` mode.

```
cd okapi-core
java -Dport=8600 -jar target/okapi-core-fat.jar dev
```

There are other commands available. Supply `help` to get a description of
these.

A Maven rule to run the gateway is part of the `pom.xml`, in the
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

These examples show how to use Okapi from the command-line, using the `curl`
http client. You should be able to copy and paste the commands to your
command-line from this document.

The exact definition of the services is in the RAML files listed in
the [Reference](#reference) section.

## Storage
The Okapi defaults to an internal in-memory mock storage, so it can run without
any database layer under it. This is fine for development and testing, but of
course in real life we will want some of our data to persist from one invocation
to the next. At the moment the MongoDB storage can be enabled by adding the
option `-Dstorage=mongo` to the command-line that starts Okapi.


### Example modules

Okapi is all about invoking modules, so we need to have a few to play with.
It comes with two dummy modules that demonstrate different things.

#### Okapi-sample-module

This is a very simple module. If you make a GET request to it, it will reply "It
works". If you POST something to it, it will reply with "Hello" followed by
whatever you posted.

Normally Okapi will be starting and stopping these modules for you, but we will
run this one directly for now -- mostly to see how to use curl, a
command-line HTTP client that is useful for testing.

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



Now we will try to POST something to the sample module. In real life this
would be a JSON structure, but for now a simple text string will do.

```
echo "Testing Okapi" > okapi.txt
curl -w '\n' -X POST -d @okapi.txt http://localhost:8080/sample
```

Again we have the -w option to get a newline in the output, and this
time we add `-X POST` to make it a post request, and `-d @okapi.txt`
to specify the name of the file containing the data that we want to
post.

The test module should respond with

    Hello Testing Okapi

which is our test data, with a "Hello" prepended to it.

That is enough about the sample module. Go back to the window where you
left it running, and kill it with a Ctrl-C. It should not have produced
any output after the initial messages.



#### Okapi-auth-module

Okapi itself does not do authentication: it delegates that to a
module.  We do not have a functional authentication module yet, but we
have a trivial dummy module that can be used to demonstrate how it
works.

The dummy module supports two functions: /login is, as its name implies,
a login function that takes a username and password, and if acceptable,
returns a token in a HTTP header. Any other path goes through the check
function that checks that we have a valid token in the HTTP request
headers.  The token, for this dummy module, is simply the username and
tenant-id concatenated with a checksum. In a real authentication
module it will be something opaque and difficult to fake.

We will see examples of this when we get to play with Okapi itself. If
you want, you can start the module directly as with the sample module.

### Running Okapi itself

Now we are ready to start Okapi.
Note: for this example to work it is important that the current directory
of the Okapi is the top-level directory `.../okapi`.

```
java -jar okapi-core/target/okapi-core-fat.jar dev
```

The `dev` command tells to run it in development mode, which makes it start
with a known clean state without any modules or tenants defined.

Okapi lists its PID (process ID) and says it `succeeded deploying verticle`.
That means it is running, and listening on the default port
which happens to be 9130, and using the 'inmemory' storage. For MongoDB
storage, add `-Dstorage=mongo` to the command-line.

At the moment Okapi does not know of any module or tenant. But it does
have its own web services enabled. We can verify both by asking Okapi
to list modules and tenants.
```
curl -w '\n' http://localhost:9130/_/proxy/modules
curl -w '\n' http://localhost:9130/_/proxy/tenants
```
Both of these return lists in the form of JSON structures. At present,
because we have just started running, it is an empty list in both
cases:

    [ ]

### Deploying Modules

So we need to tell Okapi that we want to work with some modules. In real life
these operations would be carried out by a properly authorized administrator.

As mentioned above, the process consists of three parts: deployment, discovery,
and configuring the proxying.

#### Deploying the sample module

To tell Okapi that we want to use the sample module, we create a JSON
structure of module metadata and POST it to Okapi


```
cat > /tmp/sampledeploy.json <<END
{
  "srvcId" : "sample-module",
  "name" : "okapi sample module",
  "descriptor" : {
    "cmdlineStart" : "java -Dport=%p -jar okapi-sample-module/target/okapi-sample-module-fat.jar",
    "cmdlineStop" : null
   }
}
END
```

The module descriptor tells Okapi that it needs to start the given
process to deploy the module.

Now we will deploy the module:

```
curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/sampledeploy.json  \
  http://localhost:9130/_/deployment/modules
```

Note that we need to add the Content-Type header, otherwise curl will try to
be helpful and say something about it being url-encoded, which will confuse
the Java libraries and result in a "500 - Internal Error".

We also added the "-D -" option to make curl display all response
headers, and a "-w '\n'" option for visual clarity, it makes curl output
an extra newline after the response.

You should see something like this
```
HTTP/1.1 201 Created
Content-Type: application/json
Location: /_/deployment/modules/localhost-9131
Content-Length: 306

{
  "instId" : "localhost-9131",
  "srvcId" : "sample-module",
  "name" : "okapi sample module",
  "nodeId" : "localhost",
  "url" : "http://localhost:9131",
  "descriptor" : {
    "cmdlineStart" : "java -Dport=%p -jar okapi-sample-module/target/okapi-sample-module-fat.jar",
    "cmdlineStop" : null
  }
}
```

Okapi has started the process and has given it an instance ID (instId) which is
part of the Location header. Like other RESTful services the Location header can
be used to identify the resource later.

If you look at the output of
    ps axf | grep okapi
you should see that okapi-core has spawned a new process for the
okapi-sample-module, and that it has been assigned port 9131.

You can ask Okapi to list deployed modules:
```
curl -D -  -w '\n'  http://localhost:9130/_/deployment/modules

HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 310

[ {
  "instId" : "localhost-9131",
  "srvcId" : "sample-module",
  "name" : "okapi sample module",
  "nodeId" : "localhost",
  "url" : "http://localhost:9131",
  "descriptor" : {
    "cmdlineStart" : "java -Dport=%p -jar okapi-sample-module/target/okapi-sample-module-fat.jar",
    "cmdlineStop" : null
  }
} ]
```

Note that Okapi has added an instId and a url. You can check that the URL points
to the running module:
```
curl -D -  -w '\n' http://localhost:9131/sample

HTTP/1.1 200 OK
Content-Type: text/plain
Content-Length: 8

It works
```

If we were running in a clustered environment, this step should be repeated
for each node that should run the sample module. But we run these examples on
a single machine setup, so it does not matter.

#### Adding the sample module to the discovery
next we need to POST information about the sample module to the discovery service.
The structure we post is actually the same as for the deployment, but now
the important fields are the url and both instId and srvcId.
We could have saved the result of
the deployment POST, but we can also ask Okapi to repeat it for us:
```
curl -s -o /tmp/samplediscovery.json \
  http://localhost:9130/_/deployment/modules/localhost-9131

cat /tmp/samplediscovery.json

{
  "instId" : "localhost-9131",
  "srvcId" : "sample-module",
  "name" : "okapi sample module",
  "nodeId" : "localhost",
  "url" : "http://localhost:9131",
  "descriptor" : {
    "cmdlineStart" : "java -Dport=%p -jar okapi-sample-module/target/okapi-sample-module-fat.jar",
    "cmdlineStop" : null
  }
}
```

So, we go ahead and post it:
```
curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/samplediscovery.json  \
  http://localhost:9130/_/discovery/modules
```


#### Telling the proxy about the module
Finally we need to inform the proxy module that we have a sample module that can
be enabled for tenants. The proxy is interested in the service identifier (srvcId), so
we pass "sample-module" to it.

```
cat > /tmp/sampleproxy.json <<END
  {
    "id" : "sample-module",
    "name" : "okapi sample module",
    "provides" : [ {
      "id" : "sample",
      "version" : "1.2.3"
    } ],
    "routingEntries" : [ {
      "methods" : [ "GET", "POST" ],
      "path" : "/sample",
      "level" : "30",
      "type" : "request-response"
    } ]
  }
END

curl -w '\n' -X POST -D -   \
    -H "Content-type: application/json"   \
    -d @/tmp/sampleproxy.json \
   http://localhost:9130/_/proxy/modules

HTTP/1.1 201 Created
Content-Type: application/json
Location: /_/proxy/modules/sample-module
Content-Length: 297

{
  "id" : "sample-module",
  "name" : "okapi sample module",
    "provides" : [ {
      "id" : "sample",
      "version" : "1.2.3"
    } ],
  "requires" : null,
  "routingEntries" : [ {
    "methods" : [ "GET", "POST" ],
    "path" : "/sample",
    "level" : "30",
    "type" : "request-response"
  } ]
}

```

The routingEntries tell that the module is interested in GET and POST
requests to the /sample path and nothing else, and that the module is
supposed to provide a full response. The level is used to to specify
the order in which the request will be sent to multiple modules, as will
be seen later.


#### Deploying the auth module
The first steps are very similar to the sample module. First we deploy the
module, then we tell the discovery where it lives:
```
cat > /tmp/authdeploy.json <<END
{
  "srvcId" : "auth",
  "name" : "auth",
  "descriptor" : {
    "cmdlineStart" : "java -Dport=%p -jar okapi-auth/target/okapi-auth-fat.jar",
    "cmdlineStop" : null
   }
}
END

curl -w '\n' -D - -s \
  -X POST \
  -o /tmp/authdiscovery.json \
  -H "Content-type: application/json" \
  -d @/tmp/authdeploy.json  \
  http://localhost:9130/_/deployment/modules

cat /tmp/authdiscovery.json

curl -w '\n' -D -  \
  -X POST \
  -H "Content-type: application/json" \
  -d @/tmp/authdiscovery.json  \
  http://localhost:9130/_/discovery/modules

```

Finally we tell the proxying module about it. This is a bit different, we add
version dependencies and more routing info:
```
cat > /tmp/authmodule.json <<END
{
  "id" : "auth",
  "name" : "auth",
  "provides" : [ {
    "id" : "auth",
    "version" : "3.4.5"
  } ],
  "requires" : [ {
    "id" : "sample",
    "version" : "1.2.0"
  } ],
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

For the sake of an example, we specify that the auth module requires
the sample module to be available, and at least version 1.2.0. You can
try to see what happens if you require different versions, like 0.9.9,
1.1.0, 1.3.9, or 2.0.1.

Here we have two routing entries. The second says that this module is
interested in POST requests to the /login path. This is what we use for
actually logging in.

The first routing entry says that this module is interested in seeing
any request at all, and on a pretty low level too (10), which means
that any request should go through the auth module before being
directed to a higher-level module that does the actual work. In this
way, supporting modules like authentication or logging can be tied to
some or all requests.

Then we post it as before:

```
curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/authmodule.json  \
  http://localhost:9130/_/proxy/modules
```
And should see

```
HTTP/1.1 201 Created
Location: /_/proxy/modules/auth
Content-Length: 415

{
 "id" : "auth",
  "name" : "auth",
  "url" : null,
  "provides" : [ {
    "id" : "auth",
    "version" : "3.4.5"
  } ],
  "requires" : [ {
    "id" : "sample",
    "version" : "1.2.0"
  } ],
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
```

Now we have two modules, as can be seen with

```
curl -w '\n' http://localhost:9130/_/proxy/modules
```

but we still can not use them in the way that they would be used in a real
system. Since each invocation of a module is on behalf of a tenant,
we need to create some tenants too.

### Creating tenants
For this example we create two tenants. These are simple requests.

```
cat > /tmp/tenant1.json <<END
{
  "id" : "our",
  "name" : "our library",
  "description" : "Our Own Library"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/tenant1.json  \
  http://localhost:9130/_/proxy/tenants
```

Okapi responds with
```
HTTP/1.1 201 Created
Location: /_/tenants/our
Content-Length: 81

{
  "id" : "our",
  "name" : "our library",
  "description" : "Our Own Library"
}
```

And the second tenant is similar.

```
cat > /tmp/tenant2.json <<END
{
  "id" : "other",
  "name" : "otherlibrary",
  "description" : "The Other Library"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/tenant2.json  \
  http://localhost:9130/_/proxy/tenants
```

Again, we can list them with
```
curl -w '\n' http://localhost:9130/_/proxy/tenants
```

We can now get information for one of these again.
```
curl -w '\n' http://localhost:9130/_/proxy/tenants/our
```

### Enabling a module for a tenant

There is still one step before we can use our modules. We need to specify which
tenants have which modules enabled. For our own library we enable the sample
module, without enabling the auth module.

```
cat > /tmp/enabletenant1.json <<END
{
  "id" : "sample-module"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/enabletenant1.json  \
  http://localhost:9130/_/proxy/tenants/our/modules
```

Note that we are using a RESTful approach here: the URL
`http://localhost:9130/_/proxy/tenants/our/modules` names the set of
modules that are enabled for our library, and we POST a deployed
module to this set. The tenant for which to enable the module is in the URL;
the module to be enabled is in the payload.

Now we can ask Okapi which modules are enabled for our tenant, and get
back a JSON list:

````
curl -w '\n' http://localhost:9130/_/proxy/tenants/our/modules
````

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
  -H "X-Okapi-Tenant: our" \
  http://localhost:9130/sample
```
and indeed the sample module says that _it works_.

### Enabling both modules for the other tenant

Our other tenant needs to use /sample as well, but it needs to be authenticated
to be allowed to do that. So we need to enable both sample-module and auth for it:

```
cat > /tmp/enabletenant2a.json <<END
{
  "id" : "sample-module"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/enabletenant2a.json  \
  http://localhost:9130/_/proxy/tenants/other/modules

cat > /tmp/enabletenant2b.json <<END
{
  "id" : "auth"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -d @/tmp/enabletenant2b.json  \
  http://localhost:9130/_/proxy/tenants/other/modules
```
You can list the enabled modules with
```
curl -w '\n' -D - http://localhost:9130/_/proxy/tenants/other/modules
```

### Authentication problems

If the other library tries to use our sample module:
```
curl -w '\n' -D -  \
  -H "X-Okapi-Tenant: other" \
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

Why does this happen? The other library has the auth module enabled,
and that module intercepts _all_ requests (by means of the
routingEntry whose path is `/` and whose level is 10). As a result,
the auth module is invoked before the sample module. And the auth
module causes the request to be rejected unless it contains a suitable
`X-Okapi-Token`.

In order to get that token, we need to invoke the /login service
first.

```
cat > /tmp/login.json <<END
{
  "tenant": "other",
  "username": "peter",
  "password": "peter-password"
}
END

curl -w '\n' -X POST -D - \
  -H "Content-type: application/json" \
  -H "X-Okapi-Tenant: other" \
  -d @/tmp/login.json  \
  http://localhost:9130/login
```

At present, any username is accepted so long as the password is that
username with "-password" appended. Obviously a real authentication
module would look up the username/password pair in a user register.

When successful, /login echoes the login parameters as its response;
but more importantly, it also returns a header containing an
authentication token:

    X-Okapi-Token: other:peter:04415268d4170e95ec497077ad4cba3c

Now we can add that header to the request, and see if things finally work:

```
curl -w '\n' -D -  \
  -H "X-Okapi-Tenant: other" \
  -H "X-Okapi-Token: other:peter:04415268d4170e95ec497077ad4cba3c" \
  http://localhost:9130/sample
```

it works!

Okapi also supports PUT requests to modify existing modules and tenants.
These are left as an exercise for the reader.

### Cleaning up
Now we can clean up some things
```
curl -X DELETE -w '\n'  -D - http://localhost:9130/_/proxy/modules/sample-module
curl -X DELETE -w '\n'  -D - http://localhost:9130/_/proxy/modules/auth
curl -X DELETE -w '\n'  -D - http://localhost:9130/_/proxy/tenants/our
curl -X DELETE -w '\n'  -D - http://localhost:9130/_/proxy/tenants/other
curl -X DELETE -w '\n'  -D - http://localhost:9130/_/discovery/modules/auth/localhost-9132
curl -X DELETE -w '\n'  -D - http://localhost:9130/_/discovery/modules/sample-module/localhost-9131
curl -X DELETE -w '\n'  -D - http://localhost:9130/_/deployment/modules/localhost-9132
curl -X DELETE -w '\n'  -D - http://localhost:9130/_/deployment/modules/localhost-9131

```
Okapi responds to each of these with a simple
```
HTTP/1.1 204 No Content
Content-Length: 0
```
Finally we can stop the Okapi instance we had running, with a simple Ctrl-C.

## Reference

### Okapi program

The Okapi program is shipped as a bundled jar (okapi-core-fat.jar). The
general invocation is:

  `java` [*java-options*] `-jar patho/okapi-core-fat.jar` *command* [*options*]

This is really just java(1) material. Of particular interest is java-option `-D`
which may set properties for the program. Okapi parses *command* and *options*
that follow.

Okapi requires exactly one command to be given. These are `dev` (for running in
development, single-node mode), `cluster` (for running in clustered
mode/production), `help` and others. Use `help` command to get the latest
commands and options.

### Web Service
The Okapi service requests (all those prefixed with /_/) are specified
in the [RAML](http://raml.org/) syntax.

  * [okapi.raml](../okapi-core/src/main/raml/okapi.raml)
  * [RAML and included files](../okapi-core/src/main/raml)

