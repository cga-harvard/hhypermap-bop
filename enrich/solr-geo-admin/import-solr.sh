#!/usr/bin/env bash
set -ue

#Data dir is where the CSVs are
#DATA_DIR=/Volumes/...

#INPUT: COLLECTION
COLLECTION="$1"

# Send data using "curl" for convenience. Note that sometimes (definitely for STDIN, not sure for
# referring to files like this) it will need as much memory as the file/stream is (!).  In that
# event, switch to using Solr's bin/post tool which is not hampered by that (as of v6.1).
#
# Use rowid to create a synthetic ID. Note: we assume the target collection is either empty or has
# an identical (or smaller) set of IDs.
#for COLLECTION in "ADMIN2" "US_CENSUS_TRACT" "US_MA_CENSUS_BLOCK"; do
  curl -X POST "http://localhost:8983/solr/$COLLECTION/update/csv?optimize=true&rowid=id" \
    --data-binary "@$DATA_DIR/$COLLECTION.csv" -H 'Content-type:application/csv'
#done

# OTHER WAYS

#docker run --rm -ti --network host -v "$DATA_DIR:/data" solr:6-alpine \
#  bin/post -c "$COLLECTION" -params 'rowid=id' "/data/$COLLECTION.csv"