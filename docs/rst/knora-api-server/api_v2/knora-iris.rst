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

.. _knora-iris-v2:

Knora IRIs
==========

.. contents:: :local:

The IRIs used in Knora repositories and in the Knora API v2 follow certain conventions.

Project Short-Codes
-------------------

A project short-code is a hexadecimal number of at least four digits, assigned by the DaSCH_ to uniquely
identify a Knora project regardless of where it is hosted. Project short-codes are currently optional. It
is recommended that new projects request a project code and use it in their ontology IRIs, to avoid
possible future naming conflicts.

The range of project IDs from ``0001`` to ``00FF`` inclusive is reserved for local testing, and also the ID ``0000`` is
reserved for future use by the system. Thus, the first useful project will be ``0100``.

In the beginning, Unil will use the IDs ``0100`` to ``07FF``, and Unibas ``0800`` to ``08FF``.

IRIs for Ontologies and Ontology Entities
-----------------------------------------

Internal Ontology IRIs
^^^^^^^^^^^^^^^^^^^^^^

Starting with Knora API v2, Knora makes a distinction between internal and external ontologies.
Internal ontologies are used in the triplestore, while external ontologies are used in the API. For
each internal ontology, there is a corresponding external ontology. Some internal ontologies are
built into Knora, while others are project-specific. The Knora API server automatically generates
external ontologies based on project-specific internal ontologies.

Each internal ontology has an IRI, which is also the IRI of the named graph that contains the
ontology in the triplestore. An internal project-specific ontology IRI has the form:

::

   http://www.knora.org/ontology/PROJECT_SHORTCODE/ONTOLOGY_NAME

For example, the ontology IRI based on project code ``0001`` and ontology name ``example`` would be:

::

   http://www.knora.org/ontology/0001/example

An ontology name must be a valid XML NCName_. The following names are reserved for built-in internal
Knora ontologies:

- ``knora-base``
- ``standoff``
- ``salsah-gui``
- ``dc``

Names starting with ``knora`` are reserved for future built-in Knora ontologies. A project-specific
ontology name may not start with the letter ``v`` followed by a digit, and may not contain these
reserved words:

- ``knora``
- ``ontology``
- ``simple``

.. _external-ontology-iris-v2:

External Ontology IRIs
^^^^^^^^^^^^^^^^^^^^^^

Unlike internal ontology IRIs, external ontology IRIs are meant to be dereferenced as URLs. When an
ontology IRI is dereferenced, the ontology itself can be served either in a machine-readable format
or as human-readable documentation.

The IRI of an external Knora ontology has the form:

::

   http://HOST[:PORT]/ontology/PROJECT_SHORTCODE/ONTOLOGY_NAME/API_VERSION

For built-in ontologies, the host is always ``api.knora.org``. Otherwise, the hostname and port
configured in ``application.conf`` under ``app.http.knora-api.host`` and ``app.http.knora-api.http-port``
are used (the port is omitted if it is 80).

This means that when a built-in external ontology IRI is dereferenced, the ontology can be served by
a Knora API server running at ``api.knora.org``. When a project-specific external ontology IRI is
dereferenced, the ontology can be served by the Knora API server that hosts the project. During
development and testing, this could be ``localhost``.

The name of an external ontology is the same as the name of the corresponding internal ontology,
with one exception: the external form of ``knora-base`` is called ``knora-api``.

The API version identifier indicates not only the version of the API, but also an API 'schema'. The
Knora API v2 is available in two schemas:

- A default schema, which is suitable both for reading and for editing data. The default schema
  represents values primarily as complex objects. Its version identifier is ``v2``.

- A simple schema, which is suitable for reading data but not for editing it. The simple schema
  facilitates interoperability between Knora ontologies and non-Knora ontologies, since it
  represents values primarily as literals. Its version identifier is ``simple/v2``.

Other schemas could be added in the future for more specific use cases.

When requesting an ontology, the client requests a particular schema. (This will also be true of
most Knora API v2 requests: the client will be able to specify which schema the response should be
provided in.)

For example, suppose a Knora API server is running at ``knora.example.org`` and hosts an ontology
whose internal IRI is ``http://www.knora.org/ontology/0001/example``. That ontology can then be
requested using either of these IRIs:

