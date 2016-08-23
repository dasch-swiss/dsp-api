# Triplestore initialization scripts

## GraphDB

### Initialize for unit testing (running tests inside sbt)

 - with GraphDB running inside a docker container:
 ```$ graphdb-se-docker-prepare.sh````
 
 - with GraphDB running locally, e.g., local tomcat server:
 ```$ graphdb-se-local-prepare.sh ```
 
 
### Initialize for using with the ``webapi`` 

 - with GraphDB running inside a docker container:
 ```$ graphdb-se-docker-load-test-data.sh````
 
 - with GraphDB running locally, e.g., local tomcat server:
 ```$ graphdb-se-local-load-test-data.sh ```
 

## Fuseki

Nothing neede to do. Simply run Fuseki, either in a docker container or locally by using the supplied distribution.