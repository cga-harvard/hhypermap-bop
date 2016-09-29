#1/bin/sh

# If SOLR_HOME exists yet has no solr.xml, populate with default solr home contents.
if [[ -d "$SOLR_HOME" && ! -f "$SOLR_HOME/solr.xml" ]]; then
  cp -R /opt/solr/server/solr/*  "$SOLR_HOME"
fi