- ``http://knora.example.org/ontology/0001/example/v2`` (for the default schema)
- ``http://knora.example.org/ontology/0001/example/simple/v2`` (for the simple schema)

While the internal ``example`` ontology refers to definitions in ``knora-base``, the external
``example`` ontology that is served by the API refers instead to a ``knora-api`` ontology, whose IRI
depends on the schema being used:

- ``http://api.knora.org/ontology/knora-api/v2`` (for the default schema)
- ``http://api.knora.org/ontology/knora-api/simple/v2`` (for the simple schema)

Ontology Entity IRIs
^^^^^^^^^^^^^^^^^^^^

Knora ontologies use 'hash namespaces' (see `URI Namespaces`_). This means that the IRI of an
ontology entity (a class or property definition) is constructed by adding a hash character (``#``)
to the ontology IRI, followed by the name of the entity. In Knora, an entity name must be a valid
XML NCName_. Thus, if there is a class called ``ExampleThing`` in an ontology whose internal IRI is
``http://www.knora.org/ontology/0001/example``, that class has the following IRIs:

- ``http://www.knora.org/ontology/0001/example#ExampleThing`` (in the internal ontology)
- ``http://HOST[:PORT]/ontology/0001/example/v2#ExampleThing`` (in the API v2 default schema)
- ``http://HOST[:PORT]/ontology/0001/example/simple/v2#ExampleThing`` (in the API v2 simple schema)

IRIs for Data
-------------

Knora generates IRIs for data that it creates in the triplestore. Each generated data IRI contains
one or more UUID_ identifiers to make it unique. To keep data IRIs relatively short, each UUID is
Base64_ encoded, using the 'URL and Filename safe Base64 Alphabet' specified in Table 2 of RFC 4648,
without padding; thus each UUID is a 22-character string.

Data IRIs are not currently intended to be dereferenced as URLs. Instead, each Knora resource will
have a corresponding ARK_ URL, which will be handled by a server that redirects requests to the
relevant Knora API server (see :ref:`permalinks`). However, every generated data IRI begins with
``http://rdfh.ch``. This domain is not curently used, but it is owned by the DaSCH_, so it would be
possible to make resource IRIs directly dereferenceable in the future.

The formats of generated data IRIs for different types of objects are as follows:

- Resource: ``http://rdfh.ch/PROJECT_SHORTCODE/RESOURCE_UUID``. The current implementation actually uses
  the project shortname, but it will be changed to use the project code
  (`issue #654 <https://github.com/dhlab-basel/Knora/issues/654>`_).
- Value: ``http://rdfh.ch/PROJECT_SHORTCODE/RESOURCE_UUID/values/VALUE_UUID``
- Standoff tag: ``http://rdfh.ch/PROJECT_SHORTCODE/RESOURCE_UUID/values/VALUE_UUID/STANDOFF_UUID``
- XML-to-standoff mapping: ``http://rdfh.ch/PROJECT_SHORTCODE/mappings/MAPPING_NAME``
- XML-to-standoff mapping element: ``http://rdfh.ch/PROJECT_SHORTCODE/mappings/MAPPING_NAME/elements/MAPPING_ELEMENT_UUID``
- Project: ``http://rdfh.ch/projects/PROJECT_SHORTCODE``
- Group: ``http://rdfh.ch/groups/PROJECT_SHORTCODE/GROUP_UUID``
- Permission: ``http://rdfh.ch/permissions/PROJECT_SHORTCODE/PERMISSION_UUID``
- Lists: ``http://rdfh.ch/lists/PROJECT_SHORTCODE/LIST_UUID``
- User: ``http://rdfh.ch/users/USER_UUID``


.. _permalinks:

Knora Resource Permalinks
-------------------------

TODO: document the use of ARK_ permalinks for Knora resources.

.. _DaSCH: http://dasch.swiss/
.. _NCName: https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName
.. _URI Namespaces: https://www.w3.org/2001/sw/BestPractices/VM/http-examples/2006-01-18/#naming
.. _UUID: https://tools.ietf.org/html/rfc4122
.. _Base64: https://tools.ietf.org/html/rfc4648
.. _ARK: https://tools.ietf.org/html/draft-kunze-ark-18
