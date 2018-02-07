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

.. _querying-and-creating-ontologies-v2:

Querying, Creating, and Updating Ontologies
===========================================

.. contents:: :local:

Querying Ontology Information
-----------------------------

Before reading this document, you should have a basic understanding of Knora API v2 external ontology schemas
(see :ref:`knora-iris-v2`).

Knora uses a single JSON-LD response format to represent ontology information, regardless of the request.
Clients that update ontology information also submit their updates in this same format. The basic structure
of this format is a JSON-LD document containing, at the top level, the property ``knora-api:hasOntologies``,
whose object can be a single ontology or an array of ontologies.

This response format uses prefixes to shorten IRIs, making them more human-readable. A client may wish to
convert these to full IRIs for processing. This can be done using a library that implements the
`JSON-LD API`_, by compacting the document with an empty context.

Querying Ontology Metadata
^^^^^^^^^^^^^^^^^^^^^^^^^^

To get metadata about all ontologies:

::

    HTTP GET to http://host/v2/ontologies/metadata

The response is in the default API v2 schema. Sample response:

.. code-block:: jsonld

   {
     "knora-api:hasOntologies" : [ {
       "@id" : "http://0.0.0.0:3333/ontology/00FF/images/v2",
       "@type" : "http://www.w3.org/2002/07/owl#Ontology",
       "rdfs:label" : "The images demo ontology"
     }, {
       "@id" : "http://0.0.0.0:3333/ontology/incunabula/v2",
       "@type" : "http://www.w3.org/2002/07/owl#Ontology",
       "rdfs:label" : "The incunabula ontology"
     }, {
       "@id" : "http://0.0.0.0:3333/ontology/anything/v2",
       "@type" : "http://www.w3.org/2002/07/owl#Ontology",
       "rdfs:label" : "The anything ontology",
       "knora-api:lastModificationDate" : "2017-12-19T15:23:42.166Z"
     }, {
       "@id" : "http://api.knora.org/ontology/knora-api/v2",
       "@type" : "http://www.w3.org/2002/07/owl#Ontology",
       "rdfs:label" : "The default knora-api ontology"
     } ],
     "@context" : {
       "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
       "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
     }
   }

To get metadata about the ontologies that belong to a particular project:

::

    HTTP GET to http://host/v2/ontologies/metadata/PROJECT_IRI

