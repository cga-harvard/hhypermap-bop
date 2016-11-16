### Re-ingest instructions:

First ensure the ingestion process isn't running.

    docker run --rm -ti confluentinc/cp-kafka kafka-run-class kafka.tools.StreamsResetter \
        --bootstrap-servers kafka-kafka:9092 --zookeeper kafka-zookeeper:2181 \
        --application-id bop-ingest --input-topics tweets-enriched