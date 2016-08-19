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

.. _delete-resources-and-values:

Deleting Resources and Values
=============================

In order to delete a resource or a value, the HTTP method ``DELETE`` has to be used.
Deleting resources or values requires authentication.

*****************
Delete a Resource
*****************

The delete request has to be sent to the Knora server using the ``resources`` path segment.

::

    HTTP DELETE to http://host/resources/resourceIRI



Please note that the resource IRI has to be URL encoded.

**************
Delete a Value
**************

The delete request has to be sent to the Knora server using the ``values`` path segment, providing the valueIRI:

::

    HTTP DELETE to http://host/values/valueIRI


Please note that the value IRI has to be URL encoded.