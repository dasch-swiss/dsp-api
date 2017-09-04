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


Running the Knora API Server on a Production System
===================================================

.. contents:: :local:

This section describes possible ways of running the Knora API server in
an production environment. The description should only be taken as a first
short introduction to this topic. Further reading of the referenced materials
is advised.

.. note::
    Our platform of choice is Linux CentOS 7 and is thus assumed in the
    description. The general idea should be usable on all platforms with
    small changes.

To run the Knora API server, we have two main components. First, the zipped
distribution of the server and second a supported triplestore.


Creating and running the distribution package
---------------------------------------------

Inside the `knora/webapi` folder run the following `sbt` commands:

```
$ sbt packageBin
```

This will create a `zip` file inside the `knora/webapi/target/universal` folder.
To run the Knora API Server, unzip this package, and execute the `webapi` script
inside the `bin` folder.

Alternatively, the command `sbt stage` will create a folder with the same content as before,
but will skip the zipping step.


Running a supported triplestore
--------------------------------

See the chapters on :ref:`starting-graphdb` and :ref:`starting-fuseki` on how
to start a supported triplestore.
