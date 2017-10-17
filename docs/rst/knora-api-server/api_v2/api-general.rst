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
Since the develeopment of this prototype has a long history and the specifiation of API V1 was an evolving process, V1 manifests several inconsistencies and patricularities.
With V2, we would like to offer a format that is consistent and hence easier to use for a client.

V2 Path Segment
---------------

Every request to API V1 includes ``v2`` as a path segment, e.g. ``http://host/v2/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a``.
Accordingly, requests to another version of the API will require another path segment.