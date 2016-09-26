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


Getting Started with the Knora API Server
=========================================

Choosing and Setting Up a Triplestore
-------------------------------------

The Knora API server requires a standards-compliant RDF_ triplestore. A number
of triplestore implementations are available, including `free software`_ as
well as proprietary options. The Knora API server is tested and configured to
work out of the box with the following triplestores:

* `Ontotext GraphDB`_, a high-performance, proprietary triplestore. The Knora
  API server is tested with GraphDB Standard Edition and GraphDB Free (which
  is proprietary but available free of charge).

* `Apache Jena`_, which is `free software`_. Knora comes bundled with Jena and with
  its standalone SPARQL server, Fuseki.

TODO: explain how to get started with these.

See the chapters on :ref:`starting-fuseki-2` and :ref:`starting-graphdb-se-7` for more details.


Load Test Data
^^^^^^^^^^^^^^

In order to load the test data, go to ``webapi/scripts`` and run the script for the triplestore you have chosen. In case of Fuseki, run ``fuseki-load-test-data.sh``,
in case of GraphDB ``graphdb-se-load-test-data.sh``

When working with GraphDB, you may encounter an error when loading the test data that says that there are multiple IDs for the same repository ``knora-test``.
In that case something went wrong when dropping and recreating the repository. Please delete all the data and start over:

* shutdown Tomcat: ``$CATALINA_HOME/bin/shutdown.sh``

* go to `<http://localhost:8080/openrdf-sesame/system/overview.view>`_ and look for the field ``Data directory`` which indicates the location of the directory ``.aduna``.

* Then remove this directory an restart tomcat. Now you should be able to load the test data correctly.


Creating a Test Installation
----------------------------

TODO: write subsections like this:

* Download the Knora API Server and Sipi from GitHub
* Configure
* Run

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
  [info]                            new-style standoff), 'all' (all of the above)
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

all
  Runs all of the above transformations.

Transformations that are not needed have no effect.

The program uses the Turtle parsing and formatting library from RDF4J_. Additional transformations can
be implemented as subclasses of ``org.eclipse.rdf4j.rio.RDFHandler``.

.. _RDF: https://www.w3.org/TR/rdf11-primer/
.. _free software: http://www.gnu.org/philosophy/free-sw.en.html
.. _Ontotext GraphDB: http://ontotext.com/products/graphdb/
.. _Apache Jena: https://jena.apache.org/
.. _Turtle: https://www.w3.org/TR/turtle/
.. _RDF4J: http://rdf4j.org/
