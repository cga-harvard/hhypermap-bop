#!/usr/bin/env bash

set -eu

. env.sh

echo "Creating collection $COLLECTION. (Note: will 400 error if already exists)"
curl -XPOST --fail "$SOLR_URL/admin/collections" -F action=CREATE -F name="$COLLECTION" \
  -F router.name=implicit -F shards=RT "${COLLECTION_OPTS[@]}"