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

# Editing Resources

@@toc

## Creating a Resource

To create a new resources, use this route:

```
HTTP POST to http://host/v2/resources
```

The body of the request is a JSON-LD document in the
@ref:[complex API schema](introduction.md#api-schema), specifying the resource's IRI, type,
and `rdfs:label`, along with its Knora resource properties and their values. The representation of the
resource is the same as when it is returned in a `GET` request, except that its IRI and
`knora-api:attachedToUser`, and those of its values, are not given. The format of the values submitted
is described in @ref:[Editing Values](editing-values.md). If there are multiple values for a property,
these must be given in an array.

For example, here is a request to create a resource with various value types:

```jsonld
{
  "@type" : "anything:Thing",
  "anything:hasBoolean" : {
    "@type" : "knora-api:BooleanValue",
    "knora-api:booleanValueAsBoolean" : true
  },
  "anything:hasColor" : {
    "@type" : "knora-api:ColorValue",
    "knora-api:colorValueAsColor" : "#ff3333"
  },
  "anything:hasDate" : {
    "@type" : "knora-api:DateValue",
    "knora-api:dateValueHasCalendar" : "GREGORIAN",
    "knora-api:dateValueHasEndEra" : "CE",
    "knora-api:dateValueHasEndYear" : 1489,
    "knora-api:dateValueHasStartEra" : "CE",
    "knora-api:dateValueHasStartYear" : 1489
  },
  "anything:hasDecimal" : {
    "@type" : "knora-api:DecimalValue",
    "knora-api:decimalValueAsDecimal" : {
      "@type" : "xsd:decimal",
      "@value" : "100000000000000.000000000000001"
    }
  },
  "anything:hasGeometry" : {
    "@type" : "knora-api:GeomValue",
    "knora-api:geometryValueAsGeometry" : "{\"status\":\"active\",\"lineColor\":\"#ff3333\",\"lineWidth\":2,\"points\":[{\"x\":0.08098591549295775,\"y\":0.16741071428571427},{\"x\":0.7394366197183099,\"y\":0.7299107142857143}],\"type\":\"rectangle\",\"original_index\":0}"
  },
  "anything:hasGeoname" : {
    "@type" : "knora-api:GeonameValue",
    "knora-api:geonameValueAsGeonameCode" : "2661604"
  },
  "anything:hasInteger" : [ {
    "@type" : "knora-api:IntValue",
    "knora-api:hasPermissions" : "CR knora-base:Creator|V http://rdfh.ch/groups/0001/thing-searcher",
    "knora-api:intValueAsInt" : 5,
    "knora-api:valueHasComment" : "this is the number five"
  }, {
    "@type" : "knora-api:IntValue",
    "knora-api:intValueAsInt" : 6
  } ],
  "anything:hasInterval" : {
    "@type" : "knora-api:IntervalValue",
    "knora-api:intervalValueHasEnd" : {
      "@type" : "xsd:decimal",
      "@value" : "3.4"
    },
    "knora-api:intervalValueHasStart" : {
      "@type" : "xsd:decimal",
      "@value" : "1.2"
    }
  },
  "anything:hasListItem" : {
    "@type" : "knora-api:ListValue",
    "knora-api:listValueAsListNode" : {
      "@id" : "http://rdfh.ch/lists/0001/treeList03"
    }
  },
  "anything:hasOtherThingValue" : {
    "@type" : "knora-api:LinkValue",
    "knora-api:linkValueHasTargetIri" : {
      "@id" : "http://rdfh.ch/0001/a-thing"
    }
  },
  "anything:hasRichtext" : {
    "@type" : "knora-api:TextValue",
    "knora-api:textValueAsXml" : "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text><p><strong>this is</strong> text</p> with standoff</text>",
    "knora-api:textValueHasMapping" : {
      "@id" : "http://rdfh.ch/standoff/mappings/StandardMapping"
    }
  },
  "anything:hasText" : {
    "@type" : "knora-api:TextValue",
    "knora-api:valueAsString" : "this is text without standoff"
  },
  "anything:hasUri" : {
    "@type" : "knora-api:UriValue",
    "knora-api:uriValueAsUri" : {
      "@type" : "xsd:anyURI",
      "@value" : "https://www.knora.org"
    }
  },
  "knora-api:attachedToProject" : {
    "@id" : "http://rdfh.ch/projects/0001"
  },
  "rdfs:label" : "test thing",
  "@context" : {
    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#",
    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

Permissions for the new resource can be given by adding `knora-api:hasPermissions`, and a custom creation date
can be specified by adding `knora-api:creationDate`
(an [xsd:dateTimeStamp](https://www.w3.org/TR/xmlschema11-2/#dateTimeStamp)). For example:

```jsonld
{
  "@type" : "anything:Thing",
  "anything:hasBoolean" : {
    "@type" : "knora-api:BooleanValue",
    "knora-api:booleanValueAsBoolean" : true
  },
  "knora-api:attachedToProject" : {
    "@id" : "http://rdfh.ch/projects/0001"
  },
  "rdfs:label" : "test thing",
  "knora-api:hasPermissions" : "CR knora-base:Creator|V http://rdfh.ch/groups/0001/thing-searcher",
  "knora-api:creationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "2019-01-09T15:45:54.502951Z"
  }
  "@context" : {
    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#",
    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

The format of the object of `knora-api:hasPermissions` is described in
@ref:[Permissions](../../02-knora-ontologies/knora-base.md#permissions).

If permissions are not given, configurable default permissions are used
(see @ref:[Default Object Access Permissions](../../05-internals/design/api-admin/administration.md#default-object-access-permissions)).

To create a resource, the user must have permission to create resources in the specified project.

The response is a JSON-LD document containing a
@ref:[preview](reading-and-searching-resources.md#get-the-preview-of-a-resource-by-its-iri)
of the resource.

## Modifying a Resource's Values

See @ref:[Editing Values](editing-values.md).

## Modifying a Resource's Metadata

You can modify the following metadata attached to a resource:

- label
- permissions
- last modification date

To do this, use this route:

```
HTTP PUT to http://host/v2/resources
```

The request body is a JSON-LD object containing the following information about the resource:

- `@id`: the resource's IRI
- `@type`: the resource's class IRI
- `knora-api:lastModificationDate`: an `xsd:dateTimeStamp` representing the last modification date that is currently attached to the resource, if any

The submitted JSON-LD object must also contain one or more of the following predicates, representing the metadata you want to change:

- `rdfs:label`: a string
- `knora-api:hasPermissions`, in the format described in @ref:[Permissions](../../02-knora-ontologies/knora-base.md#permissions)
- `knora-api:newModificationDate`: an [xsd:dateTimeStamp](https://www.w3.org/TR/xmlschema11-2/#dateTimeStamp).
  This is used to make sure that the resource has not been modified by someone else since you last read it.

Here is an example:

```jsonld
{
  "@id" : "http://rdfh.ch/0001/a-thing",
  "@type" : "anything:Thing",
  "rdfs:label" : "this is the new label",
  "knora-api:hasPermissions" : "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:ProjectMember",
  "knora-api:lastModificationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "2017-11-20T15:55:17Z"
  }
  "knora-api:newModificationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "2018-12-21T16:56:18Z"
  },
  "@context" : {
    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#",
    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

If you submit a `knora-api:lastModificationDate` that is different from the resource's actual last modification
date, you will get an HTTP 409 (Conflict) error.

If you submit a `knora-api:newModificationDate` that is earlier than the resource's `knora-api:lastModificationDate`,
you will get an HTTP 400 (Bad Request) error.

A successful response is an HTTP 200 (OK) status containing a confirmation message.
