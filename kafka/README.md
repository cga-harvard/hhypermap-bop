
Tips & Tricks
=============

When running "docker-compose up", stopping, and doing again, you may find
on subsequent tries it's confused about previous state and Kafka fails to
start. You may have to "docker-compose rm" 

Use the "Kafka Manager" (by Yahoo!):

    http://kafka-kafka-manager:9000/kafka-manager/

Kafka size:

    kontena container exec kafka-kafka-1 du -sh '/var/lib/kafka/data/*'

Copy topic from one to another:

    #reset if ran before
    docker run --rm -ti confluentinc/cp-kafka kafka-consumer-groups \
            --zookeeper kafka-zookeeper1:2181 \
            --delete --group replay-log-producer
    # do it
    docker run --rm -ti -e KAFKA_HEAP_OPTS=-Xmx800M confluentinc/cp-kafka kafka-run-class kafka.tools.ReplayLogProducer \
             --broker-list kafka-kafka:9092 --zookeeper kafka-zookeeper1:2181 \
             --inputtopic smileTemp --outputtopic smileLz4 \
             --property compression.type=lz4

ZooKeeper
=========

Zookeeper CLI to inspect and modify:

    docker run --rm -ti confluentinc/cp-kafka zookeeper-shell kafka-zookeeper1.kontena.local:2181