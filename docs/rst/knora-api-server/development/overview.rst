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


Overview
=========

Developing for the Knora API server requires a complete local installation of Knora. The different parts are:

  1. The cloned Knora_ Github repository
  2. One of the supplied triple stores in the Knora Github repository (GraphDB-SE 7 or Fuseki 2).
  3. SIPI by building from source_ or using the docker image_


.. _Knora: https://github.com/dhlab-basel/Knora
.. _source: https://github.com/dhlab-basel/Sipi
.. _image: https://hub.docker.com/r/dhlabbasel/sipi/


Knora Github Repository
-----------------------

::

  $ git clone https://github.com/dhlab-basel/Knora


Triple Store
-------------

A number of triplestore implementations are available, including `free software`_ as
well as proprietary options. The Knora API server is tested and configured to
work out of the box with the following triplestores:

* `Ontotext GraphDB`_, a high-performance, proprietary triplestore. The Knora
  API server is tested with GraphDB Standard Edition and GraphDB Free (which
  is proprietary but available free of charge).

* `Apache Jena`_, which is `free software`_. Knora comes bundled with Jena and with
  its standalone SPARQL server, Fuseki.

See the chapters on :ref:`starting-fuseki-2` and :ref:`starting-graphdb-se-7` for more details.

.. _free software: http://www.gnu.org/philosophy/free-sw.en.html
.. _Ontotext GraphDB: http://ontotext.com/products/graphdb/
.. _Apache Jena: https://jena.apache.org/


SIPI
----

Build SIPI Docker Image
^^^^^^^^^^^^^^^^^^^^^^^

The Sipi docker image needs to be build by hand, as it requires the Kakadu distribution.

To build the image, and push it to the docker hub, follow the following steps: 

::

  $ git clone https://github.com/dhlab-basel/docker-sipi
  (copy the Kakadu distribution ``v7_8-01382N.zip`` to the ``docker-sipi`` directory)
  $ docker build -t dhlabbasel/sipi
  $ docker run --name sipi --rm -it -p 1024:1024 dhlabbasel/sipi
  (Ctrl-c out of terminal will stop and delete container)
  $ docker push dhlabbasel/sipi

Pushing the image to the docker hub requires prior authentication with ``$ docker login``. The user needs to be
registered on ``hub.docker.com``. Also, the user needs to be allowed to push to the ``dblabbasel`` organisation.


Running SIPI
^^^^^^^^^^^^^

To use the docker image stored locally or on the docker hub repository type:

::

  $ docker run --name sipi -d -p 1024:1024 dhlabbasel/sipi
  
This will create and start a docker container with the ``dhlabbasel/sipi`` image in the background. The default
behaviour is to start Sipi by calling the following command:

::

  $ /sipi/local/bin/sipi -config /sipi/config/sipi.knora-test-config.lua


To override this default behaviour, start the container by supplying another config file:

::

  $ docker run --name sipi \
               -d \
               -p 1024:1024 \
               dhlabbasel/sipi \
               /sipi/local/bin/sipi -config /sipi/config/sipi.config.lua

You can also mount a directory (the local directory in this example), and use a config file that is outside of the
docker container:

::

  $ docker run --name sipi \
               -d \
               -p 1024:1024 \
               -v $PWD:/localdir \
               dhlabbasel/sipi \
               /sipi/local/bin/sipi -config /localdir/sipi.knora-test-config.lua