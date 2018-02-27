.. Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
   Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.

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

.. _starting-graphdb:

Starting GraphDB
================

GraphDB SE
----------

Inside the Knora API server git repository, there is a folder called ``/triplestores/graphdb-se`` containing the
latest supported version of the GraphDB-SE distribution archive.


Running Locally
^^^^^^^^^^^^^^^

Unzip ``graphdb-se-x.x.x-dist.zip`` to a place of your choosing and run the following:

::

  $ cd /to/unziped/location
  $ ./bin/graphdb -Dgraphdb.license.file=/path/to/GRAPHDB_SE.license


Running inside Docker
^^^^^^^^^^^^^^^^^^^^^

Important Steps
~~~~~~~~~~~~~~~

To be able to successfully run GraphDB inside docker two important steps need to be done beforhand:

  1. Install Docker from http://docker.com.
  2. Copy the GraphDB-SE license file into a folder of you choosing and name it ``GRAPHDB_SE.license``. We will mount
     this folder into the docker container, so that the license can be used by GraphDB running inside the container.

Usage
~~~~~

::

  $ docker run --rm -it -v /path/to/license/folder:/external -p 7200:7200 dhlabbasel/graphdb


 - ``--rm`` removes the container as soon as you stop it
 - ``-p`` forwards the exposed port to your host (or if you use boot2docker to this IP)
 - ``-it`` allows interactive mode, so you see if something get's deployed

After the GraphDB inside the docker container has started, you can find the GraphDB workbench here: http://localhost:7200

Above, we create and start a transient container (``--rm`` flag). To create a container that we can stop and start again
at a later time, follow the following steps:

::

  $ docker run --name graphdb -d -t -v /path/to/license/folder:/external -p 7200:7200 dhlabbasel/graphdb

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


GraphDB Free
------------

You can run GraphDB Free locally as described for GraphDB SE above, or you can use Knora's pre-built
GraphDB Free Docker image:

::

  $ docker run --rm -p 7200:7200 dhlabbasel/graphdb-free
