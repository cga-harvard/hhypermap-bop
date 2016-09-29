FROM solr:6-alpine

MAINTAINER David Smiley <dsmiley@apache.org>

# Add JTS jar which unfortunately must go in WEB-INF/lib/
RUN wget --no-verbose -P server/solr-webapp/webapp/WEB-INF/lib/ https://repo1.maven.org/maven2/com/vividsolutions/jts-core/1.14.0/jts-core-1.14.0.jar

COPY initEmptySolrHome.sh /docker-entrypoint-initdb.d/

# Ignore solr.in.sh since it overwrites the env vars we want to set
ENV SOLR_INCLUDE=" "

# ENVs same as default settings in solr.in.sh:

ENV SOLR_HEAP=512m

ENV GC_TUNE="-XX:NewRatio=3 \
    -XX:SurvivorRatio=4 \
    -XX:TargetSurvivorRatio=90 \
    -XX:MaxTenuringThreshold=8 \
    -XX:+UseConcMarkSweepGC \
    -XX:+UseParNewGC \
    -XX:ConcGCThreads=4 -XX:ParallelGCThreads=4 \
    -XX:+CMSScavengeBeforeRemark \
    -XX:PretenureSizeThreshold=64m \
    -XX:+UseCMSInitiatingOccupancyOnly \
    -XX:CMSInitiatingOccupancyFraction=50 \
    -XX:CMSMaxAbortablePrecleanTime=6000 \
    -XX:+CMSParallelRemarkEnabled \
    -XX:+ParallelRefProcEnabled"

ENV ENABLE_REMOTE_JMX_OPTS="false"

ENV SOLR_OPTS="-Xss256k"
