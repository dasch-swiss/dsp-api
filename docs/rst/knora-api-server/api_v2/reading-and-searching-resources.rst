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

.. _reading-and-searching-resources-v2:

Reading and Searching Resources
===============================

.. contents:: :local:

To retrieve an existing resource, the HTTP method ``GET`` has to be used. Reading resources may require authentication, since some resources may have restricted viewing permissions.

***********************************************
Get the Representation of a Resource by its IRI
***********************************************

Get a Full Representation of a Resource by its IRI
--------------------------------------------------

A full representation of resource can be obtained by making a GET request to the API providing its IRI. Because a Knora IRI has the format of a URL, its IRI has to be URL-encoded.

To get the resource with the IRI ``http://data.knora.org/c5058f3a`` (a book from the sample Incunabula project, which is included in the Knora API server's test data), make a HTTP GET request to the ``resources`` route
(path segment ``resources`` in the API call) and append the URL-encoded IRI:

::

    HTTP GET to http://host/v2/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a


If necessary, several resources can be queried at the same time, their IRIs separated by slashes. Please note that the amount of resources that can be queried in one requested is limited. See the settings for ``app/v2`` in ``application.conf``.


More formally, the URL looks like this:

::

    HTTP GET to http://host/v2/resources/resourceIRI(/anotherResourceIri)*


The response to a resource request is a ``ResourcesSequence`` (see :ref:`response-formats-v2`).


Get the preview of a resource by its IRI
----------------------------------------

In some cases, the client may only want to request the preview of a resource, which just provides its ``rdfs:label`` and type.

This works exactly like making a conventional resource request, using the path segment ``resourcespreview``:

::

    HTTP GET to http://host/v2/resourcespreview/resourceIRI(/anotherResourceIri)*


The response to a resource preview request is a ``ResourcesSequence`` (see :ref:`response-formats-v2`).

********************
Search for Resources
********************

Search for a Resource by its ``rdfs:label``
-------------------------------------------

Knora offers the possibility to search for resources by their ``rdfs:label``. The use case for this search is to find a specific resource as you type. E.g., the user wants to get a list of resources whose ``rdfs:label`` contain some search terms separated by a whitespace character:

  - Zeit
  - Zeitg
  - ...
  - Zeitglöcklein d
  - ...
  - Zeitglöcklein des Lebens

With each character added to the last term, the selection gets more specific. The first term should at least contain four characters. To make this kind of "search as you type" possible, a wildcard character is automatically added to the last search term.

::

   HTTP GET to http://host/v2/searchbylabel/searchValue[limitToResourceClass=resourceClassIRI]
   [limitToProject=projectIRI][offset=Integer]


The first parameter must be preceded by a question mark ``?``, any following parameter by an ampersand ``&``.

The default value for the parameter ``offset`` is 0, which returns the first page of search results.
Subsequent pages of results can be fetched by increasing ``offset`` by one. The amount of results per page is defined in ``app/v2`` in ``application.conf``.

The response to a label search request is a ``ResourcesSequence`` (see :ref:`response-formats-v2`).

For performance reasons, standoff markup is not queried for this route.


Full-text Search
----------------

Knora offers a full-text search that searches through all textual representations of values and ``rdfs:label`` of resources.
You can separate search terms by a white space character and they will be combined using the Boolean ``AND`` operator.
Please note that the search terms have to be URL encoded.

::

   HTTP GET to http://host/v2/search/searchValue[limitToResourceClass=resourceClassIRI]
   [limitToProject=projectIRI][offset=Integer]


Please note that the first parameter has to be preceded by a question mark ``?``, any following parameter by an ampersand ``&``.

The default value for the parameter ``offset`` is 0 which returns the first page of search results.
Subsequent pages of results can be fetched by increasing ``offset`` by one. The amount of results per page is defined in ``app/v2`` in ``application.conf``.

The response to a full-text search request is a ``ResourcesSequence`` (see :ref:`response-formats-v2`).


Extended Search
---------------

For more complex queries than a full-text search, Knora offers extended search possibilities, enabling clients to search for resources with arbitrary characteristics, as well as for a graph of resources that are interconnected in some particular way. To do this, the client submits a query in KnarQL (Knora Query Language), which is based on SPARQL (see :ref:`knarql-syntax-v2`). The Knora API server pages the results, filters them to ensure that permissions are respected, and returns them in a Knora API format (currently only JSON-LD).

A KnarQL query can be URL-encoded and sent in a GET request to the extended search route.

::

   HTTP GET to http://host/v2/searchextended/KnarQLQuery

In the future, POST requests will also be supported, to allow longer queries. See :ref:`knarql-syntax-v2` for detailed information about the query syntax and examples.

The response to an extended search request is a ``ResourcesSequence`` (see :ref:`response-formats-v2`).

Count Queries
-------------

For both full full-text and KnarQL searches, a count query can be performed. The answer of a count query is the number of resources (a number) that matched the indicated search criteria without taking into consideration permissions.
This means that the client may not be able to access any of the resources matching the search criteria because of insufficient permissions. Insufficient permissions are intended to prevent a user from accessing a resource or any of its values, or even knowing about its IRI, but not to suppress information about the existence of such a resource.

In order to perform a count query, just append the segment ``count``:

::

   HTTP GET to http://host/v2/searchbylabel/count/searchValue[limitToResourceClass=resourceClassIRI]
   [limitToProject=projectIRI][offset=Integer]

   HTTP GET to http://host/v2/search/count/searchValue[limitToResourceClass=resourceClassIRI]
   [limitToProject=projectIRI][offset=Integer]

   HTTP GET to http://host/v2/searchextended/count/KnarQLQuery


The first parameter has to be preceded by a question mark ``?``, and any following parameter by an ampersand ``&``.

The response to a count query request is a ``ResourcesSequence`` (see :ref:`response-formats-v2`).
