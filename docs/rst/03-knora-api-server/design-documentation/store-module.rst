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

.. _store-module:


Store Module
============

.. contents:: :local:

Overview
--------

The store module houses the different types of data stores supported by
the Knora API server. At the moment, only triplestores are supported. The triplestore
support is implemented in the ``org.knora.webapi.store.triplestore``
package.

Lifecycle
---------

At the top level, the store package houses  the ``StoreManager``-Actor
which is started when the Knora API server starts. The ``StoreManager`` then starts
the ``TripleStoreManagerActor`` which in turn starts the correct actor
implementation (e.g., GraphDB, Fuseki, embedded Jena, etc.).


HTTP-based Triplestores
-----------------------

HTTP-based triplestore support is implemented in the ``org.knora.webapi.triplestore.http`` package.

An HTTP-based triplestore is one that is accessed remotly over the HTTP protocol. We have implemented support for
the following triplestores:

  * Ontotext GraphDB

  * Fuseki 2


GraphDB
^^^^^^^

Fuseki 2
^^^^^^^^

Embedded Triplestores
---------------------

Embedded triplestores is implemented in the ``org.knora.webapi.triplestore.embedded`` package.

An embedded triplestore is one that runs in the same JVM as the Knora API server.


Apache Jena TDB
^^^^^^^^^^^^^^^

.. note::
   The support for embedded Jena TDB is currently dropped.
   The documentation and the code will remain in the repository. You can use it at your own risk.

The support for the embedded Jena-TDB triplestore is implemented in ``org.knora.webapi.triplestore.embedded.JenaTDBActor``.

The relevant Jena libraries that are used are the following:

 * Jena API - The library used to work programmatically with RDF data

 * Jena TDB - Their implementation of a triple store


Concurrency
~~~~~~~~~~~

Jena provides concurrency on different levels.

On the Jena TDB level there is the ``Dataset`` object, representing the
triple store. On every access, a transaction (read or write) can be
started.

On the Jena API level there is a ``Model`` object, which is equivalent
to an RDF ``Graph``. Here we can lock the model, so that MRSW (Multiple
Reader Single Writer) access is allowed.

 *  https://jena.apache.org/documentation/tdb/tdb_transactions.html

 *  https://jena.apache.org/documentation/notes/concurrency-howto.html

Implementation
~~~~~~~~~~~~~~

We employ transactions on the ``Dataset`` level. This means that every
thread that accesses the triplestore, starts a read or write enabled
transaction.

The transaction mechanism in TDB is based on write-ahead-logging. All
changes made inside a write-transaction are written to journals, then
propagated to the main database at a suitable moment. This design allows
for read-transactions to proceed without locking or other overhead over
the base database.

Transactional TDB supports one active write transaction, and multiple
read transactions at the same time. Read-transactions started before a
write-transaction commits see the database in a state without any
changes visible. Any transaction starting after a write-transaction
commits sees the database with the changes visible, whether fully
propagates back to the database or not. There can be active read
transactions seeing the state of the database before the updates, and
read transactions seeing the state of the database after the updates
running at the same time.

Configuration
~~~~~~~~~~~~~

In ``application.conf`` set to use the embedded triplestore:

::

    triplestore {
        dbtype = "embedded-jena-tdb"

        embedded-jena-tdb {
            persisted = true // "false" -> memory, "true" -> disk
            loadExistingData = false // "false" -> use data if exists, "false" -> create a fresh store
            storage-path = "_TMP" // ignored if "memory"
        }

        reload-on-start = false // ignored if "memory" as it will always reload

        rdf-data = [
            {
                path = "../knora-ontologies/knora-base.ttl"
                name = "http://www.knora.org/ontology/knora-base"
            }
            {
                path = "../knora-ontologies/knora-dc.ttl"
                name = "http://www.knora.org/ontology/dc"
            }
            {
                path = "../knora-ontologies/salsah-gui.ttl"
                name = "http://www.knora.org/ontology/salsah-gui"
            }
            {
                path = "_test_data/ontologies/incunabula-onto.ttl"
                name = "http://www.knora.org/ontology/incunabula"
            }
            {
                path = "_test_data/demo_data/incunabula-demo-data.ttl"
                name = "http://www.knora.org/data/incunabula"
            }
            {
                path = "_test_data/ontologies/images-onto.ttl"
                name = "http://www.knora.org/ontology/dokubib"
            }
            {
                path = "_test_data/demo_data/images-demo-data.ttl"
                name = "http://www.knora.org/data/dokubib"
            }
        ]
    }

Here the storage is set to ``persistent``, meaning that a Jena TDB store
will be created under the defined ``tdb-storage-path``. The
``reload-on-start`` flag, if set to ``true`` would reload the triplestore
with the data referenced in ``rdf-data``.

TDB Disk Persisted Store
~~~~~~~~~~~~~~~~~~~~~~~~

.. note::
   Make sure to set ``reload-on-start`` to ``true`` if run for
   the first time. This will create a TDB store and load the data.

If only *read access* is performed, then Knora can be run once with
reloading enabled. After that, reloading can be turned off, and the
persisted TDB store can be reused, as any data found under the
``tdb-storage-path`` will be reused.

If the TDB storage files get corrupted, then just delete the folder and
reload the data anew.


Actor Messages
~~~~~~~~~~~~~~

 *  ``ResetTripleStoreContent(rdfDataObjects: List[RdfDataObject])``

 *  ``ResetTripleStoreContentACK()``

The embedded Jena TDB can receive reset messages, and will ACK when
reloading of the data is finished. ``RdfDataObject`` is a simple case
class, containing the path and name (the same as ``rdf-data`` in the
config file)

As an example, to use it inside a test you could write something like:

::

    val rdfDataObjects = List (
           RdfDataObject(path = "../knora-ontologies/knora-base.ttl",
                         name = "http://www.knora.org/ontology/knora-base"),
           RdfDataObject(path = "../knora-ontologies/knora-dc.ttl",
                         name = "http://www.knora.org/ontology/dc"),
           RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl",
                         name = "http://www.knora.org/ontology/salsah-gui"),
           RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl",
                         name = "http://www.knora.org/ontology/incunabula"),
           RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl",
                         name = "http://www.knora.org/data/incunabula")
    )

    "Reload data " in {
        storeManager ! ResetTripleStoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTripleStoreContentACK())
    }


.. _SPARQL 1.1 Update Language: https://www.w3.org/TR/sparql11-update/#updateLanguage
