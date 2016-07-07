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

***********************************************
Get the Representation of a Resource by its IRI
***********************************************
----------------------------------------------------
Simple Request of a Resource (full Resource Request)
----------------------------------------------------

A resource can be obtained by making a GET request to the API providing its IRI. Because a Knora IRI has the format of a URL, its IRI has to be URL encoded.

In order to get the resource with the IRI ``http://data.knora.org/c5058f3a`` (an incunabula book contained in the test data), make a HTTP GET request to the resources route
(path segment ``resources`` in the API call) and append the URL encoded IRI:

::

    curl http://www.knora.org/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a

More formalized, the URL looks like this:

::

    curl http://www.knora.org/v1/resources/resourceIRI


As an answer, the client receives a JSON that represents the requested resource. It has the following members:
 - ``status``: The Knora status code, ``0`` if everything went well
 - ``userdata``: Data about the user that made the request
 - ``resinfo``: Data describing the requested resource and its class
 - ``resdata``: Short information about the resource and its class (including information about the given user's permissions on the resource)
 - ``incoming``: Resources pointing to the requested resource
 - ``props``: Properties of the requested resource.


For a complete and more formalized description of a full resource request, look  at the TypeScript interface ``resourceFullResponse`` in the module ``resourceResponseFormats`` (in ``salsah/src/typescript_interfaces``).
Build the HTML documentation like follows ``make jsonformat`` (see README for further instructions).

Provide Request Parameters
--------------------------

To make a request more specific, the following parameters can be appended to the URL (``http://www.knora.org/resources/resourceIRI?param1=value1&param2=value2``):
 - ``reqtype=info|context|rights``: Specifies the type of request.
       - Setting the parameter's to value ``info`` returns short information about the requested resource (contains only ``resinfo`` and no properties, see TypeScript interface ``resourceInfoResponse`` in module ``resourceResponseFormats``).
       - Settings the parameter's value to ``context`` returns context information (``resource_context``) about the requested resource: Either the dependent parts of a compound resource (e.g. pages of a book) or the parent resource of a dependent resource (e.g. the book a pages belongs to). By default, a context query does not return information about the requested resource itself, but only about its context (see TypeScript interface ``resourceContextResponse`` in module ``resourceResponseFormats``). See below how to get additional information about the resource.
       - The parameter ``rights`` returns only the given user's permissions on the requested resource (see TypeScript interface ``resourceRightsResponse`` in module ``resourceResponseFormats``).
 - ``resinfo=true``: Can be used in combination with ``reqtype=context``: If set, ``resinfo`` is added to the response representing information about
   the requested resource (complementary to its context), see TypeScript interface ``resourceContextResponse`` in module ``resourceResponseFormats``.

-------------------------------------------
Obtain an HTML Representation of a Resource
-------------------------------------------

In order to get an HTML representation of a resource (not a JSON), the path segment ``resources.html`` can be used:

::

    curl http://www.knora.org/v1/resources.html/resourceIRI?reqtype=properties

The request returns the properties of the requested resource as an HTML document.

-----------------------------------------------
Get only the Properties belonging to a Resource
-----------------------------------------------

In order to get only the properties of a resource without any other information, the path segment ``properties`` can be used:

::

    curl http://www.knora.org/v1/properties/resourceIRI

The JSON contains just the member ``properties`` representing the requested resource's properties (see TypeScript interface ``resourcePropertiesResponse`` in module ``resourceResponseFormats``).

**************************************
Get Information about a Resource Class
**************************************

-------------------------------
Get a Resource Class by its IRI
-------------------------------

In order to get information about a resource class, the path segment ``resourcetypes`` can be used. Append the IRI of the resource class to the URL (e.g. ``http://www.knora.org/ontology/incunabula#book``).

::

    curl http://www.knora.org/v1/resourcetypes/resourceClassIRI

In the JSON, the information about the resource class and all the property types that it may have are returned.
Please note that none of these are actual instances of a property, but only only types (see TypeScript interface ``resourceTypeResponse`` in module ``resourceResponseFormats``).

--------------------------------------------------------------
Get all the Property Types of a Resource Class or a Vocabulary
--------------------------------------------------------------

To get a list of all the available property types, the path segment ``propertylists`` can be used. It can be restricted to a certain vocbulary using the parameter ``vocabulary``
or to a certain resource class using the parameter ``restype``.

::

    # returns all the property types for incunabula:page
    curl http://www.knora.org/v1/propertylists?restype=resourceClassIRI

    # returns all the property types for the incunabula vocabulary
    curl http://www.knora.org/v1/propertylists?vocabulary=vocabularyIRI

Both of these queries return a list of property types. The default value for the parameter ``vocabulary`` is ``0``
and means that the resource classes from all the available vocabularies are returned. See TypeScript interface ``propertyTypesInResourceClassResponse`` in module ``resourceResponseFormats``.


----------------------------------------
Get the Resource Classes of a Vocabulary
----------------------------------------

Resource classes and property types are organized in (project specific) name spaces, so called vocabularies.
In order to get all the resource classes defined for a specific vocabulary (e.g. ``incunabula``), the parameter ``vocabulary`` has to be used and assigned the vocabulary's IRI:

::

    curl http://www.knora.org/v1/resourcetypes?vocabulary=vocabularyIRI

This returns all the resource classes defined for the specified vocabulary and their property types. The default value for the parameter ``vocabulary`` is ``0``
and means that the resource classes from all the available vocabularies are returned. See TypeScript interface ``resourceTypesInVocabularyResponse`` in module ``resourceResponseFormats``.

************************
Get all the Vocabularies
************************

To get a list of all available vocabularies, the path segment ``vocabularies`` can be used:

::

    curl http://www.knora.org/v1/vocabularies

The response will list all the available vocabularies. See TypeScript interface ``vocabularyResponse`` in module ``resourceResponseFormats``.

***********************************
Search for Resources by their Label
***********************************

This is a simplified way for searching for resources just by their label. It is a simple string-based method:

::

    curl http://www.knora.org/v1/resources?searchstr=searchValue

Additionally, the following parameters can be appended to the URL (search value is ``Zeitglöcklein``):
 - ``restype_id=resourceClassIRI``: This restricts the search to resources of the specified class. ``-1`` is the default value and means no restriction to a specific class. If a resource class IRI is specified, it has to be URL encoded (e.g. ``http://www.knora.org/v1/resources?searchstr=Zeitgl%C3%B6cklein&restype_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book``).
 - ``numprops=Integer``: Specifies the number of properties returned for each resource that was found (sorted by GUI order), e.g. ``http://www.knora.org/v1/resources?searchstr=Zeitgl%C3%B6cklein&numprops=4``.
 - ``limit=Integer``: Lmits the amount of results returned (e.g. ``http://www.knora.org/v1/resources?searchstr=Zeitgl%C3%B6cklein&limit=1``).


The response lists the resources that matched the search criteria (see TypeScript interface ``resourceLabelSearchResponse`` in module ``resourceResponseFormats``).

*****************************
Fulltext Search for Resources
*****************************

*****************************
Extended Search for Resources
*****************************
