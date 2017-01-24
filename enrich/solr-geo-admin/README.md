
Overview
========

Geo enrichment (aka the "geo stamper"), is a process in which we query geo datasets by a lat & lon
point from the tweet to find out which admin/political polygonal boundaries the point is within.
The spatial query will retrieve some metadata and we copy that into the Tweet/record.
This can be called [Reverse Geocoding](https://en.m.wikipedia.org/wiki/Reverse_geocoding).

There are several geo datasets (each comprised of polygons) covering admin/political regions like
countries, and states.  The data sets we have are listed further below.  We load each of these into 
their own Solr core which we co-locate on the same Solr node. 


Data
====

Some stats as of the admin files given to me by Ben:

 * ADMIN2:              46,311 features,  824 MB folder, 8.5 hours indexing,  892 MB Solr
 * US_CENSUS_TRACT:     74,002 features,  747 MB folder, 4.9 min   indexing,  840 MB Solr
 * US_MA_CENSUS_BLOCK: 154,621 features,  152 MB folder, 5.9 min   indexing,  507 MB Solr

(times above were _without_ optimize=true; with optimize=true they take longer)

How to get feature count:

    ogrinfo gadm28_levels/
    ogrinfo gadm28_levels/ -sql "select count(*) from gadm28_adm2"

Setup Instructions
============

Note we use a custom Solr docker image that adds the JTS lib (for
polygons) which has to go in a certain spot, and we may tweak other things too.

In Kontena, it's possible that it won't successfully deploy because the Solr process won't necessarily have permissions to write to the attached disk.  Ideally before deployment, you should SSH to the machine and execute:

    $ chmod -R a+rw /media/attached/

Run Solr.  There is a docker-compse.yml file for local experimentation, and kontena.yml for actual deployment.

    kontena app deploy
    OR
    docker-compose up
    OR
    docker run --name solr-geo-admin --rm -p 8983:8983 -e SOLR_HEAP=712M harvardcga/solr

Solr will be listening on port 8983.  Now SSH to where Solr is running (or no need to if local host).  Here we assume that there are several directories, each representing a data set of geo-admin shapefiles.  In production, this is on a specific OpenStack volume that should be attached to the host where Solr is running (and kontena.yml is specific to deploy Solr at this host).

There are 3 steps: "setup Solr", "shapefile to CSV", and "import Solr".  "DATA_DIR" is the local directory that contains the data set directories of shape files.

Set two important env vars:
    $ export DOCKER_CONTAINER=geoadmin-solr-1
    $ export DATA_DIR=/media/attached

Then
    $ for COLLECTION in "ADMIN2" "US_CENSUS_TRACT" "US_MA_CENSUS_BLOCK"; do
    $   ./shapefile-to-csv.sh $COLLECTION
    $   ./setup-solr.sh $COLLECTION
    $   ./import-solr.sh $COLLECTION
    $ done
    
If for some reason you need to start over from a clean slate, you may need to clear out Solr's home directory, mounted in Kontena
at /media/attached/solr-geo-admin-home (while Solr isn't running!). You can
also execute this:
curl -X POST "http://localhost:8983/solr/admin/cores?action=UNLOAD&core=$COLLECTION&deleteInstanceDir=true"
But you won't need to run shapefile-to-csv again unless you remove the CSV file.  The CSVs will be placed in DATA_DIR.


Querying
========

This is a sample query. What's interesting is that it returns the geometry
even though it's not "stored" in the Solr schema. It's actually kept in
separate storage called Lucene DocValues, and this pulls it out formatting
it in WKT; GeoJSON is another option.

    http://localhost:8983/solr/geo-admin/select?fl=*,geo:[geo+f=WKT+w=WKT]&indent=on&q=*:*&rows=2&start=9&wt=json
