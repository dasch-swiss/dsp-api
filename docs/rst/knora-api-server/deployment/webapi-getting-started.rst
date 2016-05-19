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
-----------------------------

TODO: write subsections like this:

* Download the Knora API Server and Sipi from GitHub
* Configure
* Run

.. _RDF: https://www.w3.org/TR/rdf11-primer/
.. _free software: http://www.gnu.org/philosophy/free-sw.en.html
.. _Ontotext GraphDB: http://ontotext.com/products/graphdb/
.. _Apache Jena: https://jena.apache.org/
