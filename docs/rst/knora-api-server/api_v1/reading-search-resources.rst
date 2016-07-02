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

.. _reading-and-searching-resources:

Reading and Searching Resources
===============================

Get a Resource by its IRI
-------------------------

A resource can be obtained by making a GET request to the API providing its IRI. Because a Knora IRI has the format of a URL, its IRI has to be URL encoded.

Get the resource with the IRI ``http://data.knora.org/c5058f3a`` (an incunabula book):

::

    curl http://www.knora.org/http%3A%2F%2Fdata.knora.org%2Fc5058f3a

In case the request could be successfully handled, Knora responds with a 200 HTTP status code. The actual answer from Knora (the representation of the requested resource) is sent in the HTTP body,
encoded as JSON (HTTP header: ``Content-Type: application/json; charset=UTF-8``).

