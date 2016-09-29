See dockerfile.

    docker build -t harvardcga/solr:latest .
    docker push harvardcga/solr:latest
    
Run:

    docker run --rm --name solr -p 8983:8983 -e SOLR_HOME=/var/solrhome -v /Volumes/HD1500/Data/tmp:/var/solrhome harvardcga/solr