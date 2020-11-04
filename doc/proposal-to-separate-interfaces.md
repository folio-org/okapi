# Proposal: separate interfaces from implementations

<!-- md2toc -l 2 proposal-to-separate-interfaces.md -->
* [Conventions](#conventions)
* [Introduction](#introduction)
* [Proposal](#proposal)
    * [Phase 1](#phase-1)
    * [Phase 2](#phase-2)
    * [Phase 3 (optional)](#phase-3-optional)
* [Difficulties](#difficulties)



## Conventions

This document will always set interface names in `fixed width` and implementation names in **bold**. An implementation will be said to _provide_ an interface.



## Introduction

From the earliest designs of the FOLIO system, a conceptual distinction has been made between interfaces (for example, `item-storage`) and the implementations that provide them (for example, **mod-inventory-storage**).

A single implementation may implement multiple interfaces (for example **mod-inventory-storage** provides `item-storage`, `holdings-storage`, `instance-storage` and nearly forty more interfaces!); and a single interface may be provided by multiple alternative implementations (for example, **mod-codex-mux**, **mod-codex-inventory** and **mod-codex-ekb** all implement the `codex` interface).

But this conceptual separation has been badly muddied by the pragmatic choice early in the system design that the interface definition does not have its own existence separate from the implementation. Instead, an interface is specified as part of the `provides` array in a implementation's module descriptor.

This has several undesirable consequences. For example:

* There is no such thing as the canonical definition of an interface. We cannot generate authoritative documentation of our interfaces automatically because, ATM, it does not exist.

* It is therefore not possible to point directly to an interface. The `/settings/developer/okapi-paths` facility in FOLIO would be more useful if it stated what interface each listed path belongs to, and linked to that interface -- but it can't.

* FOLIO client code cannot validate the shapes of the objects they receive because they have no way of looking up the pertinent schemas. A client's package file specifies what interfaces it depends on, but there is no way to get from interface names to JSON schemas, since the schemas are only available as part of the implementation.

The problem is seen most painfully in the case of the Codex modules: although in principle they each implement the same `codex` interface, in practice all three implementations contain their own copy of that interface's definition, and those definitions have drifted apart so that they do not in fact implement exactly the same interface at all! (See the relevant sections in the module descriptors for
[**mod-codex-mux**](https://github.com/folio-org/mod-codex-mux/blob/6ecaf885a6c5d1d62df6ecd4823a81e4c9071ad9/descriptors/ModuleDescriptor-template.json#L6-L29),
[**mod-codex-inventory**](https://github.com/folio-org/mod-codex-inventory/blob/ec2820768026d8878fc3ca547732cb9de4468d48/descriptors/ModuleDescriptor-template.json#L31-L47)
and
[**mod-codex-ekb**](https://github.com/folio-org/mod-codex-ekb/blob/b96fdb09d51bbf689057425c7ec26132a7b7ba6f/descriptors/ModuleDescriptor-template.json#L5-L26).)

This intertwingling of interface and implementation, besides being conceptually confused, has discouraged the creation of alternative implementations and also (as I will argue below) prevented us from thinking clearly about what actually is interface and what is implementation.

The challenge for us is to move to an arrangement where interfaces are separated from their implementations but in a backward-compatible way, so that we don't have to go through an everything-breaks-at-once stage.



## Proposal

At present, the interface definitions are primarily defined by the elements of the `provides` array in an implementation's module descriptor, like this:
```
"provides": [
  {
    "id": "sru",
    "version": "1.0.0",
    "handlers" : [
      {
        "methods" : [ "GET" ],
        "pathPattern" : "/sru",
        "permissionsRequired": []
      }
    ]
  }
],
```

The shortest way to get to where we want to be, would be if the module descriptor instead said something like:
```
"provides": [
  {
    "$ref": "../interfaces/sru.json"
  }
],
```
and the separate file `../interfaces/sru.json` would contain the definition:
```
{
  "id": "sru",
  "version": "1.0.0",
  "handlers" : [
    {
      "methods" : [ "GET" ],
      "pathPattern" : "/sru",
      "permissionsRequired": []
    }
  ]
}
```

That is, if any interface definition in a module descriptor contains a `$ref` element, then the corresponding value is taken to be a [JSON pointer](https://tools.ietf.org/html/rfc6901) to the location of the interface definition. That definition could be a nearby file in the same repository (or indeed elsewhere in the module descriptor itself, though there is little reason to do that). More interestingly, it could point into a separate repository: this would provide a mechanism for the three Codex modules to share a single interface definition: `"$ref": "https://github.com/folio-org/mod-codex-mux/tree/master/interfaces/codex.json"`.

(An earlier version of this proposal suggested using `"provides$ref": ["../interfaces/sru.json"]`, but that would require that either all or none of the interfaces defined in a module descriptor were by reference. The version presented here allows some interfaces to be migrated to referenced files while others remain inline, offering a smoother upgrade path.)


### Phase 1

Phase 1 would be simply to implement support for interfaces by reference in Okapi. This would of course be backwards compatible, continuing to recognise and interpret old module descriptors as previously, so it could be released as a new minor version.


### Phase 2

Phase 2 would occur after a reference-capable release of Okapi has become ubiquitous.

This would be gradual, opportunistic migration of existing modules' module descriptors from the current embedded-interface versions to the new arrangement in which their interfaces are removed and kept in a separate file -- I suggest, by convention, in an `interfaces` directory at the top level of the repository.

As noted above, this could be done one interface at a time, rather than requiring **mod-inventory-storage**, for example, to migrate all forty-something of its interface to external files at once.

(It would probably be pretty simple to create a tool to automatically extract interfaces from a module descriptor, save them to their own files, and modify the module descriptor to include suitable references.)


### Phase 3 (optional)

Once we are confident that most or all modules have completed the migration to separately defined interfaces, Okapi could begin to emit warnings when receiving a module descriptor with inline interface definitions. This would serve as a warning to the maintainers of modules that have not yet migrated their interface definitions.

At some point further down the line, it would be possible to remove support for inline interface definitions entirely. If this were done, it would be a backwards-incompatible change, and so would require a new major release of Okapi.



## Difficulties

All of this sounds very straightforward, but it rather skims over the difficult question of what exactly is included in an interface. The elements of the `provides` array specify method/paths combinations and their required and desired permissions (as well as the interface name and version number) -- but do these constitute the whole of an interface?

Specifically, are permissions part of an interface?

On the face of it, it certainly seems that they are: in **mod-inventory-storage**, the `item-storage` interface declares (among others) a handler for `GET /item-storage/items`, and specifies that the `inventory-storage.items.collection.get` permission is required. This suggests
that we should move the definitions of each permission from the module descriptor into the interface that uses it, so that the interface is self-contained.

But what happens when multiple interfaces require (or desire) the same permission? We certainly would not want to define the same permission in two interface: that would be a repeat, at a lower level, the mistake of definition interfaces within the module descriptors that describe implementations. The solution may be to move permission descriptions into their own files which are in turn referenced from interface definitions, so that multiple interfaces can depend on the same permission. But the proliferation of files is unappealing, and it is not obvious how permissions would be allocated into sets represented by a single file.

(Does this even happen, in fact? Or is it the case that each permission is used in at most once interface? On the face of it, that sounds unlikely, but when I tried to think of examples I came up empty.)

But it is not clear that permissions -- or at least _all_ permissions -- are properly part of the interface. Evidence that some, at least, should be considered part of an implementation can be found once more from the example of the three Codex modules. **mod-codex-mux** [defines `GET /codex-instances` as requiring the permission `codex-mux.instances.collection.get`](https://github.com/folio-org/mod-codex-mux/blob/6ecaf885a6c5d1d62df6ecd4823a81e4c9071ad9/descriptors/ModuleDescriptor-template.json#L10-L15), whereas **mod-codex-ekb** [defines the same operation as requiring `codex-ekb.instances.collection.get`](https://github.com/folio-org/mod-codex-ekb/blob/b96fdb09d51bbf689057425c7ec26132a7b7ba6f/descriptors/ModuleDescriptor-template.json#L10-L15). (Whether by accident or design, **mod-codex-inventory** [requires no permissions for this operation](https://github.com/folio-org/mod-codex-inventory/blob/ec2820768026d8878fc3ca547732cb9de4468d48/descriptors/ModuleDescriptor-template.json#L36-L40).) It doesn't seem unreasonable that different Codex back-end modules might require different permissions even though they implement the same interface.

So perhaps the right solution is for both interfaces _and_ implementations to be able to define permissions?

In fact, part of the value of thinking about the separation of interfaces and implementation is that it forces us to start taking seriously these conceptual issues about exactly what an interface is.



