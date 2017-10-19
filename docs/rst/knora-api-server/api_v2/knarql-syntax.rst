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

-------------
KnarQL Syntax
-------------

Every KnarQL query is a valid SPARQL Construct query. However, KnarQL only supports a subset of the elements that can be used in a SPARQL Construct query.
Additionally, KnarQL requires the client to use explicit type annotations that are valid SPARQL, but specific to the Knora API.
Also the main resource has to be marked.

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
If a property is represented by a query variable, it can be restricted to certain IRIS using a FILTER.

Values
******

Values can only be represented by a query variable. Value literals are currently not supported.
To restrict a value, a FILTER has to be used.

Required Type Annotations
*************************

Resources, properties, and values have to be accompanied by explicit type annotations. [1]_

There are two kinds of type annotations:
 - indication of the type of a value or a resource a property points to: ``knora-api:objectType``
 - indication of the type of a resource or value instance: ``rdf:type``

Value Types
***********

KnarQL currently supports the following types of values in FILTER expressions:
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