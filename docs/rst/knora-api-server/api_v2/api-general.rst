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


Introduction: Using API V2
==========================

Version 2 of the Knora API aims to make both the response and request formats more generic and consistent.
Version 1 was basically the result of the reimplementation of the existing API of the SALSAH prototype.
Since the development of this prototype has a long history and the specification of API V1 was an evolving process, V1 manifests several inconsistencies and peculiarities.
With V2, we would like to offer a format that is consistent and hence easier to use for a client.

Please note that V2 is still in development. We do not recommend yet to use it on productive systems.

JSON-LD
-------

Our preferred format for data exchange is JSON-LD_. JSON-LD highly enhances the machine readability of responses received from the Knora API server, while applying the principle of resources with attached properties instead of sending
directly the triples from the triplestore as a SPARQL endpoint would do (and vice versa when doing updates). The main advantage of using JSON-LD in V2 over our custom JSON format in V1 is that we can use Iris to identify members of the response.
A machine can then obtain more information about these Iris, e.g., get their labels in different languages to display them to the user. Moreover, we can support entities defined in schema.org_ that are widely known and used.

We designed the V2 routes in a way that would also allow for the usage of other formats such as XML.
We plan to implement support for XML once the implementation of JSON-LD is completed.
The client will be able to use content negotiation to specify the preferred exchange format.

.. _JSON-LD: https://json-ld.org/spec/latest/json-ld/
.. _schema.org: http://www.schema.org

API Schema
----------

Knora API V2 offers the query and response format in a complex and a simplified schema.
The main difference is that the complex schema exposes the complexity of value objects to the outside while the simplified version does not.
In any of these cases, the client deals with the Knora-Api format, which is automatically converted from and to Knora-Base by the API.
This approach encapsulates the internals and adds a layer of abstraction to them.
The client will be able to use content negotiation to specify the preferred exchange format.
For more information, see :ref:`querying-and-creating-ontologies-v2`.

V2 Path Segment
---------------

Every request to API V1 includes ``v2`` as a path segment, e.g. ``http://host/v2/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a``.
Accordingly, requests to another version of the API will require another path segment.