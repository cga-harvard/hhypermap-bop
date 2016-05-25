# How-to

  alias solr6='/SmileyDev/Search/solr-6.0.1/bin/solr'

Start Solr in SolrCloud mode and in the foreground (to make errors obvious)
  
  bin/solr start -c -f -s bop-solrhome/

Explicitly upload a config set (so that it might be reference-able by
multiple collections, even if we start with one).

  solr6 zk -upconfig -n bop -d bop-solrhome/configsets/bop/conf/ -z localhost:9983
  
Create the "bop" collection
  
  bin/solr create_collection -c bop -n bop
  