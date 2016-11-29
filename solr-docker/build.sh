#!/usr/bin/env bash

set -ue

(cd ../bop/solr-plugins && mvn -DskipTests package)

ln -f ../bop/solr-plugins/target/hcga-bop-solr-plugins.jar .

docker build -t harvardcga/solr .

docker push harvardcga/solr