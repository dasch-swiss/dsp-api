<!---
Copyright © 2015-2019 the contributors (see Contributors.md).

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
-->

# Reading and Searching Resources

@@toc

To retrieve an existing resource, the HTTP method `GET` has to be used.
Reading resources may require authentication, since some resources may
have restricted viewing permissions.

## Responses Describing Resources

Resources can be returned in
[JSON-LD](https://json-ld.org/spec/latest/json-ld/),
[Turtle](https://www.w3.org/TR/turtle/),
or [RDF/XML](https://www.w3.org/TR/rdf-syntax-grammar/), using
@extref[HTTP content negotiation](rfc:7231#section-5.3.2) (see
@ref:[Response Formats](introduction.md#response-formats)).

Operations for reading and searching resources can return responses in either the
simple or the complex ontology schema. The complex schema is used by default.
To receive a response in the simple schema, use the HTTP request header or URL
parameter described in @ref:[API Schema](introduction.md#api-schema).

Each Knora API v2 response describing one or more resources returns a
single RDF graph. For example, a request for a single resource returns that
resource and all its values. In a full-text search, the resource is returned with the
values that matched the search criteria. A response to an extended search
may represent a whole graph of interconnected resources.

In JSON-LD, if only one resource is returned, it is the top-level object;
if more than one resource is returned, they are represented as an array
of objects of the `@graph` member of the top-level object (see
[Named Graphs](https://json-ld.org/spec/latest/json-ld/#named-graphs) in the
JSON-LD specification).

In the complex schema, dependent resources, i.e. resources that are referred
to by other resources on the top level, are nested in link value objects.
If resources on the top level are referred to by other resources and 
these links are part of the response, virtual incoming links are generated;
see @ref:[Gravsearch: Virtual Graph Search](query-language.md)).

See the interfaces `Resource` and `ResourcesSequence` in module
`ResourcesResponse` (exists for both API schemas: `ApiV2Simple` and
`ApiV2WithValueObjects`).

### Text Markup Options

Text markup can be returned in one of two ways:

- As XML embedded in the response, using an @ref:[XML to Standoff Mapping](xml-to-standoff-mapping.md).

- As @ref:[standoff/RDF](../../02-knora-ontologies/knora-base.md#text-with-standoff-markup),
  which is Knora's internal markup representation.

Embedded XML is the default.

Implementation of support for standoff/RDF in API v2 is in its early stages. The basic
procedure works like this:

First, request a resource in the @ref:[complex schema](introduction.md#api-schema), using any relevant
API v2 route, submitting the string `standoff` as the value of either:

  - the HTTP header `X-Knora-Accept-Markup`

  - the URL parameter `markup`

If a text value in the resource contains markup, the text value will look something like this:

```jsonld
{
  "@id" : "http://rdfh.ch/0001/LK-wKXDNQJaRHOf0F0aJ2g/values/1Er1OpVwQR2u6peTwyNpJw",
  "@type" : "knora-api:TextValue",
  "knora-api:attachedToUser" : {
    "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
  },
  "knora-api:hasPermissions" : "CR knora-admin:Creator|V knora-admin:UnknownUser",
  "knora-api:textValueHasMarkup" : true,
  "knora-api:textValueHasMaxStandoffStartIndex" : 6737,
  "knora-api:userHasPermission" : "CR",
  "knora-api:valueAsString" : "\nHamlet\nACT I\nSCENE I. Elsinore. A platform before the castle...",
  "knora-api:valueCreationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "2019-05-08T17:08:32.158401Z"
  }
}
```

The object `knora-api:valueAsString` contains the text without markup. The predicate
`knora-api:textValueHasMarkup` indicates that the text value has markup,
and the value of the predicate `knora-api:textValueHasMaxStandoffStartIndex` gives the start
index of the last standoff tag; this gives the client some idea of how much markup there is.

You can then request the text value's standoff/RDF, which is returned in pages of a limited
size. To get each page:

```
HTTP GET to http://host/v2/standoff/RESOURCE_IRI/TEXT_VALUE_IRI/OFFSET
```

Both `RESOURCE_IRI` and `TEXT_VALUE_IRI` must be URL-encoded. The offset is an integer whose
initial value is 0. The response will look like this:

```jsonld
{
  "@graph" : [ {
    "@type" : "http://api.knora.org/ontology/standoff/v2#StandoffRootTag",
    "knora-api:standoffTagHasEnd" : 184716,
    "knora-api:standoffTagHasStart" : 0,
    "knora-api:standoffTagHasStartIndex" : 0,
    "knora-api:standoffTagHasUUID" : "sbBzeAaNTzaUXl90UtlYzw"
  }, {
    "@type" : "http://api.knora.org/ontology/standoff/v2#StandoffHeader1Tag",
    "knora-api:standoffTagHasEnd" : 7,
    "knora-api:standoffTagHasStart" : 1,
    "knora-api:standoffTagHasStartIndex" : 1,
    "knora-api:standoffTagHasStartParentIndex" : 0,
    "knora-api:standoffTagHasUUID" : "HhXjcdSTS_G6eSQ0apdjUw"
  }, {
    "@type" : "http://api.knora.org/ontology/standoff/v2#StandoffHeader3Tag",
    "knora-api:standoffTagHasEnd" : 14,
    "knora-api:standoffTagHasStart" : 9,
    "knora-api:standoffTagHasStartIndex" : 2,
    "knora-api:standoffTagHasStartParentIndex" : 0,
    "knora-api:standoffTagHasUUID" : "Ymr2aDUqTx6nMwGZGiqduA"
  }, {
    "@type" : "http://api.knora.org/ontology/standoff/v2#StandoffHeader3Tag",
    "knora-api:standoffTagHasEnd" : 64,
    "knora-api:standoffTagHasStart" : 16,
    "knora-api:standoffTagHasStartIndex" : 3,
    "knora-api:standoffTagHasStartParentIndex" : 0,
    "knora-api:standoffTagHasUUID" : "_Zk0B1edRK6mgdtokmosXg"
  }, {
    "@type" : "http://api.knora.org/ontology/standoff/v2#StandoffBlockquoteTag",
    "knora-api:standoffTagHasEnd" : 112,
    "knora-api:standoffTagHasStart" : 66,
    "knora-api:standoffTagHasStartIndex" : 4,
    "knora-api:standoffTagHasStartParentIndex" : 0,
    "knora-api:standoffTagHasUUID" : "1DLdI0LJTCy07w6ZsOM_Sg"
  }, {
    "@type" : "http://api.knora.org/ontology/standoff/v2#StandoffItalicTag",
    "knora-api:standoffTagHasEnd" : 111,
    "knora-api:standoffTagHasStart" : 67,
    "knora-api:standoffTagHasStartIndex" : 5,
    "knora-api:standoffTagHasStartParentIndex" : 4,
    "knora-api:standoffTagHasUUID" : "XJ6GVO1VQSqrTyLHGnHqcA"
  } ],
  "knora-api:nextStandoffStartIndex" : 100,
  "@context" : {
    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
  }
}
```

See @ref:[Text with Standoff Markup](../../02-knora-ontologies/knora-base.md#text-with-standoff-markup)
for details of the predicates in each standoff tag.

If there are more pages of standoff to be requested, the response will contain `knora-api:nextStandoffStartIndex`,
whose object should be submitted as the next `OFFSET` to the same route. This continues until
you receive a response without `knora-api:nextStandoffStartIndex`.

## Get the Representation of a Resource by IRI

### Get a Full Representation of a Resource by IRI

A full representation of resource can be obtained by making a GET
request to the API providing its IRI. Because a Knora IRI has the format
of a URL, its IRI has to be URL-encoded.

To get the resource with the IRI `http://rdfh.ch/c5058f3a` (a
book from the sample Incunabula project, which is included in the Knora
API server's test data), make a HTTP GET request to the `resources`
route (path segment `resources` in the API call) and append the
URL-encoded IRI:

```
HTTP GET to http://host/v2/resources/http%3A%2F%2Frdfh.ch%2Fc5058f3a
```

If necessary, several resources can be queried at the same time, their
IRIs separated by slashes. Please note that the amount of resources that
can be queried in one requested is limited. See the settings for
`app/v2` in `application.conf`.

More formally, the URL looks like this:

```
HTTP GET to http://host/v2/resources/resourceIRI(/anotherResourceIri)*
```

### Get a Full Representation of a Version of a Resource by IRI

To get a specific past version of a resource, use the route described in
@ref:[Get a Full Representation of a Resource by IRI](#get-a-full-representation-of-a-resource-by-iri),
and add the URL parameter `?version=TIMESTAMP`, where `TIMESTAMP` is an
[xsd:dateTimeStamp](https://www.w3.org/TR/xmlschema11-2/#dateTimeStamp) in the
UTC timezone. The timestamp can either be URL-encoded, or submitted with all
punctuation (`-`, `:`, and `.`) removed (this is to accept timestamps
from Knora's @ref:[ARK URLs](permalinks.md)).

The resource will be returned with the values that it had at the specified
time. Since Knora only versions values, not resource metadata (e.g.
`rdfs:label`), the current metadata will be returned.

The returned resource will include the predicate `knora-api:versionDate`,
containing the timestamp that was submitted, and its `knora-api:versionArkUrl`
(see @ref:[Resource Permalinks](permalinks.md)) will contain the
same timestamp.

### Get a Value in a Resource

To get a specific value of a resource, use this route:

```
HTTP GET to http://host//v2/values/resourceIRI/valueUUID
```

The resource IRI must be URL-encoded. The path element `valueUUID` is the
string object of the value's `knora-api:valueHasUUID`.

The value will be returned within its containing resource, in the same format
as for @ref:[Responses Describing Resources](#responses-describing-resources),
but without any of the resource's other values.

### Get a Version of a Value in a Resource

To get a particular version of a specific value of a resource, use the route
described in @ref:[Get a Value in a Resource](#get-a-value-in-a-resource),
and add the URL parameter `?version=TIMESTAMP`, where `TIMESTAMP` is an
[xsd:dateTimeStamp](https://www.w3.org/TR/xmlschema11-2/#dateTimeStamp) in the
UTC timezone. The timestamp can either be URL-encoded, or submitted with all
punctuation (`-`, `:`, and `.`) removed (this is to accept timestamps
from Knora's @ref:[ARK URLs](permalinks.md)).

The value will be returned within its containing resource, in the same format
as for @ref:[Responses Describing Resources](#responses-describing-resources),
but without any of the resource's other values.

Since Knora only versions values, not resource metadata (e.g.
`rdfs:label`), the current resource metadata will be returned.

### Get the Version History of a Resource

To get a list of the changes that have been made to a resource since its creation,
use this route:

```
HTTP GET to http://host/v2/resources/history/resourceIRI[?startDate=START_DATE&endDate=END_DATE]
```

The resource IRI must be URL-encoded. The start and end dates are optional, and
are URL-encoded timestamps in
[xsd:dateTimeStamp](https://www.w3.org/TR/xmlschema11-2/#dateTimeStamp) format.
The start date is inclusive, and the end date is exclusive.
If the start date is not provided, the resource's history since its creation is returned.
If the end date is not provided, the resource's history up to the present is returned.

The response is a list of changes made to the resource, in reverse chronological order.
Each entry has the properties `knora-api:author` (the IRI of the user who made the change) and
`knora-api:versionDate` (the date when the change was made). For example:

```jsonld
{
  "@graph" : [ {
    "knora-api:author" : {
      "@id" : "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "knora-api:versionDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2019-02-11T09:05:10Z"
    }
  }, {
    "knora-api:author" : {
      "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
    },
    "knora-api:versionDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2019-02-10T10:30:10Z"
    }
  }, {
    "knora-api:author" : {
      "@id" : "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
    },
    "knora-api:versionDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2019-02-10T10:05:10Z"
    }
  } ],
  "@context" : {
    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
  }
}
```

The entries include all the dates when the resource's values were created or modified (within
the requested date range), as well as the date when the resource was created (if the requested
date range allows it). Each date is included only once. Since Knora only versions values, not
resource metadata (e.g. `rdfs:label`), changes to a resource's metadata are not included in its
version history.

To request the resource as it was at each of these dates, see
@ref:[Get a Full Representation of a Version of a Resource by IRI](#get-a-full-representation-of-a-version-of-a-resource-by-iri). For consistency in citation, we recommend using these dates when
requesting resource versions.

### Get the preview of a resource by IRI

In some cases, the client may only want to request the preview of a
resource, which just provides its metadata (e.g. its IRI, `rdfs:label`,
and type), without its values.

This works exactly like making a conventional resource request, using
the path segment `resourcespreview`:

```
HTTP GET to http://host/v2/resourcespreview/resourceIRI(/anotherResourceIri)*
```

## Get a Graph of Resources

Knora can return a graph of connections between resources, e.g. for generating
a network diagram.

```
HTTP GET to http://host/v2/graph/resourceIRI[depth=Integer]
[direction=outbound|inbound|both][excludeProperty=propertyIri]
```

The first parameter must be preceded by a question mark `?`, any
following parameter by an ampersand `&`.

- `depth` must be at least 1. The maximum depth is an Knora configuration setting.
  The default is 4.
- `direction` specifies the direction of the links to be queried, i.e. links to
  and/or from the given resource. The default is `outbound`.
- `excludeProperty` is an optional link property to be excluded from the
  results.

To accommodate large graphs, the graph response format is very concise, and is therefore
simpler than the usual resources response format. Each resource represented only by its IRI,
class, and label. Direct links are shown instead of link values. For example:

```jsonld
{
  "@graph" : [ {
    "@id" : "http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ",
    "@type" : "anything:Thing",
    "rdfs:label" : "Sierra"
  }, {
    "@id" : "http://rdfh.ch/0001/A67ka6UQRHWf313tbhQBjw",
    "@type" : "anything:Thing",
    "rdfs:label" : "Victor"
  }, {
    "@id" : "http://rdfh.ch/0001/Lz7WEqJETJqqsUZQYexBQg",
    "@type" : "anything:Thing",
    "rdfs:label" : "Foxtrot"
  }, {
    "@id" : "http://rdfh.ch/0001/WLSHxQUgTOmG1T0lBU2r5w",
    "@type" : "anything:Thing",
    "anything:hasOtherThing" : {
      "@id" : "http://rdfh.ch/0001/A67ka6UQRHWf313tbhQBjw"
    },
    "rdfs:label" : "Tango"
  }, {
    "@id" : "http://rdfh.ch/0001/start",
    "@type" : "anything:Thing",
    "anything:hasOtherThing" : [ {
      "@id" : "http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ"
    }, {
      "@id" : "http://rdfh.ch/0001/WLSHxQUgTOmG1T0lBU2r5w"
    }, {
      "@id" : "http://rdfh.ch/0001/tPfZeNMvRVujCQqbIbvO0A"
    } ],
    "rdfs:label" : "Romeo"
  }, {
    "@id" : "http://rdfh.ch/0001/tPfZeNMvRVujCQqbIbvO0A",
    "@type" : "anything:Thing",
    "anything:hasOtherThing" : {
      "@id" : "http://rdfh.ch/0001/Lz7WEqJETJqqsUZQYexBQg"
    },
    "rdfs:label" : "Echo"
  } ],
  "@context" : {
    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#",
    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

## Search for Resources

### Search for a Resource by its `rdfs:label`

Knora offers the possibility to search for resources by their
`rdfs:label`. The use case for this search is to find a specific
resource as you type. E.g., the user wants to get a list of resources
whose `rdfs:label` contain some search terms separated by a whitespace
character:

   - Zeit
   - Zeitg
   - ...
   - Zeitglöcklein d
   - ...
   - Zeitglöcklein des Lebens

With each character added to the last term, the selection gets more
specific. The first term should at least contain four characters. To
make this kind of "search as you type" possible, a wildcard character is
automatically added to the last search term.

```
HTTP GET to http://host/v2/searchbylabel/searchValue[limitToResourceClass=resourceClassIRI]
[limitToProject=projectIRI][offset=Integer]
```

The first parameter must be preceded by a question mark `?`, any
following parameter by an ampersand `&`.

The default value for the parameter `offset` is 0, which returns the
first page of search results. Subsequent pages of results can be fetched
by increasing `offset` by one. The amount of results per page is defined
in `app/v2` in `application.conf`.

For performance reasons, standoff markup is not queried for this route.

To request the number of results rather than the results themselves, you can
do a count query:

```
HTTP GET to http://host/v2/searchbylabel/count/searchValue[limitToResourceClass=resourceClassIRI][limitToProject=projectIRI][offset=Integer]
```

The response to a count query request is an object with one predicate,
`http://schema.org/numberOfItems`, with an integer value.

### Full-text Search

Knora offers a full-text search that searches through all textual
representations of values and `rdfs:label` of resources. You can
separate search terms by a white space character and they will be
combined using the Boolean `AND` operator. Please note that the search
terms have to be URL-encoded.

```
HTTP GET to http://host/v2/search/searchValue[limitToResourceClass=resourceClassIRI]
[limitToStandoffClass=standoffClassIri][limitToProject=projectIRI][offset=Integer]
```

The first parameter has to be preceded by a question
mark `?`, any following parameter by an ampersand `&`.

A search value must have a minimal length of three characters (default value) as defined in `app/v2` in `application.conf`.

A search term may contain wildcards. A `?` represents a single character. It has to be URL-encoded as `%3F` since it has a special meaning in the URL syntax. For example, the term `Uniform` can be search for like this:

```
HTTP GET to http://host/v2/search/Unif%3Frm
```

A `*` represents zero, one or multiple characters. For example, the term `Uniform` can be searched for like this:

```
HTTP GET to http://host/v2/search/Uni*m
```

The default value for the parameter `offset` is 0 which returns the
first page of search results. Subsequent pages of results can be fetched
by increasing `offset` by one. The amount of results per page is defined
in `app/v2` in `application.conf`.

If the parameter `limitToStandoffClass` is provided, Knora will look for search terms
that are marked up with the indicated standoff class.

To request the number of results rather than the results themselves, you can
do a count query:

```
HTTP GET to http://host/v2/search/count/searchValue[limitToResourceClass=resourceClassIRI][limitToStandoffClass=standoffClassIri][limitToProject=projectIRI][offset=Integer]
```

The first parameter has to be preceded by a question
mark `?`, any following parameter by an ampersand `&`.

The response to a count query request is an object with one predicate,
`http://schema.org/numberOfItems`, with an integer value.

### Gravsearch

For more complex queries than a full-text search, Knora offers a query language
called @ref:[Gravsearch: Virtual Graph Search](query-language.md)).

### Support of TEI/XML

To convert standoff markup to TEI/XML, see @ref:[TEI/XML](tei-xml.md).

### Reading Resources by Class from a Project

To facilitate the development of tabular user interfaces for data entry, it is
possible to get a paged list of all the resources belonging to a particular
class in a given project, sorted by the value of a property:

```
HTTP GET to http://host/v2/resources?resourceClass=RESOURCE_CLASS_IRI&page=PAGE[&orderByProperty=PROPERTY_IRI]
```

This is useful only if the project does not contain a large amount of data;
otherwise, you should use @ref:[Gravsearch](query-language.md) to search
using more specific criteria.

The HTTP header `X-Knora-Accept-Project` must be submitted; its value is
a Knora project IRI. In the request URL, the values of `resourceClass` and `orderByProperty`
are URL-encoded IRIs in the @ref:[complex schema](introduction.md#api-schema).
The `orderByProperty` parameter is optional; if it is not supplied, resources will
be sorted alphabetically by resource IRI (an arbitrary but consistent order).
The value of `page` is a 0-based integer page number. Paging works as it does
in @ref:[Gravsearch](query-language.md)).
