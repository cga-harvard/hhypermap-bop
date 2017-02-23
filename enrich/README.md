### Preparing the sentiment server ###

This creates both a "sent-server" container and a "classifier-data" volume.
````
docker create --name sent-server -p1234:1234 -v classifier-data:/var/classifier/ dwsmiley/hcga/sentiment-server
docker cp /SmileyDev/Consulting/Harvard\ CGA/classifier-data/svmClassifier.pkl sent-server:/var/classifier/
# Optional: start it (or alternatively remove the container)
docker start sent-server
````
You can see the volume via `docker volume ls`
You can see the container via `docker ps -a` 

### Running the ETL tests ###

docker-compose -f src/test/docker-compose-integration-test.yml up --abort-on-container-exit

cd enrich
mvn test

docker-compose docker-compose-integration-test.yml down

#### Examining ####

docker run -ti confluent/tools kafka-console-consumer --topic etlOut --zookeeper 192.168.100.102:2181 --from-beginning

### Create Enriched Kafka Topic

(see example in kafka/README.md)

### Resetting the Stream State ###

If you want to start processing from the beginning all over again, follow these instructions. 
Alternatively maybe you should create a new "application-id" if that's fitting.

Background info: http://www.confluent.io/blog/data-reprocessing-with-kafka-streams-resetting-a-streams-application/

    kontena container exec kafka1.novalocal/null-kafka-kafka-1 kafka-run-class kafka.tools.StreamsResetter \
        --bootstrap-servers kafka-kafka:9092 --zookeeper kafka-zookeeper:2181 \
        --application-id re-enrich --input-topics TweetArchiveInput
    
    Also, shouldn't need to do this as the stream has no
    local state as of this writing, but you could set -DkafkaStreamsReset=true to our app

Note: https://github.com/confluentinc/cp-docker-images/issues/145 (so we work-around here)

### Build Docker Image ###

    # note: Kontena will first invoke maven build, then docker compose build
    kontena app build
    
    # push (upload) images to the registry:
    docker-compose push

### Docker Compose ###

Prerequisites: Kafka running, "solr-geo-admin" running.
    
    export HOST_IP=192.168.0.25  #OR WHATEVER YOUR IP IS; NOT 127.0.0.1
    docker up --abort-on-container-exit
