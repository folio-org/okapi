# Contribution guidelines for Okapi

## Git and branches

We are trying to follow "GitHub Flow" - see
[http://scottchacon.com/2011/08/31/github-flow.html]()

In short, the master branch is always the head of latest development. Anything
merged into master should be of such good quality that at any time a snapshot
from master passes all tests, and can be deployed. That is not to say that it
will be free of bugs, we are not superhuman.

All real work should be done in feature branches. It is still OK to make a
small trivial change directly in the master. Stuff like editing the README.


### Feature branches

Feature branches should be branched off from the master. The naming of those
is not strict, but if you start a branch to fix issue okapi-xxx filed in
[https://issues.folio.org/]() , you might well call the
branch _okapi-xxx_:

    git checkout -b okapi-xxx

You can commit stuff as you go, but try not push obviously broken stuff into
GitHub, not even in your development branch - that will be visible for the
whole world, and we plan to set up automatic testing for each branch, so
pushing a broken commit will cause some emails. But if you need to share the
code, for example for collaborating, of course you need to push it. Naturally
you will write decent commit messages explaining what you have done.

The first time you want to push your branch to GitHub, you may encounter an
error _The current branch okapi-xxx has no upstream branch_. Git tells you
what to do, namely

    git push --set-upstream origin okapi-xxx

Once you have done that, a simple `git push` will be sufficient.

While developing your own branch, pull in the master every now and then, and
resolve any conflicts that may be there. If you don't, your branch
will diverge further from master, and the final merge of your work
back into master will be difficult to resolve.

When you are all done, pull master in again, to make sure your branch merges
cleanly and passes all tests. Commit the merge, and push to your branch

    git push

You may also want to `git rebase` your branch, compressing multiple commits
into one, and editing the commit messages.

### Requesting a merge

Go to the GitHub page, and it shows some recently pushed branches, yours should
be there too. Next to it is a button "Compare and Pull Request". Click on that.
It should show you that it is _able to merge_, so click on the "Create Pull
Request" button under the comment box. Once the request is created, assign it
to someone else.


### Merging pull requests

When someone has assigned a pull request to you, check out the branch, and
look at the git log, and the code, and decide whether all is good.
You can also look at the commit messages and code changes in GitHub.

If there are small details, you can fix them yourself, commit and push to the
branch. If there are serious issues, you can close the pull request without
merging, with a comment explaining why you could not do it.

Once all is well, you can use GitHub's interface. Just go to the
conversation tab, and click on _Merge Pull Request_, edit the comment if
necessary, and click on _Confirm Merge_. GitHub should tell you that the
_Pull request successfully merged and closed_. Next to it is a button to
_Delete the Branch_. For a simple feature branch, you might as well delete
it now, it has served its purpose. But if you think there is more work that
should be done in this branch, of course you don't delete it.

This merging can also be done on the command line, if you prefer
(TODO - Describe how)

When done, you probably want to delete the local branch from your own machine

    git branch -d okapi-xxx

(TODO - Describe the automatic testing, when it is up and running)

## Releasing

The exact procedure for making a release is not yet specified. It is likely to
be something like we are doing for other software:

* Freeze the master for a short while
* Make a release branch
* Make changes specific for this release in the branch
* Tag a version
* Package and release it

Later, if there are bugs in the released version, work can continue on the
version branch, and we can release a new minor version from the branch. Some
changes may be cherry-picked from the master, or from the version branch to the
master, as need be.

## Version numbers

Since (almost?) all our stuff is web services, we need to keep two kind of
version numbers, one for the API, and one for the implementation code. To
make matters worse, a module may implement several interfaces.


### API versions

The API versions are the most important. They are two-part numbers, as in 3.14.

The rules are simple:

* If you only add things to the interface, you increment the minor number,
  because the API is backwards compatible.
* If you remove or change anything, you must increment the major number, because
  now your API is no longer backwards compatible.

For example, you can add a new function to 3.14, and call it 3.15. Any module
that requires 3.14 can also use 3.15. But if you remove anything from the API,
or change the meaning of a parameter, you need to bump the API version to 4.1.

### Implementation versions

The module versions are in 3 parts, like 3.14.15

* The last part should be incremented if you haven't changed anything in the API,
  but only fixed bugs, etc.
* The middle part should be incremented if you added something to the API, and
  incremented the minor API version.
* The major part should be incremented if you made a backwards incompatible change
  to the API, and incremented its major number.


### The simple case

In the most simple case, a module implements just one interface. Then we can
keep the two versions in sync, so module version 3.14.01 implements the interface
3.14, and the last part shows that this is the first version that does so.

### Complex cases

A module can implement more than one interface, and more than one major version
of any of them. In that case the version numbering is necessarily more complex.
There does not have to be any correlation between the module version and the
version of the interfaces it implements.

For example, if the circulation module version 3.14.1 can implement the checkout
API version 1.4 and the checkin API version 2.7. The rules are still the same:

* If you don't change any API, increment the last part to 3.14.2
* If you add new features to _any_ API, increment the middle part to 3.15.1
* If you make any backwards incompatible change to _any_ API, or drop _any_
  API at all, increment the module version to 4.1.1

The most common case is probably when we need to add a new, incompatible API
to a module, but want to keep the old one too. In such cases we only increment
the module version to 3.15.1, but mark that it provides the API versions 3.14
and 4.1.

### Dot one, not zero
The version numbers that end in zero are special, they are reserved for filing
issues for the next version. If you have a small thing you want to add next
time we change the minor version, label the issue with something like v3.15.0
or even v4.0.0 if the change is not backwards compatible. When the change gets
implemented, we bump the version number to 3.15.1 or 4.1.1.

Do not release modules with a version number ending in `.0`.


## Coding style

For Java code, we basically try to adhere to Sun Java coding
[conventions](http://www.oracle.com/technetwork/java/codeconvtoc-136057.html)
(That document is old and unmaintained, but seems to be good enough as it is)

There are a few exceptions:

* We indent with two spaces only, because vert.x uses deeply nested callbacks.
* We _don't_ use tab characters for indents, only spaces

Remember to set your IDE to remove trailing spaces on saving files,
since those produce unnecessary diffs in Git.

## License

Licensed under the Apache License, Version 2.0 (see [LICENSE]()).
See [background](http://www.apache.org/licenses/) information.

## Tests

We aim to write a lot of tests - each module should have at least some kind of
test associated with it. These can be traditional unit tests, black-box tests
that talk through the WS API, and/or proper integration tests.

When hunting down problems, it is considered good form to write a test that
demonstrates the problem first, then a fix that makes the test pass.

We have a Jenkins test system that gets invoked when you push something
to master, and/or make a pull request. It should flag any errors, but be
nice and run a ```mvn install``` on your own machine before every
```git commit```

## RAML

We keep the API specs in RAML files under okapi-core/src/main/raml/. Remember to
update those if you ever change anything in the API. And update the documentation
too, of course.
