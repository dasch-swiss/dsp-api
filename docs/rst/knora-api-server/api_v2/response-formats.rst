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

------------------
Resources Sequence
------------------

For the sake of consistency, the same response format, ``ResourcesSequence``, is used whenever one or more resources
are returned in an API response. This means that different API operations give responses with the same structure, which represent different views on the data depending on which API route was used (context).

For example, a resource request returns a resource and all its properties. In a full-text search, the resource is returned with the properties that matched the search criteria.

A response to an extended search may represent a whole graph of interconnected resources.

Basically, resources are returned in form of an ordered sequence, including information about how many elements the list has. Dependent resources, i.e. resources that are referred to by other resources on the top level, are nested in link values.

See interface ``ResourcesSequence`` in module ``ResourcesResponse`` (exists for both API schemas: ``ApiV2Simple`` and ``ApiV2WithValueObjects``).
