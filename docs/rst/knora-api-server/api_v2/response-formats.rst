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

.. _response-formats-v2:

V2 Response Formats
===================

.. contents:: :local:

-------------------------
Resources Response Format
-------------------------

In order to make the API V2 more consistent, a generic response format is used wherever one or more instances of a resource are queried.
This means that different API operations return the answer in the same structure, still they represent different views on the data depending on which API route was used (context).
For example, a resource request returns a resource and all its properties. In a fulltext search, the resource is returned with the properties that matched the search criteria.

Basically, resources are returned in form of an ordered sequence, including the information how many elements the list has.
Dependent resources, i.e. resources that are referred to by other resources on the top level, are nested in link values.


