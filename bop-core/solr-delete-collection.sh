#!/usr/bin/env bash

set -eu

. env.sh

echo "Deleting collection $COLLECTION"
curl -XPOST "$SOLR_URL/admin/collections" -F action=DELETE -F name="$COLLECTION"