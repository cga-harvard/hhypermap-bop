This is a service that consumes from a Kafka topic,
maps Tweet JSON to a SolrInputDocument that aligns with our schema, then
sends it to Solr.  We call it "ingest" though "indexer" is an alias.

The code is written in Kotlin and is partially based on Dropwizard with
our related utilities in "kafka-streams-base".  A dropwizard YAML file
is in [dw.yml](src/main/resources/dw.yml) that maps to a Configuration
source file.  However the values are overridden by environment variables
starting with `dw.`; hyphens are replaced with periods. With
Kontena/Docker you will most likely end up modifying kontena.yml
(and not dw.yml).

This service _could_ have instead been developed as a "Kafka Connect"
 sink to Solr.  I (DWS) investigated this possibility but I was underwhelmed
 with Kafka Connect, particularly in a Docker/Kontena environment with how
 Connect wants to be a server, which would be very awkward. I was also
 annoyed with duplication of concepts with Kafka Streams.
 I think Connect will be most successful when a OOTB Connect connector is
 available so you just need to configure it.  However with a destination
 like Solr (or any other NoSQL store), I think many times you'll
 want to actually write some code to map the data in the exact way
 you need without being hampered by the constraints of whatever data mapping
 facilities exists in the Connect connector.

### Kafka

Obviously the topic to consume from is an important piece of configuration.
But also very important is the "group-id" since the Kafka consumer offsets
are tracked using this identifier.  Thus if you wanted to populate another
Solr collection or BOP instance, you would run another ingest service with
a different group ID.

### Topic / Tweets / format

The Kafka records are MessagePack compressed JSON.  The record key is the
ID of the tweet.  The Tweet JSON is defined by Twitter, and note the
details have changed over time.  In addition, the HCGA/BOP enrichment
process will add additional metadata that this service looks for
(though doesn't require).

### Solr

The Solr configuration here is two parts: (A) the Solr collection to
send the document to, and (B) a "solrConnectionString"
which might either be an HTTP reference to a Solr node, or SolrCloud
zookeeper coordinates (see code for details).
You might see an HTTP reference despite the use
of SolrCloud; this is a debatable trade-off to compromise indexing
availability for consistency in performance and ease of diagnostics.

Originally it was conceived that the "ingest" would route the document to
the right shard.  But to try and make some aspects like this more re-usable
to other projects, the functionality was instead developed as a Solr URP.

## Tips/how-to:

### Re-ingest instructions:

Lets say you want to re-index everything in Solr. Perhaps the schema has
changed in an incompatible way; who knows.  You may or may not
want to index to the same collection.  With respect to Kafka, we need
to start from the beginning.  The easiest thing to do is
simply use a new "group-id"; perhaps increase a counter at the end.
I find that a little unsatisfying/hacky.  Another approach, is to remove the
traces of the offsets for this group id in Kafka.
First ensure the ingestion process isn't running (obviously).

TODO

Double-check, perhaps in the Kafka Manager, that this has the intended
effect.  KM shows consumers; it shouldn't show this one anymore.