Building the BOP
================

Java / Maven
============

Much of the components are based on the Java platform; these parts use the
Apache Maven build system.  Download/install Java 8, and Maven in that order.
Some of the BOP software is written in Kotlin (a new JVM based language) and
some parts in Java but from a build/deploy point of view it doesn't matter
as both are JVM based.  Maven commands start with "mvn" and assume
a `pom.xml` file.  There is one at the root of the project that refers
to multiple "modules" (subdirectories with other pom.xml files).  The first
build step is to build the Java software like so:

````
mvn -DskipTests install
````

_Usually you will only do this once since; modules you change can be built by themselves._

What does that do?  It compiles source files, packages them up into
".jar" files, and the "install" Maven phase means it will place the jar
files into your workstation's local Maven repo.  At least for the `kafka-streams-base`
module, the install aspect is necessary.  _The BOP's Maven build does no
Docker/Kontena related things._

You can build modules by themselves (either directly or via Kontena) and
this is typical.
However, note that most Java modules in the BOP depend on the
`kafka-streams-base` module and so it needs to be "installed" first
which happens if you install at the top level or at that module level.
BTW kafka-streams-base has increased in scope beyond Kafka/Streams things.

The parent pom.xml refers to versions of Kafka, Solr, and other things.
Changing these versions won't literally change the version of such server
processes; those are referenced in docker image versions.  Changing it here
will change _client API_ versions which usually should have parity with the
installed server but small differences matter little.

**What about tests?**   Check each module to see what tests exist if any.
Some modules have none.  Sometimes some tests require other setup.

Kontena and Docker
==================

You don't *need* Kontena (a Docker orchestration platform) to
build or deploy the BOP for your own needs, but these instructions will assume
you are using it.  _Read on nevertheless._  With this
knowledge, you can build/reconfigure/deploy it as you please.
If you are also using Kontena and want to deploy new/different versions of the BOP
software then you'll have to modify the kontena.yml files to replace
Docker account references from "harvardcga" (in the "image" YAML field)
to your own, and/or a docker registry if you so choose.

Most parts of the BOP have a kontena.yml file, defined by the Kontena Docker
orchestration platform.  kontena.yml is not quite a superset of docker-compose.yml
but it's close.  A kontena.yml is a YAML file that lists "services".
(note: some services are declared to inherit from another service either
in the same file or even in a docker-compose.yml).
A service refers to a Docker image, possibly build instructions, and
deployment/configuration information.

When you run `kontena app build` it will iterate over each service in kontena.yml
 that has a "build" part, skipping the others.  Alternatively to build
 just one service, do `kontena app build SERVICENAME`.  For each service
 to build it will do the following:

1. If there is a `hooks: pre_build:` instruction for the service, then that is
run first.  The BOP's Java based modules will have these, which
executes Maven, including an "assembly:assembly" Goal
that gathers the dependencies (e.g. SolrJ jars)
and anything else needed to be included into a Docker image.
2. Kontena tells Docker to build an Image using the "Dockerfile" script.
The "image" field in kontena.yml says what its org/name:version image reference is.
3. Kontena tells Docker to push the images to Docker Hub.  If the
kontena.yml files had image metadata that references a registry then they would be
uploaded there instead.  The BOP ones don't; it's debatable if they should.

Although there is officially no "type" of service, it may be useful to think
of 3 different types:
* Services defined/provided by the BOP, like "ingest".
These have a "build" section in kontena.yml.
* Public Docker images, like a database; unmodified.
These have *no* build section in kontena.yml.
* BOP-Derived Docker images from a public one.
These sometimes have a "build" section in kontena.yml, depending on
whether the particular kontena.yml you see is referring to it or if it
also provides the build definition for the service.  BOP-derived docker
images use a image reference with the "havardcga" prefix to clearly show
it's not the original/public image.

The case of derived docker images is kind of a shame.  We've found the need
 to extend several major images.  In the case of Kafka and Zookeeper,
 this is mostly Docker/Kontena-ization for configuration/startup
 tweaks.  Some of these changes could and should be contributed upstream
 as pull requests to the public docker images.  For Solr, it's beyond that
 as we've forked the code to have certain improvements that have not all
 been contributed and committed upstream yet.

