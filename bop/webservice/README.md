Build Instructions
==================

Prerequisites: Java 8, Maven.

This builds, skipping tests, and produces jar files in target/.

    $ mvn -DskipTests clean verify assembly:assembly

Tests currently require Solr is already running with a bop-tests
collection. TODO automate that setup better.  To set that up locally,
execute the following:

    $ docker run --rm -p 8983:8983 -v "$(pwd)/../solrhome/:/opt/solr/server/solr" \
        harvardcga/solr

Docker Instructions
===================

Prerequisites: Docker. And you must have built the 'jar' above.

Build an 'image' like so:

    $ docker build -t harvardcga/bop-webservice .
    
To run it (in the foreground):
    
    $ docker run --name bop-ws -p 8080:8080 --rm harvardcga/bop-webservice
    
Then point your browser at:
http://localhost:8080/bopws/swagger

To override configuration settings in dw.yml, you can pass environment
variables via docker prefixed by "dw." (Dropwizard). And some are
special that don't use "dw.".  Examples:
     
    -e dw.server.applicationContextPath=/bopws
    -e dw.logging.level=DEBUG
    -e SOLR_HOST=192.168.0.25
    -e SOLR_PORT=8983

If you wish to use another configuration file altogether, add this option to docker:

    -v "`pwd`/bop-ws/bop-ws.yml:/opt/bop-ws/bop-ws.yml"

IMPORTANT: Of course this web-service needs to reach Solr, and by default
it expects it at localhost:8983.  That will very likely need to be
changed, since the docker image doesn't contain Solr (a bad idea).
You might need to supply the real IP or hostname of your machine.


TODO
====

Consider doing a DW "healthcheck" immediately on boot, thus failing
if Solr isn't up?