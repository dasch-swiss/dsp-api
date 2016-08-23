### Apache Jena Fuseki 3.0.0 ###

This folder contains a distribution for **Apache Jena Fuseki 3.0.0**, the latest released version supported by Knora.

Fuseki is the triplestore from the Apache Jena project (http://https://jena.apache.org). Apache Jena license applies.


#### Simple Usage ####

```
$ docker build -t fuseki <path-to-dockerfile>
$ docker run --rm -it -p 3030:3030 fuseki
```

 - ```--rm``` removes the container as soon as you stop it
 - ```-p``` forwards the exposed port to your host (or if you use boot2docker to this IP)
 - ```-it``` allows interactive mode, so you see if something get's deployed
 
 
#### Advanced Usage ####

```
$ docker build -t fuseki <path-to-dockerfile>
$ docker run --name fuseki -d -it -p 3030:3030 fuseki
$ docker attach fuseki
(to detach press ^p^q, to stop press ^c)
$ docker start fuseki
$ docker stop fuseki
$ docker rm fuseki
```

 - ```--name``` give the container a name
 - ```-d``` run container in background and print container ID
 - ```-p``` forwards the exposed port to your host (or if you use boot2docker to this IP)
 - ```-it``` allows interactive mode, so you see if something get's deployed