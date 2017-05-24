#!/usr/bin/env bash

# Note: has optional arg "--reload" to reload the Solr collection of the same name.

set -eu

. env.sh

echo "Uploading config to $COLLECTION"
docker run --rm --net=host -v "$(pwd)/solrhome/configsets/:/hostConfigsSets/" \
  harvardcga/solr bin/solr zk -upconfig -n "$COLLECTION" -d /hostConfigsSets/bop/conf/ -z "$ZK_HOST"

if [ "${1:-x}" = "--reload" ]; then
  echo "Telling Solr to 'reload' (re-read config)"
  curl -XPOST "$SOLR_URL/admin/collections" -F action=RELOAD -F name="$COLLECTION"
else
  echo "Not telling Solr to reload."
fi