The project IRI must be URL-encoded. Example response for the ``anything`` test project
(project IRI ``http://rdfh.ch/projects/anything``):

.. code-block:: jsonld

   {
     "knora-api:hasOntologies" : {
       "@id" : "http://0.0.0.0:3333/ontology/anything/v2",
       "@type" : "http://www.w3.org/2002/07/owl#Ontology",
       "rdfs:label" : "The anything ontology",
       "knora-api:lastModificationDate" : "2017-12-19T15:23:42.166Z"
     },
     "@context" : {
       "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
       "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
     }
   }

Querying an Ontology
^^^^^^^^^^^^^^^^^^^^

An ontology can be queried either by using an API route directly or by simply dereferencing
the ontology IRI. The API route is as follows:

::

    HTTP GET to http://host/v2/ontologies/allentities/ONTOLOGY_IRI

The ontology IRI must be URL-encoded, and may be in either the default or the simple schema.
The response will be in the same schema.

If the client dereferences a project-specific ontology IRI as a URL, the Knora API server running on
the hostname in the IRI will serve the ontology. For example, if the server is running on ``0.0.0.0:3333``,
the IRI ``http://0.0.0.0:3333/ontology/00FF/images/simple/v2`` can be dereferenced
to request the ``images`` sample ontology in the simple schema.

If the client dereferences a built-in Knora ontology, such as
``http://api.knora.org/ontology/knora-api/simple/v2``, there must be a Knora API server running
at ``api.knora.org`` that can serve the ontology. The DaSCH_ intends to run such as server.
For testing, you can configure your local ``/etc/hosts`` file to resolve ``api.knora.org``
as ``localhost``.


JSON-LD Representation of an Ontology in the Simple Schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The simple schema is suitable for client applications that need to read but not update data in Knora.
For example, here is the response for the ``images`` sample ontology in the
simple schema, ``http://0.0.0.0:3333/ontology/00FF/images/simple/v2`` (simplified for clarity):

.. code-block:: jsonld

   {
     "knora-api:hasOntologies" : {
       "@id" : "http://0.0.0.0:3333/ontology/00FF/images/simple/v2",
       "@type" : "owl:Ontology",
       "knora-api:hasClasses" : {
         "p00FF-images:bild" : {
           "@id" : "p00FF-images:bild",
           "@type" : "owl:Class",
           "knora-api:resourceIcon" : "bild.png",
           "rdfs:comment" : "An image of the demo image collection",
           "rdfs:label" : "Image",
           "rdfs:subClassOf" : [ "http://api.knora.org/ontology/knora-api/simple/v2#StillImageRepresentation", {
             "@type" : "owl:Restriction",
             "owl:cardinality" : 1,
             "owl:onProperty" : "http://0.0.0.0:3333/ontology/00FF/images/simple/v2#erfassungsdatum"
           }, {
             "@type" : "owl:Restriction",
             "owl:cardinality" : 1,
             "owl:onProperty" : "http://0.0.0.0:3333/ontology/00FF/images/simple/v2#description"
           }, {
             "@type" : "owl:Restriction",
             "owl:maxCardinality" : 1,
             "owl:onProperty" : "http://0.0.0.0:3333/ontology/00FF/images/simple/v2#urheber"
           }, {
             "@type" : "owl:Restriction",
             "owl:cardinality" : 1,
             "owl:onProperty" : "http://api.knora.org/ontology/knora-api/simple/v2#creationDate"
           }, {
             "@type" : "owl:Restriction",
             "owl:minCardinality" : 0,
             "owl:onProperty" : "http://api.knora.org/ontology/knora-api/simple/v2#hasStandoffLinkTo"
           }, {
             "@type" : "owl:Restriction",
             "owl:minCardinality" : 1,
             "owl:onProperty" : "http://api.knora.org/ontology/knora-api/simple/v2#hasStillImageFileValue"
           }, {
             "@type" : "owl:Restriction",
             "owl:maxCardinality" : 1,
             "owl:onProperty" : "http://api.knora.org/ontology/knora-api/simple/v2#lastModificationDate"
           }, {
             "@type" : "owl:Restriction",
             "owl:cardinality" : 1,
             "owl:onProperty" : "http://schema.org/name"
           } ]
         },
         "p00FF-images:person" : {
           "@id" : "p00FF-images:person",
           "@type" : "owl:Class",
           "knora-api:resourceIcon" : "person.png",
           "rdfs:comment" : "Person",
           "rdfs:label" : "Person",
           "rdfs:subClassOf" : [ "http://api.knora.org/ontology/knora-api/simple/v2#Resource", {
             "@type" : "owl:Restriction",
             "owl:cardinality" : 1,
             "owl:onProperty" : "http://0.0.0.0:3333/ontology/00FF/images/simple/v2#firstname"
           }, {
             "@type" : "owl:Restriction",
             "owl:cardinality" : 1,
             "owl:onProperty" : "http://0.0.0.0:3333/ontology/00FF/images/simple/v2#lastname"
           }, {
             "@type" : "owl:Restriction",
             "owl:cardinality" : 1,
             "owl:onProperty" : "http://api.knora.org/ontology/knora-api/simple/v2#creationDate"
           }, {
             "@type" : "owl:Restriction",
             "owl:minCardinality" : 0,
             "owl:onProperty" : "http://api.knora.org/ontology/knora-api/simple/v2#hasStandoffLinkTo"
           }, {
             "@type" : "owl:Restriction",
             "owl:maxCardinality" : 1,
             "owl:onProperty" : "http://api.knora.org/ontology/knora-api/simple/v2#lastModificationDate"
           }, {
             "@type" : "owl:Restriction",
             "owl:cardinality" : 1,
             "owl:onProperty" : "http://schema.org/name"
           } ]
         }
       },
       "knora-api:hasProperties" : {
         "p00FF-images:description" : {
           "@id" : "p00FF-images:description",
           "@type" : "owl:DatatypeProperty",
           "knora-api:objectType" : "http://www.w3.org/2001/XMLSchema#string",
           "knora-api:subjectType" : "http://0.0.0.0:3333/ontology/00FF/images/simple/v2#bild",
           "rdfs:label" : "Description"
         },
         "p00FF-images:erfassungsdatum" : {
           "@id" : "p00FF-images:erfassungsdatum",
           "@type" : "owl:DatatypeProperty",
           "knora-api:objectType" : "http://api.knora.org/ontology/knora-api/simple/v2#Date",
           "knora-api:subjectType" : "http://0.0.0.0:3333/ontology/00FF/images/simple/v2#bild",
           "rdfs:label" : "Date of acquisition"
         },
         "p00FF-images:firstname" : {
           "@id" : "p00FF-images:firstname",
           "@type" : "owl:DatatypeProperty",
           "knora-api:objectType" : "http://www.w3.org/2001/XMLSchema#string",
           "knora-api:subjectType" : "http://0.0.0.0:3333/ontology/00FF/images/simple/v2#person",
           "rdfs:comment" : "First name of a person",
           "rdfs:label" : "First name",
           "rdfs:subPropertyOf" : "http://api.knora.org/ontology/knora-api/simple/v2#hasValue"
         },
         "p00FF-images:lastname" : {
           "@id" : "p00FF-images:lastname",
           "@type" : "owl:DatatypeProperty",
           "knora-api:objectType" : "http://www.w3.org/2001/XMLSchema#string",
           "knora-api:subjectType" : "http://0.0.0.0:3333/ontology/00FF/images/simple/v2#person",
           "rdfs:comment" : "Last name of a person",
           "rdfs:label" : "Name",
           "rdfs:subPropertyOf" : "http://api.knora.org/ontology/knora-api/simple/v2#hasValue"
         },
         "p00FF-images:urheber" : {
           "@id" : "p00FF-images:urheber",
           "@type" : "owl:ObjectProperty",
           "knora-api:objectType" : "http://0.0.0.0:3333/ontology/00FF/images/simple/v2#person",
           "knora-api:subjectType" : "http://0.0.0.0:3333/ontology/00FF/images/simple/v2#bild",
           "rdfs:comment" : "An entity primarily responsible for making the resource. Examples of a Creator include a person, an organization, or a service. Typically, the name of a Creator should be used to indicate the entity.",
           "rdfs:label" : "Creator",
           "rdfs:subPropertyOf" : "http://api.knora.org/ontology/knora-api/simple/v2#hasLinkTo"
         }
       },
       "knora-api:hasStandoffClasses" : { },
       "knora-api:hasStandoffProperties" : { },
       "rdfs:label" : "The images demo ontology"
     },
     "@context" : {
       "p00FF-images" : "http://0.0.0.0:3333/ontology/00FF/images/simple/v2#",
       "knora-api" : "http://api.knora.org/ontology/knora-api/simple/v2#",
       "owl" : "http://www.w3.org/2002/07/owl#",
       "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
       "xsd" : "http://www.w3.org/2001/XMLSchema#"
     }
   }

This response format has several sections: ``knora-api:hasClasses``, ``knora-api:hasProperties``,
``knora-api:hasStandoffClasses``, and ``knora-api:hasStandoffProperties``.

In a class definition, cardinalities for properties of the class are represented as in OWL,
using objects of type ``owl:Restriction``. The supported cardinalities are the ones indicated
in :ref:`knora-base-cardinalities`.

The class definitions include cardinalities that are directly defined on each class,
as well as cardinalities inherited from base classes. For example, we can see cardinalities
inherited from ``knora-api:Resource``, such as ``knora-api:hasStandoffLinkTo`` and ``http://schema.org/name``
(which represents ``rdfs:label``).

In the simple schema, Knora value properties can be datatype properties. The ``knora-base:objectType`` of a
Knora value property such as ``p00FF-images:description`` is a literal datatype, in this case ``xsd:string``.
This means that it would be possible for ``p00FF-images:description`` to be a subproperty of some standard property,
such as ``dcterms:abstract``, whose object is expected to be a literal value. A client that understands
``rdfs:subPropertyOf``, and is familiar with ``dcterms:abstract``, would then find that objects of
``p00FF-images:description`` have the expected datatype.

By default, values for ``rdfs:label`` and ``rdfs:comment`` are returned only in the user's preferred
language, or in the system default language. To obtain these values in all available languages, add
the URL parameter ``?allLanguages=true``. For example, with this parameter, the definition
of ``p00FF-images:titel`` becomes:

.. code-block:: jsonld

   {
      "@id" : "p00FF-images:titel",
      "@type" : "owl:DatatypeProperty",
      "knora-api:objectType" : "http://www.w3.org/2001/XMLSchema#string",
      "knora-api:subjectType" : "http://0.0.0.0:3333/ontology/00FF/images/simple/v2#bild",
      "rdfs:label" : [ {
       "@language" : "en",
       "@value" : "Title"
      }, {
       "@language" : "de",
       "@value" : "Titel"
      }, {
       "@language" : "fr",
       "@value" : "Titre"
      }, {
       "@language" : "it",
       "@value" : "Titolo"
      } ],
      "rdfs:subPropertyOf" : "http://api.knora.org/ontology/knora-api/simple/v2#hasValue"
   }

To find out more about the ``knora-api`` entities used in the response, the client can request
the ``knora-api`` ontology in the simple schema: ``http://api.knora.org/ontology/knora-api/simple/v2``.
For example, ``p00FF-images:erfassungsdatum`` has a ``knora-api:objectType`` of ``knora-api:Date``,
which is a subtype of ``xsd:string`` with a Knora-specific, human-readable format. In the ``knora-api``
simple ontology, there is a definition of this type:

.. code-block:: jsonld

   {
     "@id" : "knora-api:Date",
     "@type" : "rdfs:Datatype",
     "rdfs:comment" : "Represents a date as a period with different possible precisions.",
     "rdfs:label" : "Date literal",
     "rdfs:subClassOf" : {
       "@type" : "rdfs:Datatype",
       "owl:onDatatype" : "http://www.w3.org/2001/XMLSchema#string",
       "owl:withRestrictions" : {
         "xsd:pattern" : "(GREGORIAN|JULIAN):\\d{1,4}(-\\d{1,2}(-\\d{1,2})?)?( BC| AD| BCE| CE)?(:\\d{1,4}(-\\d{1,2}(-\\d{1,2})?)?( BC| AD| BCE| CE)?)?"
       }
     }
   }

JSON-LD Representation of an Ontology in the Default Schema
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The default schema is suitable for client applications that need to update data in Knora.
For example, here is the response for the ``images`` sample ontology in the
default schema, ``http://0.0.0.0:3333/ontology/00FF/images/v2`` (simplified for clarity):

.. code-block:: jsonld

   {
     "knora-api:hasOntologies" : {
       "@id" : "http://0.0.0.0:3333/ontology/00FF/images/v2",
       "@type" : "owl:Ontology",
       "knora-api:hasClasses" : {
         "p00FF-images:bild" : {
           "@id" : "p00FF-images:bild",
           "@type" : "owl:Class",
           "knora-api:canBeInstantiated" : true,
           "knora-api:resourceIcon" : "bild.png",
           "rdfs:comment" : "An image of the demo image collection",
           "rdfs:label" : "Image",
           "rdfs:subClassOf" : [ "http://api.knora.org/ontology/knora-api/v2#StillImageRepresentation", {
             "@type" : "owl:Restriction",
             "owl:cardinality" : 1,
             "owl:onProperty" : "http://0.0.0.0:3333/ontology/00FF/images/v2#erfassungsdatum"
           }, {
             "@type" : "owl:Restriction",
             "owl:cardinality" : 1,
             "owl:onProperty" : "http://0.0.0.0:3333/ontology/00FF/images/v2#description"
           }, {
             "@type" : "owl:Restriction",
             "owl:maxCardinality" : 1,
             "owl:onProperty" : "http://0.0.0.0:3333/ontology/00FF/images/v2#urheber"
           }, {
             "@type" : "owl:Restriction",
             "owl:maxCardinality" : 1,
             "owl:onProperty" : "http://0.0.0.0:3333/ontology/00FF/images/v2#urheberValue"
           }, {
             "@type" : "owl:Restriction",
             "knora-api:isInherited" : true,
             "owl:cardinality" : 1,
             "owl:onProperty" : "http://api.knora.org/ontology/knora-api/v2#creationDate"
           }, {
             "@type" : "owl:Restriction",
             "knora-api:isInherited" : true,
             "owl:cardinality" : 1,
             "owl:onProperty" : "http://api.knora.org/ontology/knora-api/v2#hasPermissions"
           }, {
             "@type" : "owl:Restriction",
             "knora-api:isInherited" : true,
             "owl:minCardinality" : 0,
             "owl:onProperty" : "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkTo"
           }, {
             "@type" : "owl:Restriction",
             "knora-api:isInherited" : true,
             "owl:minCardinality" : 0,
             "owl:onProperty" : "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkToValue"
           }, {
             "@type" : "owl:Restriction",
             "knora-api:isInherited" : true,
             "owl:minCardinality" : 1,
             "owl:onProperty" : "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue"
           }, {
             "@type" : "owl:Restriction",
             "knora-api:isInherited" : true,
             "owl:maxCardinality" : 1,
             "owl:onProperty" : "http://api.knora.org/ontology/knora-api/v2#lastModificationDate"
           }, {
             "@type" : "owl:Restriction",
             "knora-api:isInherited" : true,
             "owl:cardinality" : 1,
             "owl:onProperty" : "http://schema.org/name"
           } ]
         },
         "p00FF-images:person" : {
           "@id" : "p00FF-images:person",
           "@type" : "owl:Class",
           "knora-api:canBeInstantiated" : true,
           "knora-api:resourceIcon" : "person.png",
           "rdfs:comment" : "Person",
           "rdfs:label" : "Person",
           "rdfs:subClassOf" : [ "http://api.knora.org/ontology/knora-api/v2#Resource", {
             "@type" : "owl:Restriction",
             "owl:cardinality" : 1,
             "owl:onProperty" : "http://0.0.0.0:3333/ontology/00FF/images/v2#firstname"
           }, {
             "@type" : "owl:Restriction",
             "owl:cardinality" : 1,
             "owl:onProperty" : "http://0.0.0.0:3333/ontology/00FF/images/v2#lastname"
           }, {
             "@type" : "owl:Restriction",
             "knora-api:isInherited" : true,
             "owl:cardinality" : 1,
             "owl:onProperty" : "http://api.knora.org/ontology/knora-api/v2#creationDate"
           }, {
             "@type" : "owl:Restriction",
             "knora-api:isInherited" : true,
             "owl:cardinality" : 1,
             "owl:onProperty" : "http://api.knora.org/ontology/knora-api/v2#hasPermissions"
           }, {
             "@type" : "owl:Restriction",
             "knora-api:isInherited" : true,
             "owl:minCardinality" : 0,
             "owl:onProperty" : "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkTo"
           }, {
             "@type" : "owl:Restriction",
             "knora-api:isInherited" : true,
             "owl:minCardinality" : 0,
             "owl:onProperty" : "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkToValue"
           }, {
             "@type" : "owl:Restriction",
             "knora-api:isInherited" : true,
             "owl:maxCardinality" : 1,
             "owl:onProperty" : "http://api.knora.org/ontology/knora-api/v2#lastModificationDate"
           }, {
             "@type" : "owl:Restriction",
             "knora-api:isInherited" : true,
             "owl:cardinality" : 1,
             "owl:onProperty" : "http://schema.org/name"
           } ]
         }
       },
       "knora-api:hasProperties" : {
         "p00FF-images:description" : {
           "@id" : "p00FF-images:description",
           "@type" : "owl:ObjectProperty",
           "knora-api:isEditable" : true,
           "knora-api:objectType" : "http://api.knora.org/ontology/knora-api/v2#TextValue",
           "knora-api:subjectType" : "http://0.0.0.0:3333/ontology/00FF/images/v2#bild",
           "rdfs:label" : "Description",
           "rdfs:subPropertyOf" : "http://api.knora.org/ontology/dc/v2#description"
         },
         "p00FF-images:erfassungsdatum" : {
           "@id" : "p00FF-images:erfassungsdatum",
           "@type" : "owl:ObjectProperty",
           "knora-api:isEditable" : true,
           "knora-api:objectType" : "http://api.knora.org/ontology/knora-api/v2#DateValue",
           "knora-api:subjectType" : "http://0.0.0.0:3333/ontology/00FF/images/v2#bild",
           "rdfs:label" : "Date of acquisition"
         },
         "p00FF-images:firstname" : {
           "@id" : "p00FF-images:firstname",
           "@type" : "owl:ObjectProperty",
           "knora-api:isEditable" : true,
           "knora-api:objectType" : "http://api.knora.org/ontology/knora-api/v2#TextValue",
           "knora-api:subjectType" : "http://0.0.0.0:3333/ontology/00FF/images/v2#person",
           "rdfs:comment" : "First name of a person",
           "rdfs:label" : "First name",
           "rdfs:subPropertyOf" : "http://api.knora.org/ontology/knora-api/v2#hasValue"
         },
         "p00FF-images:lastname" : {
           "@id" : "p00FF-images:lastname",
           "@type" : "owl:ObjectProperty",
           "knora-api:isEditable" : true,
           "knora-api:objectType" : "http://api.knora.org/ontology/knora-api/v2#TextValue",
           "knora-api:subjectType" : "http://0.0.0.0:3333/ontology/00FF/images/v2#person",
           "rdfs:comment" : "Last name of a person",
           "rdfs:label" : "Name",
           "rdfs:subPropertyOf" : "http://api.knora.org/ontology/knora-api/v2#hasValue"
         },
         "p00FF-images:urheber" : {
           "@id" : "p00FF-images:urheber",
           "@type" : "owl:ObjectProperty",
           "knora-api:isEditable" : true,
           "knora-api:isLinkProperty" : true,
           "knora-api:objectType" : "http://0.0.0.0:3333/ontology/00FF/images/v2#person",
           "knora-api:subjectType" : "http://0.0.0.0:3333/ontology/00FF/images/v2#bild",
           "rdfs:comment" : "An entity primarily responsible for making the resource. Examples of a Creator include a person, an organization, or a service. Typically, the name of a Creator should be used to indicate the entity.",
           "rdfs:label" : "Creator",
           "rdfs:subPropertyOf" : "http://api.knora.org/ontology/knora-api/v2#hasLinkTo"
         },
         "p00FF-images:urheberValue" : {
           "@id" : "p00FF-images:urheberValue",
           "@type" : "owl:ObjectProperty",
           "knora-api:isEditable" : true,
           "knora-api:isLinkValueProperty" : true,
           "knora-api:objectType" : "http://api.knora.org/ontology/knora-api/v2#LinkValue",
           "knora-api:subjectType" : "http://0.0.0.0:3333/ontology/00FF/images/v2#bild",
           "rdfs:comment" : "An entity primarily responsible for making the resource. Examples of a Creator include a person, an organization, or a service. Typically, the name of a Creator should be used to indicate the entity.",
           "rdfs:label" : "Creator",
           "rdfs:subPropertyOf" : "http://api.knora.org/ontology/knora-api/v2#hasLinkToValue"
         }
       },
       "knora-api:hasStandoffClasses" : { },
       "knora-api:hasStandoffProperties" : { },
       "rdfs:label" : "The images demo ontology"
     },
     "@context" : {
       "p00FF-images" : "http://0.0.0.0:3333/ontology/00FF/images/v2#",
       "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
       "owl" : "http://www.w3.org/2002/07/owl#",
       "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
       "xsd" : "http://www.w3.org/2001/XMLSchema#"
     }
   }

In the default schema, all Knora value properties are object properties, whose
objects are IRIs, each of which uniquely identifies a value that contains metadata and can
potentially be edited. The ``knora-base:objectType`` of a Knora value property such as
``p00FF-images:description`` is a Knora value class, in this case ``knora-api:TextValue``.
Similarly, ``p00FF-images:erfassungsdatum`` has a ``knora-api:objectType`` of ``knora-api:DateValue``,
which has a more complex structure than the ``knora-api:Date`` datatype shown in the previous section.
A client can find out more about these value classes by requesting the ``knora-api`` ontology in the
default schema, ``http://api.knora.org/ontology/knora-api/v2``.

Moreover, additional information is provided in the default schema, to help clients that wish to create
or update resources and values. A class that can be instantiated is identified with
the boolean property ``knora-api:canBeInstantiated``, to distinguish it from built-in abstract
classes. Properties whose values can be edited by clients are identified with ``knora-api:isEditable``,
to distinguish them from properties whose values are maintained automatically by the
Knora API server. Link value properties are shown along with link properties, because a client
that updates links will need the IRIs of their link values.

Ontology Updates
----------------

The ontology update API must ensure that the ontologies it creates are valid and consistent, and that existing
data is not invalidated by a change to an ontology. To make this easier to enforce, the ontology update API
allows only one entity to be created or modified at a time. It is not possible to submit an entire ontology all
at once. In most cases, an update request is a JSON-LD document containing ``knora-api:hasOntologies``,
providing only the information that is relevant to the update.

Moreover, the API enforces the following rules:

- An entity (i.e. a class or property) cannot be referred to until it has been created.

- An entity cannot be modified or deleted if it is used in data, except for changes to its
  ``rdfs:label`` or ``rdfs:comment``.

- An entity cannot be modified if another entity refers to it, with one exception: a ``knora-api:subjectType`` or
  ``knora-api:objectType`` that refers to a class will not prevent the class's cardinalities from being modified.

Because of these rules, some operations have to be done in a specific order:

- Properties have to be defined before they can be used in the cardinalities of a class,
  but a property's ``knora-api:subjectType`` cannot refer to a class that does not yet exist. The recommended
  approach is to first create a class with no cardinalities, then create the properties that it needs,
  then add cardinalities for those properties to the class.

- To delete a class along with its properties, the client must first remove the cardinalities
  from the class, then delete the property definitions, then delete the class definition.

When changing an existing ontology, the client must always supply the ontology's ``knora-api:lastModificationDate``,
which is returned in the response to each update. If user A attempts to update an ontology, but user B
has already updated it since the last time user A received the ontology's ``knora-api:lastModificationDate``,
user A's update will be rejected with an HTTP 409 Conflict error. This means that it is possible for two different
users to work concurrently on the same ontology, but this is discouraged since it is likely to lead to confusion.

An ontology can be created or updated only by a system administrator, or by a project administrator in the
ontology's project.

Ontology updates always use the default schema.

Creating a New Ontology
^^^^^^^^^^^^^^^^^^^^^^^

An ontology is always created within a particular project. This is the only ontology update request in which
the client submits a JSON-LD document that does not contain ``knora-api:hasOntologies``.

::

    HTTP POST to http://host/v2/ontologies

.. code-block:: jsonld

   {
       "knora-api:ontologyName": "ONTOLOGY_NAME",
       "knora-api:projectIri": "PROJECT_IRI",
       "rdfs:label": "ONTOLOGY_NAME",
       "@context": {
           "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
           "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
       }
   }

The ontology name must follow the rules given in :ref:`knora-iris-v2`.

A successful response will be a JSON-LD document containing ``knora-api:hasOntologies``,
providing only the ontology's metadata, which includes the ontology's IRI. When the client
makes further requests to create entities (classes and properties) in the ontology, it must
construct entity IRIs by concatenating the ontology IRI, a ``#`` character, and the
entity name. An entity name must be a valid XML NCName_.

Changing an Ontology's Metadata
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Currently, the only modifiable ontology metadata is the ontology's ``rdfs:label``.

::

    HTTP PUT to http://host/v2/ontologies/metadata

.. code-block:: jsonld

  {
    "knora-api:hasOntologies": {
      "@id": "ONTOLOGY_IRI",
      "rdfs:label": "NEW_ONTOLOGY_LABEL",
      "knora-api:lastModificationDate": "ONTOLOGY_LAST_MODIFICATION_DATE"
    },
    "@context": {
      "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
      "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
    }
  }

A successful response will be a JSON-LD document containing ``knora-api:hasOntologies``,
providing only the ontology's metadata.

Creating a Class Without Cardinalities
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

::

    HTTP POST to http://host/v2/ontologies/classes

.. code-block:: jsonld

   {
     "knora-api:hasOntologies" : {
       "@id" : "ONTOLOGY_IRI",
       "@type" : "owl:Ontology",
       "knora-api:hasClasses" : {
         "CLASS_IRI" : {
           "@id" : "CLASS_IRI",
           "@type" : "owl:Class",
           "rdfs:label" : {
             "@language" : "LANGUAGE_CODE",
             "@value" : "LABEL"
           },
           "rdfs:comment" : {
             "@language" : "LANGUAGE_CODE",
             "@value" : "COMMENT"
           },
           "rdfs:subClassOf" : "BASE_CLASS_IRI"
         }
       },
       "knora-api:lastModificationDate" : "ONTOLOGY_LAST_MODIFICATION_DATE"
     },
     "@context" : {
       "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
       "owl" : "http://www.w3.org/2002/07/owl#",
       "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
       "xsd" : "http://www.w3.org/2001/XMLSchema#"
     }
   }

Values for ``rdfs:label`` and ``rdfs:comment`` must be submitted in at least one language,
either as an object or as an array of objects.

At least one base class must be provided, which can be ``knora-api:Resource`` or any of its subclasses.

A successful response will be a JSON-LD document containing ``knora-api:hasOntologies``,
providing the new class definition (but not any of the other entities in the ontology).


Creating a Class With Cardinalities
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This can work if the new class will have cardinalities for properties that have
no ``knora-api:subjectType``, or if the new class will be a subclass of their
``knora-api:subjectType``.

::

    HTTP POST to http://host/v2/ontologies/classes

.. code-block:: jsonld

   {
     "knora-api:hasOntologies" : {
       "@id" : "ONTOLOGY_IRI",
       "@type" : "owl:Ontology",
       "knora-api:hasClasses" : {
         "CLASS_IRI" : {
           "@id" : "CLASS_IRI",
           "@type" : "owl:Class",
           "rdfs:label" : {
             "@language" : "LANGUAGE_CODE",
             "@value" : "LABEL"
           },
           "rdfs:comment" : {
             "@language" : "LANGUAGE_CODE",
             "@value" : "COMMENT"
           },
           "rdfs:subClassOf" : [
               "BASE_CLASS_IRI",
               {
                   "@type": "http://www.w3.org/2002/07/owl#Restriction",
                   "OWL_CARDINALITY_PREDICATE": "OWL_CARDINALITY_VALUE",
                   "owl:onProperty": "PROPERTY_IRI"
               }
           ]
         }
       },
       "knora-api:lastModificationDate" : "ONTOLOGY_LAST_MODIFICATION_DATE"
     },
     "@context" : {
       "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
       "owl" : "http://www.w3.org/2002/07/owl#",
       "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
       "xsd" : "http://www.w3.org/2001/XMLSchema#"
     }
   }

``OWL_CARDINALITY_PREDICATE`` and ``OWL_CARDINALITY_VALUE`` must correspond
to the supported combinations given in :ref:`knora-base-cardinalities`.
(The placeholder ``OWL_CARDINALITY_VALUE`` is shown here in quotes, but it should
be an unquoted integer.)

Values for ``rdfs:label`` and ``rdfs:comment`` must be submitted in at least one language,
either as an object or as an array of objects.

At least one base class must be provided.

A successful response will be a JSON-LD document containing ``knora-api:hasOntologies``,
providing the new class definition (but not any of the other entities in the ontology).

Changing the Labels of a Class
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This operation is permitted even if the class is used in data.

::

    HTTP PUT to http://host/v2/ontologies/classes

.. code-block:: jsonld

   {
     "knora-api:hasOntologies" : {
       "@id" : "ONTOLOGY_IRI",
       "@type" : "owl:Ontology",
       "knora-api:hasClasses" : {
         "CLASS_IRI" : {
           "@id" : "CLASS_IRI",
           "@type" : "owl:Class",
           "rdfs:label" : {
             "@language" : "LANGUAGE_CODE",
             "@value" : "LABEL"
           }
         }
       },
       "knora-api:lastModificationDate" : "ONTOLOGY_LAST_MODIFICATION_DATE"
     },
     "@context" : {
       "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
       "owl" : "http://www.w3.org/2002/07/owl#",
       "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
       "xsd" : "http://www.w3.org/2001/XMLSchema#"
     }
   }

Values for ``rdfs:label`` must be submitted in at least one language,
either as an object or as an array of objects. The submitted labels will
replace the existing ones.

Changing the Comments of a Class
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This operation is permitted even if the class is used in data.

::

    HTTP PUT to http://host/v2/ontologies/classes

.. code-block:: jsonld

   {
     "knora-api:hasOntologies" : {
       "@id" : "ONTOLOGY_IRI",
       "@type" : "owl:Ontology",
       "knora-api:hasClasses" : {
         "CLASS_IRI" : {
           "@id" : "CLASS_IRI",
           "@type" : "owl:Class",
           "rdfs:comment" : {
             "@language" : "LANGUAGE_CODE",
             "@value" : "COMMENT"
           }
         }
       },
       "knora-api:lastModificationDate" : "ONTOLOGY_LAST_MODIFICATION_DATE"
     },
     "@context" : {
       "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
       "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
       "owl" : "http://www.w3.org/2002/07/owl#",
       "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
       "xsd" : "http://www.w3.org/2001/XMLSchema#"
     }
   }

Values for ``rdfs:comment`` must be submitted in at least one language,
either as an object or as an array of objects. The submitted comments will
replace the existing ones.

Creating a Property
^^^^^^^^^^^^^^^^^^^

::

    HTTP POST to http://host/v2/ontologies/properties

.. code-block:: jsonld

   {
     "knora-api:hasOntologies" : {
       "@id" : "ONTOLOGY_IRI",
       "@type" : "owl:Ontology",
       "knora-api:hasProperties" : {
         "PROPERTY_IRI" : {
           "@id" : "PROPERTY_IRI",
           "@type" : "owl:ObjectProperty",
           "knora-api:subjectType" : "SUBJECT_TYPE",
           "knora-api:objectType" : "OBJECT_TYPE",
           "rdfs:label" : {
             "@language" : "LANGUAGE_CODE",
             "@value" : "LABEL"
           },
           "rdfs:comment" : {
             "@language" : "LANGUAGE_CODE",
             "@value" : "COMMENT"
           },
           "rdfs:subPropertyOf" : "BASE_PROPERTY_IRI"
         }
       },
       "knora-api:lastModificationDate" : "ONTOLOGY_LAST_MODIFICATION_DATE"
     },
     "@context" : {
       "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
       "owl" : "http://www.w3.org/2002/07/owl#",
       "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
       "xsd" : "http://www.w3.org/2001/XMLSchema#"
     }
   }

Values for ``rdfs:label`` and ``rdfs:comment`` must be submitted in at least one language,
either as an object or as an array of objects.

At least one base property must be provided, which can be ``knora-api:hasValue``, ``knora-api:hasLinkTo``,
or any of their subproperties, with the exception of file properties (subproperties of ``knora-api:hasFileValue``)
and link value properties (subproperties of ``knora-api:hasLinkToValue``).

The property definition must specify its ``knora-api:objectType``. If the new property is a subproperty
of ``knora-api:hasValue``, its ``knora-api:objectType`` must be one of the built-in subclasses
of ``knora-api:Value``, which are defined in the ``knora-api`` ontology in the default schema.
If the new property is a subproperty of ``knora-base:hasLinkTo``, its ``knora-api:objectType`` must
be a subclass of ``knora-api:Resource``.

To improve consistency checking, it is recommended, but not required, to provide ``knora-api:subjectType``,
which must be a subclass of ``knora-api:Resource``.

A successful response will be a JSON-LD document containing ``knora-api:hasOntologies``,
providing the new property definition (but not any of the other entities in the ontology).

Changing the Labels of a Property
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This operation is permitted even if the property is used in data.

::

    HTTP PUT to http://host/v2/ontologies/properties

.. code-block:: jsonld

   {
     "knora-api:hasOntologies" : {
       "@id" : "ONTOLOGY_IRI",
       "@type" : "owl:Ontology",
       "knora-api:hasProperties" : {
         "PROPERTY_IRI" : {
           "@id" : "PROPERTY_IRI",
           "@type" : "owl:ObjectProperty",
           "rdfs:label" : {
             "@language" : "LANGUAGE_CODE",
             "@value" : "LABEL"
           }
         }
       },
       "knora-api:lastModificationDate" : "ONTOLOGY_LAST_MODIFICATION_DATE"
     },
     "@context" : {
       "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
       "owl" : "http://www.w3.org/2002/07/owl#",
       "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
       "xsd" : "http://www.w3.org/2001/XMLSchema#"
     }
   }

Values for ``rdfs:label`` must be submitted in at least one language, either as an object
or as an array of objects.

Changing the Comments of a Property
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This operation is permitted even if the property is used in data.

::

    HTTP PUT to http://host/v2/ontologies/properties

.. code-block:: jsonld

   {
     "knora-api:hasOntologies" : {
       "@id" : "ONTOLOGY_IRI",
       "@type" : "owl:Ontology",
       "knora-api:hasProperties" : {
         "PROPERTY_IRI" : {
           "@id" : "PROPERTY_IRI",
           "@type" : "owl:ObjectProperty",
           "rdfs:comment" : {
             "@language" : "LANGUAGE_CODE",
             "@value" : "COMMENT"
           }
         }
       },
       "knora-api:lastModificationDate" : "ONTOLOGY_LAST_MODIFICATION_DATE"
     },
     "@context" : {
       "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
       "owl" : "http://www.w3.org/2002/07/owl#",
       "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
       "xsd" : "http://www.w3.org/2001/XMLSchema#"
     }
   }

Values for ``rdfs:comment`` must be submitted in at least one language, either as an object
or as an array of objects.

Adding Cardinalities to a Class
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This operation is not permitted if the class is used in data, or if it has
a subclass.

::

    HTTP POST to http://host/v2/ontologies/cardinalities

.. code-block:: jsonld

   {
     "knora-api:hasOntologies" : {
       "@id" : "ONTOLOGY_IRI",
       "@type" : "owl:Ontology",
       "knora-api:hasClasses" : {
         "CLASS_IRI" : {
           "@id" : "CLASS_IRI",
           "@type" : "owl:Class",
           "rdfs:subClassOf" : [
               {
                   "@type": "http://www.w3.org/2002/07/owl#Restriction",
                   "OWL_CARDINALITY_PREDICATE": "OWL_CARDINALITY_VALUE",
                   "owl:onProperty": "PROPERTY_IRI"
               }
           ]
         }
       },
       "knora-api:lastModificationDate" : "ONTOLOGY_LAST_MODIFICATION_DATE"
     },
     "@context" : {
       "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
       "owl" : "http://www.w3.org/2002/07/owl#",
       "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
       "xsd" : "http://www.w3.org/2001/XMLSchema#"
     }
   }

At least one cardinality must be submitted.

``OWL_CARDINALITY_PREDICATE`` and ``OWL_CARDINALITY_VALUE`` must correspond
to the supported combinations given in :ref:`knora-base-cardinalities`.
(The placeholder ``OWL_CARDINALITY_VALUE`` is shown here in quotes, but it should
be an unquoted integer.)

A successful response will be a JSON-LD document containing ``knora-api:hasOntologies``,
providing the new class definition (but not any of the other entities in the ontology).

Replacing the Cardinalities of a Class
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This removes all the cardinalities from the class and replaces them with the
submitted cardinalities. If no cardinalities are submitted (i.e. the request
contains no ``rdfs:subClassOf``), the class is left with no cardinalities.

This operation is not permitted if the class is used in data, or if it has
a subclass.

::

    HTTP PUT to http://host/v2/ontologies/cardinalities

.. code-block:: jsonld

   {
     "knora-api:hasOntologies" : {
       "@id" : "ONTOLOGY_IRI",
       "@type" : "owl:Ontology",
       "knora-api:hasClasses" : {
         "CLASS_IRI" : {
           "@id" : "CLASS_IRI",
           "@type" : "owl:Class",
           "rdfs:subClassOf" : [
               {
                   "@type": "http://www.w3.org/2002/07/owl#Restriction",
                   "OWL_CARDINALITY_PREDICATE": "OWL_CARDINALITY_VALUE",
                   "owl:onProperty": "PROPERTY_IRI"
               }
           ]
         }
       },
       "knora-api:lastModificationDate" : "ONTOLOGY_LAST_MODIFICATION_DATE"
     },
     "@context" : {
       "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
       "owl" : "http://www.w3.org/2002/07/owl#",
       "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
       "xsd" : "http://www.w3.org/2001/XMLSchema#"
     }
   }

``OWL_CARDINALITY_PREDICATE`` and ``OWL_CARDINALITY_VALUE`` must correspond
to the supported combinations given in :ref:`knora-base-cardinalities`.
(The placeholder ``OWL_CARDINALITY_VALUE`` is shown here in quotes, but it should
be an unquoted integer.)

A successful response will be a JSON-LD document containing ``knora-api:hasOntologies``,
providing the new class definition (but not any of the other entities in the ontology).

Deleting a Property
^^^^^^^^^^^^^^^^^^^

A property can be deleted only if no other ontology entity refers to it, and if it is not used in data.

::

    HTTP DELETE to http://host/v2/ontologies/properties/PROPERTY_IRI?lastModificationDate=ONTOLOGY_LAST_MODIFICATION_DATE

The property IRI and the ontology's last modification date must be URL-encoded.

A successful response will be a JSON-LD document containing ``knora-api:hasOntologies``,
providing only the ontology's metadata.

Deleting a Class
^^^^^^^^^^^^^^^^

A class can be deleted only if no other ontology entity refers to it, and if it is not used in data.

::

    HTTP DELETE to http://host/v2/ontologies/classes/CLASS_IRI?lastModificationDate=ONTOLOGY_LAST_MODIFICATION_DATE

The class IRI and the ontology's last modification date must be URL-encoded.

A successful response will be a JSON-LD document containing ``knora-api:hasOntologies``,
providing only the ontology's metadata.

.. _DaSCH: http://dasch.swiss/
.. _JSON-LD API: https://www.w3.org/TR/json-ld-api/
.. _NCName: https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName
