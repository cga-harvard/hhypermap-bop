FROM confluentinc/cp-kafka:3.0.1

COPY kafka-log4j.properties.template /etc/confluent/docker/log4j.properties.template
COPY kafka.run /etc/confluent/docker/run