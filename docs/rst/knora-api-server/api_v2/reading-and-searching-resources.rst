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

.. _reading-and-searching-resources-2:

Reading and Searching Resources
===============================

.. contents:: :local:

In order to get an existing resource, the HTTP method ``GET`` has to be used.
The request has to be sent to the Knora server using the ``resources`` path segment (depending on the type of request, this segment has to be exchanged, see below).
Reading resources may require authentication since some resources may have restricted viewing permissions.

***********************************************
Get the Representation of a Resource by its IRI
***********************************************
----------------------------------------------------
Simple Request of a Resource (full Resource Request)
----------------------------------------------------