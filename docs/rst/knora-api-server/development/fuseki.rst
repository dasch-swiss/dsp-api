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

.. _starting-fuseki-2:

Starting Fuseki 2
==================


Locally
-------

Inside the Knora API server git repository, there is a folder called
``triplestores/fuseki`` containing a script named ``fuseki-server``. All needed
configuration files are in place. To start Fuseki 2, just run this
script:

::

  $ ./fuseki-server

Inside Docker
--------------

Inside the Knora API server git repository, there is a folder called ``triplestores/fuseki`` containing the
``Dockerfile`` we can use to create the docker image:

::

  $ cd triplestores/fuseki
  $ docker build -t fuseki .
  $ docker run --rm -it -p 3030:3030 fuseki


Do not forget the '.' in the first command.