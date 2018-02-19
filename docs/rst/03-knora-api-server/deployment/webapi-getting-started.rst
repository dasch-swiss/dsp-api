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


Getting Started with the Knora API Server
=========================================

Choosing and Setting Up a Triplestore
-------------------------------------

The Knora API server requires a standards-compliant RDF_ triplestore. A number
of triplestore implementations are available, including `free software`_ as
well as proprietary options.

The Knora API server is designed to work with any standards-compliant triplestore.
It is primarily tested with `Ontotext GraphDB`_, a high-performance, proprietary
triplestore. We recommend GraphDB Standard Edition, but GraphDB Free (which is
proprietary but available free of charge) also works.

Knora includes support for `Apache Jena`_, which is `free software`_, but use of
Jena is deprecated, and support for it will probably be removed in the future.

Built-in support and configuration for other triplestores is planned.

See the chapters on :ref:`starting-graphdb` and :ref:`starting-fuseki`
for more details.


Creating Repositories and Loading Test Data
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

To create a test repository called ``knora-test`` and load test data into it,
go to ``webapi/scripts`` and run the script for the triplestore you have
chosen.

* For GraphDB SE:
    * If you are running GraphDB directly from its installation directory (using its ``graphdb`` script), run ``graphdb-se-local-init-knora-test.sh``.
    * If you are running GraphDB from a Docker image, run ``graphdb-se-docker-init-knora-test.sh``.
* For GraphDB Free:
    * Configuration is currently provided only for running GraphDB Free in Docker. To create a test repository, run ``graphdb-free-init-knora-test.sh``
* For Fuseki, run ``fuseki-load-test-data.sh``.

You can create your own scripts based on these scripts, to create new
repositories and optionally to load existing Knora-compliant RDF data into
them.

If you are using GraphDB, you must create your repository using a repository
configuration file that specifies the file ``KnoraRules.pie`` as its
``owlim:ruleset``. This enables RDFS inference and Knora-specific consistency
rules. When using GraphDB, the Knora API server uses RDFS inference to improve
query performance. The Knora-specific consistency rules help ensure that your
data is internally consistent and conforms to the Knora ontologies.

When testing with GraphDB, you may sometimes get an error when loading the
test data that says that there are multiple IDs for the same repository
``knora-test``. In that case, something went wrong when dropping and
recreating the repository. You can solve this by deleting the repository
manually and starting over. **Make sure you don't delete important data.** To
delete the repository, stop GraphDB, delete the ``data`` directory in your
GraphDB installation, and restart GraphDB.


Creating a Test Installation
----------------------------

Run a supported triplestore
^^^^^^^^^^^^^^^^^^^^^^^^^^^

See the chapters on :ref:`starting-graphdb` and :ref:`starting-fuseki` on how to start a supported triplestore.

Creating and running the WEBAPI Server distribution package
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

To create a deployment package for the WEBAPI Server, please run
from inside the ``knora/webapi`` folder the following commands:

::

  $ sbt stage
  $ cd target/universal/stage
  $ ./bin/webapi

Downloading and running SIPI
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Download SIPI from SIPI_ and install from source by following the SIPI_Manual_. For running and setting up SIPI
for Knora, please refer to :ref:`SIPI`.

Transforming Data When Ontologies Change
----------------------------------------

When there is a change in Knora's ontologies or in a project-specific ontology, it may be necessary to update existing
data to conform to the new ontology. This can be done directly in SPARQL, but for simple transformations, Knora
includes a command-line program that works on RDF data files in Turtle_ format. You can run it from SBT:

::

  > run-main org.knora.webapi.util.TransformData --help
  [info] Running org.knora.webapi.util.TransformData --help
  [info]
  [info] Updates the structure of Knora repository data to accommodate changes in Knora.
  [info]
  [info] Usage: org.knora.webapi.util.TransformData -t [deleted|permissions|strings|standoff|all] input output
  [info]
  [info]   -t, --transform  <arg>   Selects a transformation. Available transformations:
  [info]                            'deleted' (adds missing 'knora-base:isDeleted'
  [info]                            statements), 'permissions' (combines old-style
  [info]                            multiple permission statements into single permission
  [info]                            statements), 'strings' (adds missing valueHasString),
  [info]                            'standoff' (transforms old-style standoff into
  [info]                            new-style standoff), 'creator' (transforms existing
  [info]                            'knora-base:Owner' group inside permissions to
  [info]                            'knora-base:Creator'), 'owner' (gives
  [info]                            'knora-base:Creator' CR permissions to correspond to
  [info]                            the previous behaviour for owners - use with care as
  [info]                            it will add permissions that where not there before),
  [info]                            'all' (all of the above minus 'owner')
  [info]       --help               Show help message
  [info]
  [info]  trailing arguments:
  [info]   input (required)    Input Turtle file
  [info]   output (required)   Output Turtle file

The currently available transformations are:

deleted
  Adds ``knora-base:isDeleted false`` to resources and values that don't have a ``knora-base:isDeleted``
  predicate.

permissions
  Combines old-style permission statements (``hasViewPermission``, ``hasModifyPermission``, etc.) into
  one ``hasPermissions`` statement per resource or value, as described in the section **Permissions** in
  :ref:`knora-ontologies`.

strings
  Adds missing ``valueHasString`` statements to Knora value objects.

standoff
  Transforms old-style standoff markup (containing tag names as strings) to new-style standoff markup
  (using different OWL class names for different tags).

creator
  Transforms existing ``knora-base:Owner`` group inside permissions to ``knora-base:Creator``.

owner
  Gives ``knora-base:Creator`` **CR permissions** to correspond to the previous behaviour for owners.
  Use with care as it will add permissions that where not there before.

all
  Runs all of the above transformations.

Transformations that are not needed have no effect, so it is safe to use ``-t all``.

The program uses the Turtle parsing and formatting library from RDF4J_. Additional transformations can
be implemented as subclasses of ``org.eclipse.rdf4j.rio.RDFHandler``.

Selectively Disabling Routes
-----------------------------

In ``application.conf`` the setting ``app.routes-to-reject`` contains a list of strings, representing
routes which should be rejected.

For Example, the string ``"v1/users"`` would lead to rejection of any route which contains this string.

.. _RDF: https://www.w3.org/TR/rdf11-primer/
.. _free software: http://www.gnu.org/philosophy/free-sw.en.html
.. _Ontotext GraphDB: http://ontotext.com/products/graphdb/
.. _Apache Jena: https://jena.apache.org/
.. _Turtle: https://www.w3.org/TR/turtle/
.. _RDF4J: http://rdf4j.org/
.. _SIPI: https://github.com/dhlab-basel/Sipi
.. _SIPI_Manual: https://dhlab-basel.github.io/Sipi/documentation/index.html
