### GraphDB-SE 7.1.0 ###

This folder contains the distribution archive for **GraphDB-SE 7.1.0**, the latest released version supported by Knora.

GraphDB-SE is the Standard Edition of the triplestore from Ontotext (http://ontotext.com). GraphDB-SE must be licensed separately by the user.

#### Important Steps ####

To be able to successfully run GraphDB inside docker three important steps need to be done beforhand:

  1. Install Docker from http://docker.com.
  2. Copy the GraphDB-SE license file into this folder and name it ``GRAPHDB_SE.license``. It is already added to a
     local ``.gitignore`` file which can be found inside this folder. Under no circumstance should the license file be
     committed to Github.
  3. (optional) The current version of ``KnoraRules.pie`` from the ``webapi/scripts`` needs to be copied to this folder
     each time it was changed. This file needs to be copied into the docker image, which can only be done if it is found
     inside this folder.


#### Usage ####

From inside the ``triplestores/graphdb-se-7`` folder, type:

```
  $ docker build -t graphdb .
  $ docker run --rm -it -p 7200:7200 graphdb
```

Do not forget the '.' in the first command.

 - ``--rm`` removes the container as soon as you stop it
 - ``-p`` forwards the exposed port to your host (or if you use boot2docker to this IP)
 - ``-it`` allows interactive mode, so you see if something get's deployed

After the GraphDB inside the docker container has started, you can find the GraphDB workbench here: http://localhost:7200

Above, we create and start a transient container (``--rm`` flag). To create a container that we can stop and start again
at a later time, follow the following steps:

```
  $ docker build -t graphdb <path-to-dockerfile>
  $ docker run --name graphdb -d -t -p 7200:7200 graphdb
  
  (to see the console output, attach to the container; to detach press Ctrl-c)
  $ docker attach graphdb
    
  (to stop the container)
  $ docker stop graphdb
  
  (to start the container again)
  $ docker start graphdb
  
  (to remove the container; needs to be stopped)
  $ docker rm graphdb
```

 - ``--name`` give the container a name
 - ``-d`` run container in background and print container ID
 - ``-t`` allocate a pseudo TTY, so you see the console output
 - ``-p`` forwards the exposed port to your host