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

.. _delete-resources-and-values:

Deleting Resources and Values
=============================

Knora does not actually delete resources or values; it just marks them as deleted. To mark a resource or value
as deleted, you must use the HTTP method ``DELETE`` has to be used. This requires authentication.

**************************
Mark a Resource as Deleted
**************************

The delete request has to be sent to the Knora server using the ``resources`` path segment.

::

    HTTP DELETE to http://host/resources/resourceIRI?deleteComment=String


The resource IRI must be URL-encoded. The ``deleteComment`` is an optional comment explaining why the resource
is being marked as deleted.


***********************
Mark a Value as Deleted
***********************

The delete request has to be sent to the Knora server using the ``values`` path segment, providing the valueIRI:

::

    HTTP DELETE to http://host/values/valueIRI?deleteComment=String


The value IRI must be URL-encoded. The ``deleteComment`` is an optional comment explaining why the value is
being marked as deleted.

Once a value has been marked as deleted, no new versions of it can be made.
