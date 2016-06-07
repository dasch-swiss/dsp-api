### GraphDB-SE 6.x.x ###

This folder conains the distribution archive for **GraphDB-SE 6.6.4**, the latest released version supported by Knora.

GraphDB-SE is the Standard Edition of the triplestore from Ontotext (http://ontotext.com). GraphDB-SE must be licensed separately by the user.


#### Usage ####

```
$ docker build -t yourName <path-to-dockerfile>
$ docker run --rm -it -p 8080:8080 yourName
```

 - ```--rm``` removes the container as soon as you stop it
 - ```-p``` forwards the exposed port to your host (or if you use boot2docker to this IP)
 - ```-it``` allows interactive mode, so you see if something get's deployed