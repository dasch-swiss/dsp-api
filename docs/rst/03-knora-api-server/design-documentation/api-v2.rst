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


Knora API v2 Design
===================

.. contents:: :local:

General Principles
------------------

- Knora API v2 requests and responses are RDF documents. Currently only `JSON-LD`_
  documents are supported. Support for XML is planned.

- Each class or property used in a request or response has a definition in an ontology, which
  the Knora API server can serve.

- Response formats are reused for different requests whenever possible, to minimise
  the number of different response formats a client has to handle. For example,
  any request for one or more resources (such as a search result, or a request for
  one specific resource) returns a response in the same format.

- Response size is limited by design. Large amounts of data must be retrieved by
  requesting small pages of data, one after the other.

- Responses that provide data are distinct from responses that provide definitions
  (i.e. ontology entities). Data responses indicate which types are used, and the
  client can request information about these types separately.

API Schemas
-----------

The types used in the triplestore are not exposed directly in the API. Instead, they are
mapped onto API 'schemas'. Two schemas are currently provided.

- A default schema, which is suitable both for reading and for editing data. The default schema
  represents values primarily as complex objects.

- A simple schema, which is suitable for reading data but not for editing it. The simple schema
  facilitates interoperability between Knora ontologies and non-Knora ontologies, since it
  represents values primarily as literals.

Each schema has its own type IRIs, which are derived from the ones used in the triplestore.
For details of these different IRI formats, see :ref:`knora-iris-v2`.


Implementation
--------------

JSON-LD Parsing and Formatting
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Each API response is represented by a class that extends ``KnoraResponseV2``, which
has a method ``toJsonLDDocument`` that specifies the target schema. It is currently
up to each route to determine what the appropriate response schema should be. Some routes
will support only one response schema. Others will allow the client to choose, and there will
be one or more standard ways for the client to specify the desired response schema.

A route calls ``RouteUtilV2.runJsonRoute``, passing a request message and a response schema.
When ``RouteUtilV2`` gets the response message from the responder, it calls ``toJsonLDDocument``
on it, specifying that schema. The response message returns a ``JsonLDDocument``, which is
a simple data structure that is then converted to Java objects and passed to the JSON-LD
Java library for formatting. In general, ``toJsonLDDocument`` is implemented in two stages:
first the object converts itself to the target schema, and then the resulting object is
converted to a ``JsonLDDocument``.

A route that receives JSON-LD requests should use ``JsonLDUtil.parseJsonLD`` to convert each
request to a ``JsonLDDocument``.


Operation Wrappers
^^^^^^^^^^^^^^^^^^

Whenever possible, the same data structures are used for input and output. Often more data is
available in output than in input. For example, when a value is read from the triplestore, its IRI
is available, but when it is being created, it does not yet have an IRI. In such cases, there is a
class like ``ValueContentV2``, which represents the data that is used both for input and for output.
When a value is read, a ``ValueContentV2`` is wrapped in a ``ReadValueV2``, which additionally
contains the value's IRI. When a value is created, it is wrapped in a ``CreateValueV2``, which has
the resource IRI and the property IRI, but not the value IRI.

A ``Read*`` wrapper can be wrapped in another ``Read*`` wrapper; for example, a ``ReadResourceV2``
contains ``ReadValueV2`` objects.

Each ``*Content*`` class should extend ``KnoraContentV2`` and thus have a ``toOntologySchema`` method
or converting itself between internal and external schemas, in either direction.

Each ``Read*`` wrapper class should have a method for converting itself to JSON-LD in a particular
external schema. If the ``Read*`` wrapper is a ``KnoraResponseV2``, this method is
``toJsonLDDocument``.


Smart IRIs
^^^^^^^^^^

Usage
~~~~~

The ``SmartIri`` trait can be used to parse and validate IRIs, and in particular for converting Knora type
IRIs between internal and external schemas. It validates each IRI it parses. To use it, import the following:

.. highlight:: scala

::

  import org.knora.webapi.util.{SmartIri, StringFormatter}
  import org.knora.webapi.util.IriConversions._

Ensure that an implicit instance of ``StringFormatter`` is in scope:

::

  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

Then, if ``iriStr`` is a string representing an IRI, you can can convert it to a ``SmartIri``
like this:

::

  val iri: SmartIri = iriStr.toSmartIri

If the IRI came from a request, use this method to throw a specific exception if the IRI
is invalid:

::

  val iri: SmartIri = iriStr.toSmartIriWithErr(
      () => throw BadRequestException(s"Invalid IRI: $iriStr")
  )

You can then use methods such as ``SmartIri.isKnoraApiV2EntityIri`` and ``SmartIri.getProjectCode``
to obtain information about the IRI. To convert it to another schema, call ``SmartIri.toOntologySchema``.
Converting a non-Knora IRI returns the same IRI.

If the IRI represents a Knora internal value class such as ``knora-base:TextValue``, converting it to
the ``ApiV2Simple`` schema will return the corresponding simplified type, such as ``xsd:string``. But this
conversion is not performed in the other direction (external to internal), since this would require
knowledge of the context in which the IRI is being used.

The performance penalty for using a ``SmartIri`` instead of a string is very small. Instances
are automatically cached once they are constructed. Parsing and caching a ``SmartIri`` instance takes
about 10-20 µs, and retrieving a cached ``SmartIri`` takes about 1 µs.

There is no advantage to using ``SmartIri`` for data IRIs, since they are not schema-specific (and are not
cached). If a data IRI has been received from a client request, it is better just to validate it using
``StringFormatter.validateAndEscapeIri``.

Implementation
~~~~~~~~~~~~~~

The smart IRI implementation, ``SmartIriImpl``, is nested in the ``StringFormatter`` class, because
it uses the Knora API server's hostname, which isn't available until the Akka ActorSystem has
started. However, this means that the type of a ``SmartIriImpl`` instance is dependent on the instance
of ``StringFormatter`` that constructed it. Therefore, instances of ``SmartIriImpl``
created by different instances of ``StringFormatter`` can't be compared directly.

There are in fact two instances of ``StringFormatter``:

- one returned by ``StringFormatter.getGeneralInstance`` which is available after Akka has started
  and has the API server's hostname (and can therefore provide ``SmartIri`` instances capable
  of parsing IRIs containing that hostname). This instance is used throughout the Knora API
  server.

- one returned by ``StringFormatter.getInstanceForConstantOntologies``, which is available before
  Akka has started, and is used only by the hard-coded constant ``knora-api`` ontologies.

This is the reason for the existence of the ``SmartIri`` trait, which is a top-level definition
and has its own ``equals`` and ``hashCode`` methods. Instances of ``SmartIri`` can thus be
compared (e.g. to use them as unique keys in collections), regardless of which instance of
``StringFormatter`` created them.


.. _JSON-LD: http://json-ld.org/
