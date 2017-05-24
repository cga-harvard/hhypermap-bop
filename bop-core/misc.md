
Moving a shard
--------------

First you add a replica, after which the shard is in 2 places. Then you remove the first replica.

    curl -v -XPOST 'http://bop-solr-1:8983/solr/admin/collections?action=ADDREPLICA&collection=bop_tweets2&shard=shard-2016-09-01T00_00_00Z&node=bop-solr-5.bop.kontena.local%3A8983_solr'

Now wait until the move is complete.  Use the Solr admin UI, "Cloud" tab for the new replica to appear in green.
Now delete the old:

    curl -v -XPOST 'http://bop-solr-1:8983/solr/admin/collections?action=DELETEREPLICA&collection=bop_tweets2&shard=shard-2016-09-01T00_00_00Z&replica=core_node22'

To know how to refer to the "node" correctly, go to the Solr admin screen, "Cloud" tab, "Tree" tab.  Then expand "/live_nodes".
It should be obvious which machine each is for.  Note that the colon should be escaped with "%3A" when put into a URL.

To delete the old one, you needn't use the curl command above.  The easiest way is to use Solr's UI for this.
Go to the "Collections" tab then click the right one then click the right
month. You'll see both replicas.  Delete the old one (probably the smaller number). You can expand the arrow to see the details
to know which node each replica is on.

Delete a shard
--------------

Firstly, you wouldn't normally do this since the oldest shards are removed
automatically when new shards are created. But lets say you stopped adding data; then you might want to do this.
Also, only do this to the tail end (oldest shard/month). You can get the shard
names by looking at the "Cloud" screen in the Solr admin UI.

    curl -v -XPOST 'http://bop-solr-1:8983/solr/admin/collections?action=DELETESHARD&collection=bop_tweets2&shard=shard-2014-09-01T00_00_00Z'
