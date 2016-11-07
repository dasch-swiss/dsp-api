.. Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
   Tobias Schweizer, André Kilchenmann, and André Fatton.

   This file is part of Knora.

   Knora is free software: you can redistribute it and/or modify
   it under the terms of the GNU Affero General Public License as published
   by the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   Knora is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU Affero General Public License for more details.

   You should have received a copy of the GNU Affero General Public
   License along with Knora.  If not, see <http://www.gnu.org/licenses/>.

.. _starting-graphdb-se-7:

Starting GraphDB-SE 7
======================

Inside the Knora API server git repository, there is a folder called ``/triplestores/graphdb-se-7`` containing the
GraphDB-SE-7 distribution archive and the dockerfile we will use to build the docker image. This docker image will then
be used to run a docker container, inside which GraphDB will be running.

Important Steps
---------------

To be able to successfully run GraphDB inside docker three important steps need to be done beforhand:

  1. Install Docker from http://docker.com.
  2. Copy the GraphDB-SE license file into this folder and name it ``GRAPHDB_SE.license``. It is already added to a
     local ``.gitignore`` file which can be found inside this folder. Under no circumstance should the license file be
     committed to Github.
  3. (optional) The current version of ``KnoraRules.pie`` from the ``webapi/scripts`` needs to be copied to this folder
     each time it was changed. This file needs to be copied into the docker image, which can only be done if it is found
     inside this folder.


Usage
-----

From inside the ``triplestores/graphdb-se-7`` folder, type:

::

  $ docker build -t graphdb .
  $ docker run --rm -it -p 7200:7200 graphdb


Do not forget the '.' in the first command.

 - ``--rm`` removes the container as soon as you stop it
 - ``-p`` forwards the exposed port to your host (or if you use boot2docker to this IP)
 - ``-it`` allows interactive mode, so you see if something get's deployed

After the GraphDB inside the docker container has started, you can find the GraphDB workbench here: http://localhost:7200

Above, we create and start a transient container (``--rm`` flag). To create a container that we can stop and start again
at a later time, follow the following steps:

::

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

 - ``--name`` give the container a name
 - ``-d`` run container in background and print container ID
 - ``-t`` allocate a pseudo TTY, so you see the console output
 - ``-p`` forwards the exposed port to your host