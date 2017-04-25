Overview (official)
===================

The goal of the BOP is to provide a proof-of-concept platform designed to lower the
 barrier for researchers who need to access big streaming spatio-temporal datasets.  
 
The BOP is a project run by the Harvard Center for Geographic Analysis (HCGA) with funding by the Sloan Foundation.
It is being developed specifically to provide a big data API to the Harvard Dataverse and Harvard WorldMap platforms.
The platform is loaded with streaming geo-referenced tweets but a similar dataset could, with some tweaking, be substituted.
Hosting of the BOP is being provided by Massachusetts Open Cloud.

The BOP builds on the the 2D grid heatmap faceting capability of Apache Lucene/Solr
which we (HCGA) are calling HHypermap Core.   Development of that feature
was through HCGA and funded by the National Endowment for the Humanities.
It's also used by an HCGA map service discovery platform called HHypermap Registry.


Live
====

Web UI:
http://bop.worldmap.harvard.edu/bop/
  The web UI is *not* a direct part of the BOP project.

Web-Service:
http://bop.worldmap.harvard.edu/bopws/swagger#/default
 The link is to "Swagger" based documentation.


Technical Overview
==================

The primary infrastructure components are Apache Kafka and Apache Solr.
Furthermore we deploy everything to a Docker based infrastructure managed by
Kontena -- although you could deploy this software on bare metal if you
like.
The following sequence illustrates the path a tweet takes through the system,
and refers to modules (subdirectories in this GitHub repository) where
applicable.

**Incoming Flow:**

1. Tweets are "harvested" by [/CGA-Harvester/]. It's a service that continuously
 polls Twitter's API and then deposits the JSON tweets into a Kafka topic.
 We compress the JSON tweets using MessagePack.
2. Tweets are "enriched" by [/enrich/].  This service consumes input Kafka
 topic(s), does it's enrichment, then adds the enriched tweets onto
 another Kafka topic.  Enrichment consists of sentiment analysis (e.g. happy/sad) and
 geo enrichment (e.g. USA/Massachusetts) -- reverse-geocoding tagging.
3. Tweets are "ingested" (aka indexed) into Solr by [/bop/ingest]. This
 service consumes input Kafka topic(s) and of course sends them to Solr.
4. A custom Solr UpdateRequestProcessor (URP) called
 "DateShardingURPFactory" in [/bop/solr-plugins/] sees incoming tweets
 to route them to the correct time based shard. This URP will also create
 and delete new shards as needed.

**Search Request Flow:**

1. There is a BOP web UI that makes HTTP/REST requests to the BOP.
(Technically this is not part of the BOP).  Or they might come from someone
else.
2. HTTP requests are proxied through a Kontena loadbalancer [/loadbalancer/]
that is more so serving a proxy function into the Kontena "weave" network
than a load balancing one.
3. The BOP Webservice at [/bop/webservice] is a RESTful webservice
providing a search service to Solr. (This enables our own API
instead of Solr's or securing Solr).
4. A custom Solr SearchHandler called DateShardRoutingSearchHandler
looks for special start-end parameters to determine which date shards to
route the request to (avoiding complete fan-out).


**Further resources:**

Each portion of the BOP may have further documentation beyond this
document.  Expore and read README.md files.

This presentation provides a good overview.  However be aware that some
 aspects like the realtime shard handoff have not been implemented.
Presentation: "H-Hypermap: Heatmap Analytics at Scale" (2016-10)
slides: http://www.slideshare.net/DavidSmiley2/hhypermap-heatmap-analytics-at-scale
video: https://www.youtube.com/watch?v=nzAH5QEl9hQ


**Next Step: [Building the BOP](BUILD.md)**


Credits
=======
* Benjamin Lewis (HCGA) -- Project Leader
* David Smiley (independent) -- Lead Developer
* Devika Kakkar (HCGA) -- Developer: Sentiment analysis, geotweet harvesting