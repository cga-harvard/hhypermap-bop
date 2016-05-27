# Preparation

  alias solr6='/SmileyDev/Search/solr-6.0.1/bin/solr'

Start Solr in SolrCloud mode and in the foreground (to make errors obvious).
We use this directory here as the "solr home" only to keep the state local
to this project and not intermingled with other possible Solr projects.
  
  solr6 start -c -f -s bop-solrhome/

Explicitly upload a config set (also to update it when it changes)

  solr6 zk -upconfig -n bop -d bop-solrhome/configsets/bop/conf/ -z localhost:9983
  
# Experimentation

Create the "bop" collection WITHOUT time sharding (purely for testing).

  solr6 create_collection -c bop -n bop

Loading data from a zip of twitter JSON.  Notice we use a named request
handler to make doing this easier so we don't need to specify as many
params.  Note need at least Solr 6.0.1 to post large files from stdin.

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