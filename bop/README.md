This is the "BOP" part of the project.  But hey isn't the entire project
 source tree the BOP, you might ask?  This is the subject of some internal
 debate/confusion. Technically no; in particular the
 tweet harvesting, Kafka process, and enrichment are not, even though
 some of this was established as a part of the BOP development effort.
 The "core of the BOP" may add clarity
 as that is definitely what's in this directory here. Perhaps this directory
 should be renamed to "bop-core" or just "core" so as to not confuse it
 with related things.

Dependencies: Operationally, we expect a Kafka topic to consume from.  But
 from a software/code/config standpoint, this module also depends on
  HCGA customized Docker images for Zookeeper and likewise for Solr.
  "kafka-streams-base" is also a dependency.

Modules/Directories
===================

**[ingest](ingest/)** is a service that consumes from a Kafka topic,
maps Tweet JSON to a SolrInputDocument that aligns with our schema, then
sends it to Solr.

**solr-plugins** is code that will run inside the Solr process which
adds functionality.  This is referenced externally by
"solr-docker" at the top level of the source tree.

**solrhome** holds a Solr "configset"
(configuration files, including a schema).  TODO

**webservice** a HTTP/REST service that communicates with Solr.

Other files: TODO