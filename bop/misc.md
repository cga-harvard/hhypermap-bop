
Delete a shard
--------------

Firstly, you wouldn't normally do this since the oldest shards are removed
automatically when new shards are created. But lets say you stopped adding data; then you might want to do this.
Also, only do this to the tail end (oldest shard/month). You can get the shard
names by looking at the "Cloud" screen in the Solr admin UI.

    curl -v 'http://bop-solr-1:8983/solr/admin/collections?action=DELETESHARD&collection=bop&shard=shard-2016-12-23'