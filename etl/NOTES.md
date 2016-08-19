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

cd etl
mvn test

docker-compose docker-compose-integration-test.yml down

#### Examining ####

docker run -ti confluent/tools kafka-avro-console-consumer --topic etlOut --zookeeper 192.168.100.102:2181 --from-beginning --property schema.registry.url=http://192.168.100.102:8081

### Managing Kafka ###

docker run --rm -e ZK_HOSTS=192.168.100.102:2181 sheepkiller/kafka-manager:1.3.1.6

Then add the cluster

### Managing Docker ###

#### top containers ####
docker stats

#### remove containers that probably aren't needed ####
docker rm $(docker ps -q -f status=exited)

### Build Docker Image ###

mvn package -DskipTests docker:build