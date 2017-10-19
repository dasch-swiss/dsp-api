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

.. _knarql-syntax-v2:

KnarQL: Knora Query Language
============================

.. contents:: :local:

-------------
Basic Concept
-------------
KnarQL is not a SPARQL endpoint, but is easy to use for anyone familiar with SPARQL. The reason why we cannot offer an open SPARQL endpoint is mainly access control.
But even more importantly, we want to facilitate accessing data stored in a triplestore by not exposing the full internal complexity of our RDF data model to the outside.
This why KnarQL is kind of a hybrid between an API and a SPARQL endpoint.
The SPARQL sent to the extended search route will be processed by the Knora API to more complex SPARQL that is actually sent to the triplestore.

****************************
Main and Dependent Resources
****************************
The main resource is the actual result of an extended search, other resources present in the response belonging to the main resource are referred to as dependent resources.
If the client asks for a resource A relating to a resource B, then all matches for A will be presented as main resources and those for B as dependent resources.

**********
Query Path
**********
The extended search returns a sub-graph of the Knora graph, representing the information from the triplestore the user is currently interested in.
This sub-graph is called the query path.
The query path may span different levels (levels of relations between resources), depending on the complexity of the given query.
In version 1 of the extended search, only one level was supported.

******************
Permission Control
******************
The client only gets to see resources and properties that he has sufficient permissions for.
Also sufficient permissions for the full query path must be provided,
i.e. if permissions for any resource or value in the query path are insufficient, the whole main resource will be suppressed.

*********
Inference
*********
KnarQL make use of the tripelstore's reasoning engine to support inference.
This means that for a given property, all its subproperties will be included too.

**********
API Schema
**********
KnarQL supports the Knora-API simple schema. See :ref:`querying-and-creating-ontologies-v2`.

-------------
KnarQL Syntax
-------------
Every KnarQL query is a valid SPARQL 1.1 CONSTRUCT_ query. However, KnarQL only supports a subset of the elements that can be used in a SPARQL Construct query.
Additionally, KnarQL requires the client to use explicit type annotations that are valid SPARQL, but specific to the Knora API.
Also the main resource has to be marked.

.. _CONSTRUCT: https://www.w3.org/TR/sparql11-query/#construct

************
WHERE Clause
************
The WHERE clause specifies the query path (the conditions that have to be met).
Resources that match the given criteria and the client has sufficient permissions on will be returned.

Supported SPARQL Elements
*************************
The current version of KnarQL allows for the following elements in a SPARQL Construct query:
 - OPTIONAL (cannot be nested in another OPTIONAL or UNION)
 - UNION (cannot be nested in another UNION)
 - FILTER (may contain a complex expression using Boolean operators AND and OR): the left argument of a compare expression must be a query variable
 - FILTER NOT EXISTS
 - OFFSET: the OFFSET is needed for paging. It does not actually refer to the amount of triples to be returned, but to the requested page of results. The default value is 0 which equals the first page of results. The amount of results per page is defined in ``app/v2`` in ``application.conf``.
 - ORDER BY: the result of a SPARQL Construct query is normally a set of triples which cannot be ordered. However, a KnarQL query does not return triples but a list of resources with values that can be ordered.

Resources
*********
Resources can be represented by an IRI or a query variable.

Properties
**********
Properties can be represented by an IRI or a query variable.
If a property is represented by a query variable, it can be restricted to certain IRIs using a FILTER.

Values
******
Values can only be represented by a query variable. Value literals are currently not supported.
To restrict a value, a FILTER has to be used.

Required Type Annotations
*************************
Resources, properties, and values have to be accompanied by explicit type annotation statements. [1]_

There are two type annotation properties:
 - ``knora-api:objectType``: indicates the type of a value or a resource a property points to.
 - ``rdf:type``: indicates the type of a resource or value instance.

Property Types
**************
A property may either point to a value or to a resource.
In the first case, it is called a value property, in the second case a linking property.
The type annotation property ``knora-api:objectType`` indicates the type of instance of a value or resource the property points to.

Value Property Types
^^^^^^^^^^^^^^^^^^^^
Supported value property types:
 - ``xsd:string``
 - ``xsd:integer``
 - ``xsd:decimal``
 - ``xsd:boolean``
 - ``knora-api:Date``
 - ``knora-api:StillImageFile``
 - ``knora-api:Geom``

Linking Property Types
^^^^^^^^^^^^^^^^^^^^^^
A linking property has to be annotated with the type ``knora-api:Resource``.
Since inference is used, this matches any resource.
To restrict the types of resources, additional statements can be made using ``rdfs:type``.
The property can also be restricted using a FILTER in case a query variable is used.

