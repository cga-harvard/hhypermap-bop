# Preparation

Start Solr in SolrCloud mode.
We use this directory here as the "solr home" only to keep the state local
to this project and not intermingled with other possible Solr projects.
  
    docker run --rm --name bop-solr -v "$(pwd)/bop/solrhome/:/opt/solr/server/solr" -p 8983:8983 harvardcga/solr -c

Explicitly upload a config set (also to update it when it changes)

    docker exec -ti bop-solr solr zk -upconfig -n bop -d server/solr/configsets/bop/conf/ -z localhost:9983
    # Or if NOT running Solr locally... (might have to use IP not hostname when on Kontena VPN)
    docker run --rm -v "$(pwd)/bop/solrhome/:/opt/solr/server/solr" harvardcga/solr \
        /opt/solr/bin/solr zk -upconfig -n bop -d server/solr/configsets/bop/conf/ -z 10.81.1.164:9983
  
Reload a collection using this config (obviously only after it's there)

  curl -XPOST http://localhost:8983/solr/admin/collections -F action=RELOAD -F name=bop

# Experimentation

Create the "bop" collection WITHOUT time sharding (purely for testing).

  docker exec -ti bop-solr solr create_collection -c bop -n bop
  # Kontena alternate instructions:
  kontena container exec bop-solr-1 solr create_collection -c bop -n bop
  
Add an alias, "tweets"

  curl -XPOST http://localhost:8983/solr/admin/collections \
  -F action=CREATEALIAS -F name=tweets -F collections=bop

Loading data from a zip of twitter JSON.  Notice we use a named request
handler to make doing this easier so we don't need to specify as many
params.  Note need Solr 6.1 on client to post large files from stdin
(regardless of server Solr version).

```
unzip -p results_mar_11_20_2013.zip | \
 $SOLR_INSTALL/bin/post \
 -url "http://localhost:8983/solr/bop/update/twitterJson" \
 -out yes -d
```

Old way for reference (doesn't work anymore but demonstrates Solr functionality)

```
unzip -p results_mar_11_20_2013.zip | \
$SOLR_INSTALL/bin/post -type application/json \
-url "http://localhost:8983/solr/bop/update/json/docs?\
f=id:/id_str&f=/created_at&f=coord:/coordinates/coordinates&f=/text&f=user_name:/user/name" \
-out yes -d
```