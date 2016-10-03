
Tips & Tricks
=============

Use the "Kafka Manager" (by Yahoo!):

    http://kafka-kafka-manager:9000/kafka-manager/

Kafka size:

    kontena container exec kafka-kafka-1 du -sh '/var/lib/kafka/data/*'

ZooKeeper
=========

Zookeeper CLI to inspect and modify:

    docker run --rm -ti confluentinc/cp-kafka zookeeper-shell kafka-zookeeper.kontena.local:2181