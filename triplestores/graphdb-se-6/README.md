### GraphDB-SE 6.6.4 ###

This folder contains the *war* files for **GraphDB-SE 6.6.4**, the latest released version supported by Knora.

GraphDB-SE is the Standard Edition of the triplestore from Ontotext (http://ontotext.com). GraphDB-SE must be licensed separately by the user.


#### Usage ####

```
$ docker build -t graphdb <path-to-dockerfile>
$ docker run --rm -it -p 8080:8080 graphdb
```

 - ```--rm``` removes the container as soon as you stop it
 - ```-p``` forwards the exposed port to your host (or if you use boot2docker to this IP)
 - ```-it``` allows interactive mode, so you see if something get's deployed
 
#### Advanced Usage ####

```
$ docker build -t graphdb <path-to-dockerfile>
$ docker run --name graphdb -d -it -p 8080:8080 graphdb
$ docker attach graphdb
(to detach press ^p^q, to stop press ^c)
$ docker start graphdb
$ docker stop graphdb
$ docker rm graphdb
```

 - ```--name``` give the container a name
 - ```-d``` run container in background and print container ID
 - ```-p``` forwards the exposed port to your host (or if you use boot2docker to this IP)
 - ```-it``` allows interactive mode, so you see if something get's deployed