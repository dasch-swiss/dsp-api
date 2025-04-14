# Reading and Searching Resources

To retrieve an existing resource, the HTTP method `GET` has to be used.
Reading resources may require authentication, since some resources may
have restricted viewing permissions.

## Responses Describing Resources

Resources can be returned in
[JSON-LD](https://json-ld.org/spec/latest/json-ld/),
[Turtle](https://www.w3.org/TR/turtle/),
or [RDF/XML](https://www.w3.org/TR/rdf-syntax-grammar/), using
[HTTP content negotiation](https://tools.ietf.org/html/rfc7231#section-5.3.2) (see
[Response Formats](introduction.md#response-formats)).

Operations for reading and searching resources can return responses in either the
simple or the complex ontology schema. The complex schema is used by default.
To receive a response in the simple schema, use the HTTP request header or URL
parameter described in [API Schema](introduction.md#api-schema).

Each DSP-API v2 response describing one or more resources returns a
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
see [Gravsearch: Virtual Graph Search](query-language.md)).

See the interfaces `Resource` and `ResourcesSequence` in module
`ResourcesResponse` (exists for both API schemas: `ApiV2Simple` and
`ApiV2WithValueObjects`).

### Requesting Text Markup as XML

When requesting a text value with standoff markup, there are three possibilities:

1. The text value uses standard mapping.
2. The text value uses a custom mapping which *does not* specify an XSL transformation.
3. The text value uses a custom mapping which specifies an XSL transformation.

In the first case, the mapping will be defined as:

```json
"kb:textValueHasMapping": {
        "@id": "http://rdfh.ch/standoff/mappings/StandardMapping"
    }
```

the text value will only be available as `kb:textValueAsXml`, which will be of the following structure:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<text documentType="html">
   ...
</text>
```

where the content of `<text>` is a limited set of HTML tags that can be handled by CKEditor in DSP-APP.
This allows for both displaying and editing the text value.

In the second and third case, `kb:textValueHasMapping` will point to the custom mapping 
that may or may not specify an XSL transformation.

If no transformation is specified (second case), the text value will be returned only as `kb:textValueAsXml`.
This property will be a string containing the contents of the initially uploaded XML.

Note: The returned XML document is equivalent to the uploaded document but it is not necessarily identical -
the order of the attributes in one element may vary from the original.

In the third case, when a transformation is specified, both `kb:textValueAsXml` and `kb:textValueAsHtml` will be returned.
`kb:textValueAsHtml` is the result of the XSL transformation applied to `kb:textValueAsXml`.
The HTML representation is intended to display the text value in a human readable and properly styled way, 
while the XML representation can be used to update the text value.

## Get the Representation of a Resource by IRI

### Get a Full Representation of a Resource by IRI

A full representation of resource can be obtained by making a GET
request to the API providing its IRI. Because a DSP IRI has the format
of a URL, its IRI has to be URL-encoded.

To get the resource with the IRI `http://rdfh.ch/c5058f3a` (a
book from the sample Incunabula project, which is included in the DSP-API 
server's test data), make a HTTP GET request to the `resources`
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
[Get a Full Representation of a Resource by IRI](#get-a-full-representation-of-a-resource-by-iri),
and add the URL parameter `?version=TIMESTAMP`, where `TIMESTAMP` is an
[xsd:dateTimeStamp](https://www.w3.org/TR/xmlschema11-2/#dateTimeStamp) in the
UTC timezone. The timestamp can either be URL-encoded, or submitted with all
punctuation (`-`, `:`, and `.`) removed (this is to accept timestamps
from DSP's [ARK URLs](permalinks.md)).

The resource will be returned with the values that it had at the specified
time. Since DSP only versions values, not resource metadata (e.g.
`rdfs:label`), the current metadata will be returned.

Each value will be returned with the permissions that are attached to
the **current** version of the value
(see [Permissions](../../02-dsp-ontologies/knora-base.md#permissions)).

The returned resource will include the predicate `knora-api:versionDate`,
containing the timestamp that was submitted, and its `knora-api:versionArkUrl`
(see [Resource Permalinks](permalinks.md)) will contain the
same timestamp.

### Get a Value in a Resource

To get a specific value of a resource, use this route:

```
HTTP GET to http://host/v2/values/resourceIRI/valueUUID
```

The resource IRI must be URL-encoded. The path element `valueUUID` is the
string object of the value's `knora-api:valueHasUUID`.

The value will be returned within its containing resource, in the same format
as for [Responses Describing Resources](#responses-describing-resources),
but without any of the resource's other values.

### Get a Version of a Value in a Resource

To get a particular version of a specific value of a resource, use the route
described in [Get a Value in a Resource](#get-a-value-in-a-resource),
and add the URL parameter `?version=TIMESTAMP`, where `TIMESTAMP` is an
[xsd:dateTimeStamp](https://www.w3.org/TR/xmlschema11-2/#dateTimeStamp) in the
UTC timezone. The timestamp can either be URL-encoded, or submitted with all
punctuation (`-`, `:`, and `.`) removed (this is to accept timestamps
from DSP's [ARK URLs](permalinks.md)).

The value will be returned within its containing resource, in the same format
as for [Responses Describing Resources](#responses-describing-resources),
but without any of the resource's other values.

Since DSP only versions values, not resource metadata (e.g.
`rdfs:label`), the current resource metadata will be returned.

The value will be returned with the permissions that are attached to
its **current** version
(see [Permissions](../../02-dsp-ontologies/knora-base.md#permissions)).

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

```json
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
date range allows it). Each date is included only once. Since DSP only versions values, not
resource metadata (e.g. `rdfs:label`), changes to a resource's metadata are not included in its
version history.

To request the resource as it was at each of these dates, see
[Get a Full Representation of a Version of a Resource by IRI](#get-a-full-representation-of-a-version-of-a-resource-by-iri). 
For consistency in citation, we recommend using these dates when requesting resource versions.

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

DSP can return a graph of connections between resources, e.g. for generating a network diagram.

```
HTTP GET to http://host/v2/graph/resourceIRI[depth=Integer]
[direction=outbound|inbound|both][excludeProperty=propertyIri]
```

The first parameter must be preceded by a question mark `?`, any
following parameter by an ampersand `&`.

- `depth` must be at least 1. The maximum depth is a DSP configuration setting.
  The default is 4.
- `direction` specifies the direction of the links to be queried, i.e. links to
  and/or from the given resource. The default is `outbound`.
- `excludeProperty` is an optional link property to be excluded from the
  results.

To accommodate large graphs, the graph response format is very concise, and is therefore
simpler than the usual resources response format. Each resource represented only by its IRI,
class, and label. Direct links are shown instead of link values. For example:

```json
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

DSP offers the possibility to search for resources by their
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
specific. The first term should at least contain three characters. To
make this kind of "search as you type" possible, a wildcard character is
automatically added to the last search term. 

Characters provided by the user that have a special meaning in the Lucene Query Parser 
syntax need to be escaped. If a user wants to search for the string "Zeit-Glöcklein", she
needs to type "Zeit\-Glöcklein". The special characters that need escaping are: 
`+`, `-`, `&`, `|`, `!`, `(`, `)`, `[`, `]`, `{`, `}`, `^`, `"`, `~`, `*`, `?`, `:`, `\`, `/`

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

DSP offers a full-text search that searches through all textual
representations of values and `rdfs:label` of resources. 
Full-text search supports the 
[Lucene Query Parser syntax](../../07-lucene/lucene-query-parser-syntax.md).
Note that Lucene's default operator is a logical OR when submitting several search terms.

The search index used by DSP transforms all text into lower case characters and splits text into tokens by whitespace. 
For example, if a text value is: `The cake needs flour, sugar, and butter.`, 
the tokens are `the`, `cake`, `needs`, `flour,`, `sugar,`, `and`, `butter.`.
Note that punctuation marks like `,` and `.` are left with the word where they occurred.
Therefore, if you search for `sugar` you would have to use `sugar*` or `sugar?` 
to get results that contain `sugar,` or `sugar.` as well.
The reason for this kind of tokenization is 
that some users need to be able to search explicitly for special characters including punctuation marks.

Alphabetic, numeric, symbolic, and diacritical Unicode characters 
which are not in the first 127 ASCII characters (the "Basic Latin" Unicode block) 
are converted into their ASCII equivalents, if one exists, e.g. `é` or `ä` are converted into `e` and `a`.

Please note that the search terms have to be URL-encoded.

```
HTTP GET to http://host/v2/search/searchValue[limitToResourceClass=resourceClassIRI]
[limitToStandoffClass=standoffClassIri][limitToProject=projectIRI][offset=Integer]
```

The first parameter has to be preceded by a question mark `?`, any following parameter by an ampersand `&`.

A search value must have a minimal length of three characters (default value) 
as defined in `search-value-min-length` in `application.conf`.

A search term may contain wildcards. A `?` represents a single character. 
It has to be URL-encoded as `%3F` since it has a special meaning in the URL syntax. 
For example, the term `Uniform` can be search for like this:

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
in `results-per-page` in `application.conf`.

If the parameter `limitToStandoffClass` is provided, DSP will look for search terms
that are marked up with the indicated standoff class.

If the parameter `returnFiles=true` is provided, DSP will return any
file value attached to each matching resource.

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

For more complex queries than a full-text search, DSP offers a query language
called [Gravsearch: Virtual Graph Search](query-language.md)).

### Support of TEI/XML

To convert standoff markup to TEI/XML, see [TEI/XML](text/tei-xml.md).

### IIIF Manifests

This is an experimental feature and may change.

To generate a IIIF manifest for a resource, containing
the still image representations that have `knora-api:isPartOf` (or a subproperty)
pointing to that resource:

```
HTTP GET to http://host/v2/resources//iiifmanifest/RESOURCE_IRI
```

### Reading Resources by Class from a Project

To facilitate the development of tabular user interfaces for data entry, it is
possible to get a paged list of all the resources belonging to a particular
class in a given project, sorted by the value of a property:

```
HTTP GET to http://host/v2/resources?resourceClass=RESOURCE_CLASS_IRI&page=PAGE[&orderByProperty=PROPERTY_IRI]
```

This is useful only if the project does not contain a large amount of data;
otherwise, you should use [Gravsearch](query-language.md) to search
using more specific criteria.

The specified class and property are used without inference; they will not
match subclasses or subproperties.

The HTTP header `X-Knora-Accept-Project` must be submitted; its value is
a DSP project IRI. In the request URL, the values of `resourceClass` and `orderByProperty`
are URL-encoded IRIs in the [complex schema](introduction.md#api-schema).
The `orderByProperty` parameter is optional; if it is not supplied, resources will
be sorted alphabetically by resource IRI (an arbitrary but consistent order).
The value of `page` is a 0-based integer page number. Paging works as it does
in [Gravsearch](query-language.md)).

### Get the Full History of a Resource and its Values as Events

To get a list of the changes that have been made to a resource and its values since its creation as events ordered by 
date:

```
HTTP GET to http://host/v2/resources/resourceHistoryEvents/<resourceIRI>
```

The resource IRI must be URL-encoded. The response is a list of events describing changes made to the resource and its values,
 in chronological order. Each entry has the properties: 
 `knora-api:eventType` (the type of the operation performed on a specific date. The operation can be either
 `createdResource`, `updatedResourceMetadata`, `deletedResource`, `createdValue`, `updatedValueContent`, 
 `updatedValuePermissions`, or `deletedValue`.), 
`knora-api:versionDate` (the date when the change was made),
`knora-api:author` (the IRI of the user who made the change),
`knora-api:eventBody` (the information necessary to make the same request). 

For example, the following response contains the list of events describing the version history of the resource 
`http://rdfh.ch/0001/thing-with-history` ordered by date:

```json
{
  "@graph" : [ 
        {
            "knora-api:eventType": "createdResource",
            "knora-api:author": {
                "@id": "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
            },
            "knora-api:eventBody": {
                "rdfs:label": "A thing with version history",
                "knora-api:resourceIri": "http://rdfh.ch/0001/thing-with-history",
                "knora-api:resourceClassIri": "http://www.knora.org/ontology/0001/anything#Thing",
                "knora-api:hasPermissions": "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser",
                "knora-api:creationDate": {
                    "@value": "2019-02-08T15:05:10Z",
                    "@type": "xsd:dateTimeStamp"
                },
                "knora-api:attachedToProject": {
                    "@id": "http://rdfh.ch/projects/0001"
                }
            },
            "knora-api:versionDate": {
                "@value": "2019-02-08T15:05:10Z",
                "@type": "xsd:dateTimeStamp"
            }
        },
        {
            "knora-api:eventType": "createdValue",
            "knora-api:author": {
                "@id": "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
            },
            "knora-api:eventBody": {
                "knora-api:resourceIri": "http://rdfh.ch/0001/thing-with-history",
            "knora-api:resourceClassIri": "http://www.knora.org/ontology/0001/anything#Thing",
                "knora-api:valueCreationDate": {
                    "@value": "2019-02-10T10:30:10Z",
                    "@type": "xsd:dateTimeStamp"
                },
                "knora-api:valueHasUUID": "IZGOjVqxTfSNO4ieKyp0SA",
                "knora-api:hasPermissions": "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
                "@type": "knora-base:LinkValue",
                "http://www.knora.org/ontology/0001/anything#hasOtherThingValue": {
                    "knora-api:linkValueHasTargetIri": {
                        "@id": "http://rdfh.ch/0001/2qMtTWvVRXWMBcRNlduvCQ"
                    }
                },
                "rdf:Property": "http://www.knora.org/ontology/0001/anything#hasOtherThingValue",
                "@id": "http://rdfh.ch/0001/thing-with-history/values/3a"
            },
            "knora-api:versionDate": {
                "@value": "2019-02-10T10:30:10Z",
                "@type": "xsd:dateTimeStamp"
            }
        },
        {
            "knora-api:eventType": "updatedValueContent",
            "knora-api:author": {
                "@id": "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
            },
            "knora-api:eventBody": {
                "knora-api:resourceIri": "http://rdfh.ch/0001/thing-with-history",
                "knora-api:resourceClassIri": "http://www.knora.org/ontology/0001/anything#Thing"
                "http://www.knora.org/ontology/0001/anything#hasText": {
                    "knora-api:valueAsString": "two"
                },
                "knora-api:valueCreationDate": {
                    "@value": "2019-02-11T10:05:10Z",
                    "@type": "xsd:dateTimeStamp"
                },
                "knora-base:previousValue": "http://rdfh.ch/0001/thing-with-history/values/2a",
                "knora-api:valueHasUUID": "W5fm67e0QDWxRZumcXcs6g",
                "@type": "knora-base:TextValue",
                "rdf:Property": "http://www.knora.org/ontology/0001/anything#hasText",
                "@id": "http://rdfh.ch/0001/thing-with-history/values/2b"
            },
            "knora-api:versionDate": {
                "@value": "2019-02-11T10:05:10Z",
                "@type": "xsd:dateTimeStamp"
            }
        },
        {
            "knora-api:eventType": "deletedValue",
            "knora-api:author": {
                "@id": "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
            },
            "knora-api:eventBody": {
                "knora-api:resourceIri": "http://rdfh.ch/0001/thing-with-history",
                "knora-api:resourceClassIri": "http://www.knora.org/ontology/0001/anything#Thing",
                "knora-base:previousValue": "http://rdfh.ch/0001/thing-with-history/values/3a",
                "knora-api:deleteDate": {
                    "@type": "xsd:dateTimeStamp",
                    "@value": "2019-02-13T09:00:10Z"
                },
                "knora-api:isDeleted": true,
                "@type": "knora-base:LinkValue",
                "rdf:Property": "http://www.knora.org/ontology/0001/anything#hasOtherThingValue",
                "@id": "http://rdfh.ch/0001/thing-with-history/values/3b"
            },
            "knora-api:versionDate": {
                "@value": "2019-02-13T09:00:10Z",
                "@type": "xsd:dateTimeStamp"
            }
        }
    ],
  "@context" : {
    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
  }
}
```

Since the history of changes made to the metadata of a resource is not part of resouce's version history, there are no 
events describing the changes on metadata elements like its `rdfs:label` or `rdfs:comment`. 
The only record depicting a change in a resource's metadata is the `knora-api:lastModificationDate` of the resource. Thus 
the event `updatedResourceMetadata` indicates a change in a resource's metadata, its `knora-api:eventBody` contains the 
payload needed to update the value of the resource's `lastModificationDate`, see 
[modifying metadata of a resource](editing-resources.md#modifying-a-resources-metadata).


### Get the Full History of all Resources of a Project as Events

To get a list of the changes that have been made to the resources and their values of a project as events ordered by 
date:

```
HTTP GET to http://host/v2/resources/projectHistoryEvents/<projectIRI>
```

The project IRI must be URL-encoded. The response contains the resource history events of all resources that belong to 
the specified project.
