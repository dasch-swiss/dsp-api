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


Testing with Fuseki 2
=====================

Inside the Knora API server git repository, there is a folder called
``_fuseki`` containing a script named ``fuseki-server``. All needed
configuration files are in place. To start Fuseki 2, just run this
script.

How to Write Your Test
----------------------

(1) Inside a test, at the beginning, add the following (change the paths
    to the test data as needed):

::

    val rdfDataObjects = List (
           RdfDataObject(path = "_test_data/ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
           RdfDataObject(path = "_test_data/ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
           RdfDataObject(path = "_test_data/ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
           RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
           RdfDataObject(path = "_test_data/responders.v1.ValuesResponderV1Spec/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula")
    )

    "Reload data " in {
        storeManager ! ResetTripleStoreContent(rdfDataObjects)
        expectMsg(15.seconds, ResetTripleStoreContentACK())
    }

(2) In the config section add ``fuseki`` as the ``dbtype``:

::

    app {
         triplestore {
             //dbtype = "embedded-jena-tdb"
             dbtype = "fuseki"
         ...
    }

Important
---------

The reloading of the test data should be always done at the
beginning of the test, because when using Fuseki in combination with
``reload-on-start``, the data is not loaded in time (when the actor
starts), so that the tests already run without all the data inside the
triple store.
