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

Basic Concept
-------------

KnarQL is not a SPARQL endpoint, but is easy to use for anyone familiar with SPARQL. The reason why we cannot offer an open SPARQL endpoint is mainly access control.
But even more importantly, we want to facilitate accessing data stored in a triplestore by not exposing the full internal complexity of our RDF-datamodel to the outside.
This why KnarQL is kind of a hybrid between an API request and a SPARQL endpoint.
The SPARQL sent to the extended search route is simplified and will be processed by the Knora API to more complex SPARQL that is actually sent to the triplestore.

KnarQL Syntax
-------------

Every KnarQL query is a valid SPARQL Construct query. However, KnarQL only supports a subset of the elements that can be used in a SPARQL Construct query.
Additionally, KnarQL requires the client to use some type annotations that are valid SPARQL, but specific to the Knora API.

******************
Supported Elements
******************

The current version of KnarQL allows for the following elements in a SPARQL Construct query:
 - OPTIONAL (cannot be nested in another OPTIONAL or UNION)
 - UNION (cannot be nested in another UNION)
 - FILTER: may contain a complex expression using Boolean operators AND and OR.
 - FILTER NOT EXISTS
 - OFFSET: the OFFSET is needed for paging. It does not actually refer to the amount of triples to be returned, but to the requested page of results. The default value is 0 which equals the first page of results. The amount of results per page is defined in ``app/v2`` in ``application.conf``.
 - ORDER BY: the result of a SPARQL Construct query is normally a set of triples which cannot be ordered. However, a KnarQL query does not return triples but a list of resources with values that can be ordered.

*************************
Required Type Annotations
*************************

Properties (represented both as variables and IRIs) and values (represented as variables) have to be accompanied by type annotations.
In a future version, KnarQL could possibly infer this information from the context, but for the current version we require explicit type annotations.

There are two kinds of type annotations: one indicating the type of value a property points to, and another indicating the type of a resource or value instance:
 - type annotation for a property: ``knora-api:objectType``
 - type annotation for a resource or value instance: ``rdf:type``

 In the CONSTRUCT clause of a KnarQL query, the resource the user is mainly interested in has to be marked as ``knora-api:isMainResource`` with the value Boolean literal value true.