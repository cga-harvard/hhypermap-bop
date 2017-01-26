#!/usr/bin/env bash

set -ue

SOLR_VERSION=6.3.0-hcga1
(cd ../bop/solr-plugins && mvn -DskipTests package)

ln -f ../bop/solr-plugins/target/hcga-bop-solr-plugins.jar .

docker build -t harvardcga/solr -t harvardcga/solr:$SOLR_VERSION .

docker push harvardcga/solr
docker push harvardcga/solr:SOLR_VERSION