Value Types
***********
Value types are used to indicate the type of a value (``rdf:type``).
KnarQL supports the following types of value instances:

 - ``xsd:string``
 - ``xsd:integer``
 - ``xsd:decimal``
 - ``xsd:boolean``
 - ``knora-api:Date``
 - ``knora-api:StillImageFile``
 - ``knora-api:Geom``

Please note that not all of these types are supported in FILTER to restrict values.
Supported value types in FILTER:

 - ``xsd:string``
 - ``xsd:integer``
 - ``xsd:decimal``
 - ``xsd:boolean``
 - ``knora-api:Date``

****************
CONSTRUCT Clause
****************
The CONSTRUCT clause specifies how much information the response should return.
The CONSTRUCT clause may only contain triples also present in a KnarQL query's WHERE clause.

Marking of the Main Resource
****************************
In the CONSTRUCT clause of a KnarQL query, the resource the user is mainly interested in has to be marked with the property ``knora-api:isMainResource`` set to true.
The marking of the main resource is mandatory and cannot be omitted.

.. [1] In a future version, KnarQL could possibly infer this information from the context, but for the current version such annotations are required.

-----------------
KnarQL by Example
-----------------
In this section, we provide some sample queries of different complexity to illustrate the usage of KnarQL.

*************************************************
Getting all the Components of a Compound resource
*************************************************
In order to get all the components of a compound resource, the following KnarQL query can be sent to the API.

In this case, the compound resource is an ``incunabula:book`` identified by the IRI ``http://data.knora.org/c5058f3a`` and the components are of type ``incunabula:page`` (test data for the incunabula project).
Since inference is supported, we can use ``knora-api:StillImageRepresentation`` (``incunabula:page`` is one of its subclasses).
This makes the query more generic and allows for reuse (for instance, a client would like to query different types of compound resources defined in different ontologies).

ORDER BY is used to sort the components by their seqence number so they appear in the correct sequence.

OFFSET is set to 0 to get the first page of results.

Please note that the prefix ``knora-api`` refers to the Knora-Api simple schema.

::

   PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>

   CONSTRUCT {
      ?component knora-api:isMainResource true . # marking of the component searched for as the main resource, mandatory
      ?component knora-api:seqnum ?seqnum . # return the sequence number in the response
      ?component knora-api:hasStillImageFileValue ?file . # return the StillImageFile in the response
   } WHERE {
      ?component a knora-api:Resource . # explicit type annotation for the component searched for, mandatory
      ?component a knora-api:StillImageRepresentation . # additional restriction of the type of component, optional

      ?component knora-api:isPartOf <http://data.knora.org/c5058f3a> . # component relates to compound resource via this property
      knora-api:isPartOf knora-api:objectType knora-api:Resource . # type annotation for linking property, mandatory
      <http://data.knora.org/c5058f3a> a knora-api:Resource . # type annotation for compound resource, mandatory

      ?component knora-api:seqnum ?seqnum . # component must have a sequence number, no further restrictions given
      knora-api:seqnum knora-api:objectType xsd:integer . # type annotation for the value property, mandatory
      ?seqnum a xsd:integer . # type annotation for the sequence number, mandatory

      ?component knora-api:hasStillImageFileValue ?file . # component must have a StillImageFile, no further restrictions given
      knora-api:hasStillImageFileValue knora-api:objectType knora-api:StillImageFile . # type annotation for the value property, mandatory
      ?file a knora-api:StillImageFile . # type annotation for the StillImageFile, mandatory
   }
   ORDER BY ASC(?seqnum) # order by sequence number, ascending
   OFFSET 0 #get first page of results


The ``incunabula:book`` with the IRI ``http://data.knora.org/c5058f3a`` has 402 pages (this result can be obtained by doing a count query, see :ref:`reading-and-searching-resources-v2`).
However, only the first page of results is returned with OFFSET set to 0. The same query can be sent again with OFFSET set to 1 to get the next page of results and so forth.
Once a page does not contain the full possible amount of results (see settings in ``app/v2`` in ``application.conf``) or is empty, no more results are available.

Let's assume the client is not interested in all of the book's pages, but just in first 10 of them. In that case, the sequence number can be restricted using a FILTER that is added to the query's WHERE clause:

::

   FILTER(?seqnum <= 10)

The first page starts with sequence number 1, so with this FILTER only the first ten pages are returned.

