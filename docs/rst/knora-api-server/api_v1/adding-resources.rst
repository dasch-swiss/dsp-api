.. Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
   Tobias Schweizer, André Kilchenmann, and André Fatton.

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

.. _adding-resources:

Adding Resources
================

*************************************************
Adding Resources without a digital Representation
*************************************************

In order to create a resource, the HTTP method ``POST`` has to be used.
The request has to be sent to the Knora server using the ``resources`` path segment.

Unlike in the case of GET requests, the request body consists of JSON describing the resource to be created.
The format of the JSON used to create a resource without a digital representation (e.g. an image file) is described
in the TypeScript interface ``createResourceWithoutRepresentationRequest`` in module ``createResourceFormats``.
It requires the IRI of the resource class the new resource belongs to, a label describing the new resource,
the IRI of the project the new resource belongs to, and the properties to be assigned to the new resource.

Creating resources requires authentication since only known users may add resources.

 



**********************************************
Adding Resources with a digital Representation
**********************************************
