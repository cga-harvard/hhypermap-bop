Bop-core is the Solr/Ingester and Swagger webservice part of the project.     

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
