# (this should be source'ed) by other scripts

BOP_ENV="${BOP_ENV:-local}"

echo "(BOP_ENV=$BOP_ENV)"
case "$BOP_ENV" in
  "Kontena" ) # Kontena, VPN'ed to MOC
    #On Docker for Mac, name resolution when VPN to Kontena doesn't reliably resolve
    zkHostname="$(nslookup "bop-zookeeper" | grep -m1 "^Address: " | cut -d' ' -f2)"
    ZK_HOST="${zkHostname:?Failed to nslookup bop-zookeeper. Remember to be on VPN}:2181"
    SOLR_URL_COLL=http://bop-solr:8983/solr/bop_tweets2
    # createNodeSet is sensitive to the SOLR_HOST which is in turn based on
    #  KONTENA_NODE_NAME which has changed slightly across Kontena versions and seems sticky/node
    COLLECTION_OPTS=(-F maxShardsPerNode=8
      -F createNodeSet=bop-solr-1:8983_solr)
#    COLLECTION_OPTS=(-F maxShardsPerNode=4 \
#      -F rule=shard:RT,replica:1,role:overseer \
#      -F rule=shard:!RT,replica:1,role:!overseer~)
    ;;
  "local" )
    ZK_HOST=localhost:9983
    SOLR_URL_COLL=http://localhost:8983/solr/bop
    COLLECTION_OPTS=(-F maxShardsPerNode=8)
    ;;
  *)
    echo "Unrecognized BOP_ENV $BOP_ENV"
    exit 1
    ;;
esac

SOLR_URL=$(dirname "$SOLR_URL_COLL")
COLLECTION=$(basename "$SOLR_URL_COLL")