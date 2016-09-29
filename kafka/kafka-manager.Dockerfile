FROM openjdk:8-jdk-alpine

# DWS: based off of https://github.com/DockerKafka/kafka-manager-docker/
#   and https://github.com/sheepkiller/kafka-manager-docker/
#MAINTAINER PalSzak

ENV ZK_HOSTS=localhost:2181 \
    KM_VERSION=1.3.1.8 \
    KM_CONFIGFILE="conf/application.conf"

RUN apk update && \
    apk add bash ca-certificates wget && \
    update-ca-certificates && \
    mkdir -p /tmp && \
    cd /tmp && \
    wget https://github.com/yahoo/kafka-manager/archive/${KM_VERSION}.tar.gz && \
    tar -xf ${KM_VERSION}.tar.gz && \
    cd /tmp/kafka-manager-${KM_VERSION} && \
    ./sbt dist && \
    mkdir -p /opt && \
    unzip -d /opt/ ./target/universal/kafka-manager-${KM_VERSION}.zip && \
    mv /opt/kafka-manager-${KM_VERSION} /opt/kafka-manager && \
    rm -fr /tmp/* /root/.sbt /root/.ivy2

COPY start-kafka-manager.sh /opt/kafka-manager/bin/docker-kafka-manager.sh
#VOLUME ["/opt/kafka-manager/conf"]

ENV PATH /opt/kafka-manager/bin:$PATH

EXPOSE 9000

WORKDIR /opt/kafka-manager

ENTRYPOINT ["./bin/docker-kafka-manager.sh"]