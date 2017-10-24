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

.. _reading-and-searching-resources:

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

A resource can be obtained by making a GET request to the API providing its IRI. Because a Knora IRI has the format of a URL, its IRI has to be URL encoded.

In order to get the resource with the IRI ``http://data.knora.org/c5058f3a`` (an incunabula book contained in the test data), make a HTTP GET request to the resources route
(path segment ``resources`` in the API call) and append the URL encoded IRI:

::

    HTTP GET to http://host/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a

More formalized, the URL looks like this:

::

    HTTP GET to http://host/v1/resources/resourceIRI


As an answer, the client receives a JSON that represents the requested resource. It has the following members:
 - ``status``: The Knora status code, ``0`` if everything went well
 - ``userdata``: Data about the user that made the request
 - ``resinfo``: Data describing the requested resource and its class
 - ``resdata``: Short information about the resource and its class (including information about the given user's permissions on the resource)
 - ``incoming``: Resources pointing to the requested resource
 - ``props``: Properties of the requested resource.


For a complete and more formalized description of a full resource request, look  at the TypeScript interface ``resourceFullResponse`` in the module ``resourceResponseFormats``.

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

    HTTP GET to http://host/v1/resources.html/resourceIRI?reqtype=properties

The request returns the properties of the requested resource as an HTML document.

-----------------------------------------------
Get only the Properties belonging to a Resource
-----------------------------------------------

In order to get only the properties of a resource without any other information, the path segment ``properties`` can be used:

::

    HTTP GET to http://host/v1/properties/resourceIRI

The JSON contains just the member ``properties`` representing the requested resource's properties (see TypeScript interface ``resourcePropertiesResponse`` in module ``resourceResponseFormats``).

**************************************
Get Information about a Resource Class
**************************************

-------------------------------
Get a Resource Class by its IRI
-------------------------------

In order to get information about a resource class, the path segment ``resourcetypes`` can be used. Append the IRI of the resource class to the URL (e.g. ``http://www.knora.org/ontology/incunabula#book``).

::

    HTTP GET to http://host/v1/resourcetypes/resourceClassIRI

In the JSON, the information about the resource class and all the property types that it may have are returned.
Please note that none of these are actual instances of a property, but only types (see TypeScript interface ``resourceTypeResponse`` in module ``resourceResponseFormats``).

--------------------------------------------------------------
Get all the Property Types of a Resource Class or a Vocabulary
--------------------------------------------------------------

To get a list of all the available property types, the path segment ``propertylists`` can be used. It can be restricted to a certain vocbulary using the parameter ``vocabulary``
or to a certain resource class using the parameter ``restype``.

::

    # returns all the property types for incunabula:page
    HTTP GET to http://host/v1/propertylists?restype=resourceClassIRI

    # returns all the property types for the incunabula vocabulary
    HTTP GET to http://host/v1/propertylists?vocabulary=vocabularyIRI

Both of these queries return a list of property types. The default value for the parameter ``vocabulary`` is ``0``
and means that the resource classes from all the available vocabularies are returned. See TypeScript interface ``propertyTypesInResourceClassResponse`` in module ``resourceResponseFormats``.


----------------------------------------
Get the Resource Classes of a Vocabulary
----------------------------------------

Resource classes and property types are organized in (project specific) name spaces, so called vocabularies.
In order to get all the resource classes defined for a specific vocabulary (e.g. ``incunabula``), the parameter ``vocabulary`` has to be used and assigned the vocabulary's IRI:

::

    HTTP GET to http://host/v1/resourcetypes?vocabulary=vocabularyIRI

This returns all the resource classes defined for the specified vocabulary and their property types. The default value for the parameter ``vocabulary`` is ``0``
and means that the resource classes from all the available vocabularies are returned. See TypeScript interface ``resourceTypesInVocabularyResponse`` in module ``resourceResponseFormats``.

************************
Get all the Vocabularies
************************

To get a list of all available vocabularies, the path segment ``vocabularies`` can be used:

::

    HTTP GET to http://host/v1/vocabularies

The response will list all the available vocabularies. See TypeScript interface ``vocabularyResponse`` in module ``resourceResponseFormats``.

********************
Search for Resources
********************

-----------------------------------
Search for Resources by their Label
-----------------------------------

This is a simplified way for searching for resources just by their label. It is a simple string-based method:

::

    HTTP GET to http://host/v1/resources?searchstr=searchValue

Additionally, the following parameters can be appended to the URL (search value is ``Zeitglöcklein``):
 - ``restype_id=resourceClassIRI``: This restricts the search to resources of the specified class (subclasses of that class will also match). ``-1`` is the default value and means no restriction to a specific class. If a resource class IRI is specified, it has to be URL encoded (e.g. ``http://www.knora.org/v1/resources?searchstr=Zeitgl%C3%B6cklein&restype_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book``).
 - ``numprops=Integer``: Specifies the number of properties returned for each resource that was found (sorted by GUI order), e.g. ``http://www.knora.org/v1/resources?searchstr=Zeitgl%C3%B6cklein&numprops=4``.
 - ``limit=Integer``: Limits the amount of results returned (e.g. ``http://www.knora.org/v1/resources?searchstr=Zeitgl%C3%B6cklein&limit=1``).


The response lists the resources that matched the search criteria (see TypeScript interface ``resourceLabelSearchResponse`` in module ``resourceResponseFormats``).

---------------
Fulltext Search
---------------

Knora offers a fulltext search that searches through all textual representations of values. You can separate search terms by a white space and they will be combined using the Boolean ``AND`` operator.
Please note that the search terms have to be URL encoded.

::

    HTTP GET to http://host/v1/search/searchValue?searchtype=fulltext[&filter_by_restype=resourceClassIRI]
    [&filter_by_project=projectIRI][&show_nrows=Integer]{[&start_at=Integer]

The parameter ``searchtype`` is required and has to be set to ``fulltext``. Additionally, these parameters can be set:
  - ``filter_by_restype=resourceClassIRI``: restricts the search to resources of the specified resource class (subclasses of that class will also match).
  - ``filter_by_project=projectIRI``: restricts the search to resources of the specified project.
  - ``show_nrows=Integer``: Indicates how many reults should be presented on one page. If omitted, the default value ``25`` is used.
  - ``start_at=Integer``: Used to enable paging and go through all the results request by request.

The response presents the retrieved resources (according to ``show_nrows`` and ``start_at``) and information about paging.
If not all resources could be presented on one page (``nhits`` is greater than ``shown_nrows``), the next page can be requested (by increasing ``start_at`` by the number of ``show_nrows``).
You can simply go through the elements of ``paging`` to request the single pages one by one.
See TypeScript interface ``searchResponse`` in module ``searchResponseFormats``.

-----------------------------
Extended Search for Resources
-----------------------------

::

    HTTP GET to http://host/v1/search/?searchtype=extended
    [&filter_by_restype=resourceClassIRI][&filter_by_project=projectIRI][&filter_by_owner=userIRI]
    (&property_id=propertyTypeIRI&compop=comparisonOperator&searchval=searchValue)+
    [&show_nrows=Integer][&start_at=Integer]

The parameter ``searchtype`` is required and has to be set to ``extended``. An extended search requires at least one set of parameters consisting of:
  - ``property_id=propertyTypeIRI``: the property the resource has to have (subproperties of that property will also match).
  - ``compop=comparisonOperator``: the comparison operator to be used to match between the resource's property value and the search term.
  - ``searchval=searchTerm``: the search value to look for.

You can also provide several of these sets to make your query more specific.

The following table indicates the possible combinations of value types and comparison operators:

+------------------+-----------------------------------------------------+
| Value Type       | Comparison Operator                                 |
+==================+=====================================================+
| Date Value       | EQ, !EQ, GT, GT_EQ, LT, LT_EQ, EXISTS               |
+------------------+-----------------------------------------------------+
| Integer Value    | EQ, !EQ, GT, GT_EQ, LT, LT_EQ, EXISTS               |
+------------------+-----------------------------------------------------+
| Float Value      | EQ, !EQ, GT, GT_EQ, LT, LT_EQ, EXISTS               |
+------------------+-----------------------------------------------------+
| Text Value       | MATCH_BOOLEAN, MATCH, EQ, !EQ, LIKE, !LIKE, EXISTS  |
+------------------+-----------------------------------------------------+
| Geometry Value   | EXISTS                                              |
+------------------+-----------------------------------------------------+
| Resource Pointer | EQ, EXISTS                                          |
+------------------+-----------------------------------------------------+
| Color Value      | EQ, EXISTS                                          |
+------------------+-----------------------------------------------------+
| List Value       | EQ, EXISTS                                          |
+------------------+-----------------------------------------------------+
| Boolean Value    | EQ, !EQ, EXISTS                                     |
+------------------+-----------------------------------------------------+


Explanation of the comparison operators:
  - ``EQ``: checks if a resource's value *equals* the search value. In case of a text value type, it checks for identity of the strings compared.
    In case of a date value type, equality is given if the dates overlap in any way. Since dates are internally always treated as periods,
    equality is given if a date value's period ends after or equals the start of the defined period and
    a date value's period starts before or equals the end of the defined period.
  - ``!EQ``: checks if a resource's value *does not equal* the search value. In case of a text value type, it checks if the compared strings are different.
    In case of a date value type, inequality is given if the dates do not overlap in any way, meaning that a date starts after the end of the defined period or ends before the beginning of the defined period
    (dates are internally always treated as periods, see above).
  - ``GT``: checks if a resource's value is *greater than* the search value. In case of a date value type, it assures that a period begins after the indicated period's end.
  - ``GT_EQ``: checks if a resource's value *equals or is greater than* the search value. In case of a date value type, it assures that the periods overlap in any way (see ``EQ``) **or** that the period starts after the indicated period's end (see ``GT``).
  - ``LT``: checks if a resource's value is *lower than* the search value. In case of a date value type, it assures that a period ends before the indicated period's start.
  - ``LT_EQ``: checks if a resource's value *equals or is lower than* the search value. In case of a date value type, it assures that the periods overlap in any way (see ``EQ``) **or** that the period ends before the indicated period's start (see ``LT``).
  - ``EXISTS``: checks if an instance of the indicated property type *exists* for a resource. **Please always provide an empty search value when using EXISTS: "searchval="**. Otherwise, the query syntax rules would be violated.
  - ``MATCH``: checks if a resource's text value *matches* the search value. The behaviour depends on the used triplestore's full text index.
  - ``LIKE``: checks if the search value is contained in a resource's text value.
  - ``!LIKE``: checks if the search value is not contained in a resource's text value.
  - ``MATCH_BOOLEAN``: checks if a resource's text value *matches* the provided list of positive (exist) and negative (do not exist) terms. The list takes this form: ``([+-]term\s)+``.

Additionally, these parameters can be set:
  - ``filter_by_restype=resourceClassIRI``: restricts the search to resources of the specified resource class (subclasses of that class will also match).
  - ``filter_by_project=projectIRI``: restricts the search to resources of the specified project.
  - ``filter_by_owner``: restricts the search to resources owned by the specified user.
  - ``show_nrows=Integer``: Indicates how many reults should be presented on one page. If omitted, the default value ``25`` is used.
  - ``start_at=Integer``: Used to enable paging and go through all the results request by request.

Some sample searches:
  - ``http://localhost:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=!EQ&searchval=Zeitgl%C3%B6cklein%20des%20Lebens%20und%20Leidens%20Christi``: searches for books that have a title that does not equal "Zeitglöcklein des Lebens und Leidens Christi".
  - ``http://www.knora.org/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=MATCH&searchval=Zeitgl%C3%B6cklein&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23pubdate&compop=EQ&searchval=JULIAN:1490``: searches for resources of type ``incunabula:book`` whose titles match "Zeitglöcklein" and were published in the year 1490 (according to the Julian calendar).


The response presents the retrieved resources (according to ``show_nrows`` and ``start_at``) and information about paging.
If not all resources could be presented on one page (``nhits`` is greater than ``shown_nrows``), the next page can be requested (by increasing ``start_at`` by the number of ``show_nrows``).
You can simply go through the elements of ``paging`` to request the single pages one by one.
See the TypeScript interface ``searchResponse`` in module ``searchResponseFormats``.

************************
Get a Graph of Resources
************************

The path segment ``graphdata`` returns a graph of resources that are reachable via links to or from an initial resource.

::

    HTTP GET to http://host/v1/graphdata/resourceIRI?depth=Integer

The parameter ``depth`` specifies the maximum depth of the graph, and defaults to 4. If ``depth`` is 1, the operation will return only the initial resource and any resources that are directly linked to or from it.

The graph includes any link that is a subproperty of ``knora-base:hasLinkTo``, except for links that are subproperties of ``knora-base:isPartOf``. Specifically, if resource ``R1`` has a link that is a subproperty of ``knora-base:isPartOf`` pointing to resource ``R2``, no link from ``R1`` to ``R2`` is included in the graph.

The response represents the graph as a list of nodes (resources) and a list of edges (links). For details, see the TypeScript interface ``graphDataResponse`` in module ``graphDataResponseFormats``.

**********************
Get Hierarchical Lists
**********************

The knora-base ontology allows for the definition of hierarchical lists. These can be queried by providing the IRI of the root node.
Selections are hierarchical list that are just one level deep. Internally, they are represented as hierarchical lists.

You can get a hierarchical by using the path segment ``hlists`` and appending the hierarchical list's IRI (URL encoded):

::

    HTTP GET to http://host/v1/hlists/rootNodeIRI

The response shows all of the list nodes that are element of the requested hierarchical list as a tree structure. See TypeScript interface ``hierarchicalListResponse`` in module ``hierarchicalListResponseFormats``.

For each node, the full path leading to it from the top level can be requested by making a query providing the node's IRI and setting the param ``reqtype=node``:

::

    HTTP GET to http://host/v1/hlists/nodeIri?reqtype=node


The response presents the full path to the current node. See TypeScript interface ``nodePathResponse`` in module ``hierarchicalListResponseFormats``.
