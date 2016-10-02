#!/usr/bin/env bash
set -ue

#INPUT: COLLECTION
COLLECTION="$1"
# terminology note: "Solr core vs collection; usually the same thing"

# Note: this script is meant to be run on the same host running Solr in docker

DOCKER_CONTAINER=${DOCKER_CONTAINER:-"solr-geo-admin"}

# Where within the container is the Solr home?  It might be mounted elsewhere.
SOLR_HOME=$(docker exec $DOCKER_CONTAINER printenv SOLR_HOME)
if [ -z "$SOLR_HOME" ]; then
  SOLR_HOME="/opt/solr/server/solr"
fi
CONFIGSETS="/opt/solr/server/solr/configsets"

# Define a schema snippet. Do this now because we fail early if the collection name isn't supported.
# Also define the "fl" (field list) mapping what will ultimately get put into tweets
case "$COLLECTION" in
  "ADMIN2" )
    ADDFIELDS='
    "add-field":{ "name":"Admin2",      "type":"string", "indexed":true, "stored":true, "docValues":true, "required":true},
    "add-field":{ "name":"ADMIN2_TXT",  "type":"string", "indexed":false, "stored":true, "docValues":true, "required":true},
    '
    FL='id:field(Admin2),txt:field(ADMIN2_TXT)'
    ;;
  "US_CENSUS_TRACT" )
    ADDFIELDS='
    "add-field":{ "name":"GEOID10",     "type":"string", "docValues":true, "required":true},
    '
    FL='tract:field(GEOID10)'
    ;;
  "US_MA_CENSUS_BLOCK" )
    ADDFIELDS='
    "add-field":{ "name":"GEOID10",     "type":"string", "docValues":true, "required":true},
    "add-field":{ "name":"REALTOWN",    "type":"string", "docValues":true, "required":true},
    '
    FL='block:field(GEOID10),townName:field(REALTOWN)'
    ;;
   *)
  echo "Unrecognized collection $COLLECTION" >&2
  exit 1
  ;;
esac

# We create a core (aka collection), assuming *not* in SolrCloud.  Start with "basic_config" template.
#docker exec -it "$DOCKER_CONTAINER" \
#  bin/solr create_core -d basic_configs -c "$COLLECTION"
# DON'T DO ABOVE because if solr-home is configured to be elsewhere or is Docker mounted, this might
#  not work. So we do the manual equivalent: copy config, and create the core (tell Solr).

# Copy the configset "basic_configs" as a new collection
docker exec -it "$DOCKER_CONTAINER" \
  cp -R "$CONFIGSETS/basic_configs" "$SOLR_HOME/$COLLECTION"

# Rewrite solrconfig.xml to include a geometry cache. Unfortunately, this isn't configurable
# via Solr's API, so we do it this classic/manual way.
REP='<cache name="perSegSpatialFieldCache_WKT" class="solr.LRUCache" size="${solr.geomCacheSize:1024}" initialSize="0" autowarmCount="100%" regenerator="solr.NoOpRegenerator"/>'
docker exec -it "$DOCKER_CONTAINER" \
  sed -i 's@</query>@'"$REP"'</query>@' "$SOLR_HOME/$COLLECTION/conf/solrconfig.xml"

# Create Core based on existing instanceDir
curl -X POST "http://localhost:8983/solr/admin/cores?action=CREATE&name=$COLLECTION&instanceDir=$COLLECTION"

#   just in case...
sleep 1

# Setup schema. Note after this POST, the Solr core/collection will reload its configs.
curl -X POST -H 'Content-type:application/json'  "http://localhost:8983/solr/$COLLECTION/schema" -d '{
/*
 JTS Spatial4j docs: https://locationtech.github.io/spatial4j/apidocs/org/locationtech/spatial4j/context/jts/JtsSpatialContextFactory.html
 */

  "replace-field":{ "name":"id", "type":"int", "indexed":true, "stored":true, "docValues":true },

  "add-field-type":{
    "name":"geom",
    "class":"solr.RptWithGeometrySpatialField",

    "spatialContextFactory":"JTS",
    "precisionModel":"floating_single",
    "validationRule":"repairBuffer0",
    "autoIndex":"true",

    "geo":true,
    "distanceUnits":"kilometers",
    "maxDistErr":0.005
    },

  "add-field":{ "name":"WKT",    "type":"geom", "stored":false, "required":true},

  '"$ADDFIELDS"'

  "add-dynamic-field":{ "name":"*", "type":"string", "indexed":false, "stored":true, "docValues":false }

}'

curl -X POST -H 'Content-type:application/json'  "http://localhost:8983/solr/$COLLECTION/config" -d '{
  "add-requesthandler" : {
    "name": "/hcga_enrich",
    "class":"solr.SearchHandler",
    "defaults":{ "fl":"'"$FL"'", "omitHeader":true, "echoParams":"none" }
  }
}'

#ERRORS?  To start all over, remove this collection:
#curl -X POST "http://localhost:8983/solr/admin/cores?action=UNLOAD&core=$COLLECTION&deleteInstanceDir=true"
