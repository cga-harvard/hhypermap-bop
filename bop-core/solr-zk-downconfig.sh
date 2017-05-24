#!/usr/bin/env bash

set -eu

. env.sh

echo "Downloading config $COLLECTION to 'bop' (will overwrite)"
docker run --rm --net=host -v "$(pwd)/solrhome/configsets/:/hostConfigsSets/" \
  harvardcga/solr bin/solr zk -downconfig -n "$COLLECTION" -d /hostConfigsSets/bop/ -z "$ZK_HOST"
