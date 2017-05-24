#!/usr/bin/env bash

set -ue

CONTAINER=bop-solr

# Note: (1) enables java remote debugging, (2) maps local plugins jar into container
docker run --rm --name "$CONTAINER" -p 8983:8983 -p 9983:9983 -p 5005:5005 -e SOLR_HEAP=1G -e SOLR_HOST=127.0.0.1 \
  -v "$(pwd)/solr-plugins/target/hcga-bop-solr-plugins.jar:/opt/solr/server/solr-webapp/webapp/WEB-INF/lib/hcga-bop-solr-plugins.jar" \
  harvardcga/solr -c -a "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"

#curl -X POST http://localhost:8983/solr/admin/info/logging -F set=root:WARN > /dev/null
#curl -X POST http://localhost:8983/solr/admin/info/logging -F set=edu.harvard.gis.hhypermap.bop.solrplugins:DEBUG > /dev/null