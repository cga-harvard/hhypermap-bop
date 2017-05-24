Billion Object Platform UI [![Build Status](https://travis-ci.org/terranodo/angular-search.svg?branch=master)](https://travis-ci.org/terranodo/angular-search) [![Test Coverage](https://codeclimate.com/github/terranodo/angular-search/badges/coverage.svg)](https://codeclimate.com/github/terranodo/angular-search/coverage)
====

[AngularJS](https://angularjs.org/) + [OpenLayers 3](http://openlayers.org/) interface to query a [Apache Solr](http://lucene.apache.org/solr/) instance based on this [API](http://54.158.101.33:8080/bopws/swagger/#/default).
The Solr instance can be filtered by time, by a search term and by space.

[Demo](http://terranodo.io/angular-search)

Installation
---
Be sure to have at least node version 4 installed.


### Local environment:

Install dependencies:
```
npm install
```

For development use:
```
npm run server
```
This command compile the templates html, the less files and watch the changes.
Run in http://localhost:3001/search it uses the `404.html`

To run the production version locally:
```
npm run deploy
npm run serve
```

### Docker environment:

Using local docker command
```
docker build -t bopimage . 
docker run -i --name bopcontainer -d -p 80:80 bopimage
```
it runs in  http://localhost, if you want change the port, modify the first number of `80:80` on the above comand i.e `3000:80`

Using docker compose:
```
docker-compose build web
docker-compose up -d 
```

From **Docker hub** [harvardcga/bop-ui](https://hub.docker.com/r/harvardcga/bop-ui):
```
docker run -p 80:80 harvardcga/bop-ui
```

_Used libraries_:
* AngularJS 1.6.3
* OpenLayers 3 (v3.16.0)
* Bootstrap v3.3.4
