# Preparation

  alias solr6='/SmileyDev/Search/solr-6.0.1/bin/solr'

Start Solr in SolrCloud mode and in the foreground (to make errors obvious).
We use this directory here as the "solr home" only to keep the state local
to this project and not intermingled with other possible Solr projects.
  
  bin/solr start -c -f -s bop-solrhome/

Explicitly upload a config set.

  solr6 zk -upconfig -n bop -d bop-solrhome/configsets/bop/conf/ -z localhost:9983
  
# Experimentation

Create the "bop" collection WITHOUT time sharding (purely for testing).

  solr6 create_collection -c bop -n bop
