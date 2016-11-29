# (this should be source'ed) by other scripts

BOP_ENV="${BOP_ENV:-local}"

echo "(BOP_ENV=$BOP_ENV)"
case "$BOP_ENV" in
  "Kontena" ) # Kontena, VPN'ed to MOC
    ZK_HOST=bop-zookeeper:2181
    SOLR_URL_COLL=http://bop-solr:8983/solr/bop
    COLLECTION_OPTS=(-F maxShardsPerNode=4 \
      -F rule=shard:RT,replica:1,role:overseer \
      -F rule=shard:!RT,replica:1,role:!overseer~)
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