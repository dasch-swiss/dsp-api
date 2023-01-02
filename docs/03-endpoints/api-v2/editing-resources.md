<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Creating and Editing Resources

## Creating a Resource

To create a new resources, use this route:

```
HTTP POST to http://host/v2/resources
```

The body of the request is a JSON-LD document in the
[complex API schema](introduction.md#api-schema), specifying the type,`rdfs:label`, and its Knora resource properties
and their values. The representation of the resource is the same as when it is returned in a `GET` request, except that
its `knora-api:attachedToUser` is not given, and the resource IRI and those of its values can be optionally specified.
The format of the values submitted is described in [Creating and Editing Values](editing-values.md). If there are multiple values for
a property, these must be given in an array.

For example, here is a request to create a resource with various value types:

```json
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
    "knora-api:hasPermissions" : "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher",
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

Permissions for the new resource can be given by adding `knora-api:hasPermissions`, a custom creation date can be
specified by adding `knora-api:creationDate`
(an [xsd:dateTimeStamp](https://www.w3.org/TR/xmlschema11-2/#dateTimeStamp)), and the resource's creator can be specfied
by adding `knora-api:attachedToUser`. For example:

```json
{
  "@type" : "anything:Thing",
  "anything:hasBoolean" : {
    "@type" : "knora-api:BooleanValue",
    "knora-api:booleanValueAsBoolean" : true
  },
  "knora-api:attachedToProject" : {
    "@id" : "http://rdfh.ch/projects/0001"
  },
  "knora-api:attachedToUser" : {
    "@id" : "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
  },
  "rdfs:label" : "test thing",
  "knora-api:hasPermissions" : "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher",
  "knora-api:creationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "2019-01-09T15:45:54.502951Z"
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

The format of the object of `knora-api:hasPermissions` is described in
[Permissions](../../02-dsp-ontologies/knora-base.md#permissions).

If permissions are not given, configurable default permissions are used (see
[Default Object Access Permissions](../../05-internals/design/api-admin/administration.md#default-object-access-permissions)
).

To create a resource, the user must have permission to create resources of that class in that project.

The predicate `knora-api:attachedToUser` can be used to specify a creator other than the requesting user only if the
requesting user is an administrator of the project or a system administrator. The specified creator must also have
permission to create resources of that class in that project.

In addition to the creation date, in the body of the request, it is possible to specify a custom IRI (
of [Knora IRI](knora-iris.md#iris-for-data) form) for a resource through the `@id` attribute which will then be assigned
to the resource; otherwise the resource will get a unique random IRI.

A custom resource IRI must be `http://rdfh.ch/PROJECT_SHORTCODE/` (where `PROJECT_SHORTCODE`
is the shortcode of the project that the resource belongs to) plus a custom ID string.

Similarly, it is possible to assign a custom IRI to the values using their `@id` attributes; if not given, random IRIs
will be assigned to the values.

A custom value IRI must be the IRI of the containing resource, followed by a `/values/` and a custom ID string.

An optional custom UUID of a value can also be given by adding `knora-api:valueHasUUID`. Each custom UUID must
be [base64url-encoded](https://tools.ietf.org/html/rfc4648#section-5) without padding. Each value of the new resource
can also have a custom creation date specified by adding `knora-api:creationDate`
(an [xsd:dateTimeStamp](https://www.w3.org/TR/xmlschema11-2/#dateTimeStamp)). For example:

```json
{
   "@id" : "http://rdfh.ch/0001/oveR1dQltEUwNrls9Lu5Rw",
   "@type" : "anything:Thing",
   "knora-api:attachedToProject" : {
     "@id" : "http://rdfh.ch/projects/0001"
   },
   "anything:hasInteger" : {
       "@id" : "http://rdfh.ch/0001/oveR1dQltEUwNrls9Lu5Rw/values/IN4R19yYR0ygi3K2VEHpUQ",
       "@type" : "knora-api:IntValue",
       "knora-api:intValueAsInt" : 10,
       "knora-api:valueHasUUID" : "IN4R19yYR0ygi3K2VEHpUQ",
       "knora-api:creationDate" : {
               "@type" : "xsd:dateTimeStamp",
               "@value" : "2020-06-04T12:58:54.502951Z"
       }
   },
   "rdfs:label" : "test thing with custom IRI",
   "knora-api:creationDate" : {
     "@type" : "xsd:dateTimeStamp",
     "@value" : "2019-01-09T15:45:54.502951Z"
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

The response is a JSON-LD document containing a
[preview](reading-and-searching-resources.md#get-the-preview-of-a-resource-by-iri)
of the resource.

## Modifying a Resource's Values

See [Creating and Editing Values](editing-values.md).

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
- `knora-api:lastModificationDate`: an `xsd:dateTimeStamp` representing the last modification date that is currently
  attached to the resource, if any. This is used to make sure that the resource has not been modified by someone else
  since you last read it.

The submitted JSON-LD object must also contain one or more of the following predicates, representing the metadata you
want to change:

- `rdfs:label`: a string
- `knora-api:hasPermissions`, in the format described
  in [Permissions](../../02-dsp-ontologies/knora-base.md#permissions)
- `knora-api:newModificationDate`: an [xsd:dateTimeStamp](https://www.w3.org/TR/xmlschema11-2/#dateTimeStamp).

Here is an example:

```json
{
  "@id" : "http://rdfh.ch/0001/a-thing",
  "@type" : "anything:Thing",
  "rdfs:label" : "this is the new label",
  "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:ProjectMember",
  "knora-api:lastModificationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "2017-11-20T15:55:17Z"
  },
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

If you submit a `knora-api:lastModificationDate` that is different from the resource's actual last modification date,
you will get an HTTP 409 (Conflict) error.

If you submit a `knora-api:newModificationDate` that is earlier than the resource's `knora-api:lastModificationDate`,
you will get an HTTP 400 (Bad Request) error.

A successful response is an HTTP 200 (OK) status containing the resource's metadata.

## Deleting a Resource

Knora does not normally delete resources; instead, it marks them as deleted, which means that they do not appear in
normal query results.

To mark a resource as deleted, use this route:

```
HTTP POST to http://host/v2/resources/delete
```

The request body is a JSON-LD object containing the following information about the resource:

- `@id`: the resource's IRI
- `@type`: the resource's class IRI
- `knora-api:lastModificationDate`: an `xsd:dateTimeStamp` representing the last modification date that is currently
  attached to the resource, if any. This is used to make sure that the resource has not been modified by someone else
  since you last read it.

```json
{
  "@id" : "http://rdfh.ch/0001/a-thing",
  "@type" : "anything:Thing",
  "knora-api:lastModificationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "2019-02-05T17:05:35.776747Z"
  },
  "knora-api:deleteComment" : "This resource was created by mistake.",
  "@context" : {
    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#",
    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  }
}
```

The optional property `knora-api:deleteComment` specifies a comment to be attached to the resource, explaining why it
has been marked as deleted.

The optional property `knora-api:deleteDate`
(an [xsd:dateTimeStamp](https://www.w3.org/TR/xmlschema11-2/#dateTimeStamp))
indicates when the resource was marked as deleted; if not given, the current time is used.

The response is a JSON-LD document containing the predicate `knora-api:result` with a confirmation message.

### Requesting Deleted Resources

Resources marked as deleted are not found in search queries. It is however possible to request them directly or from an
ARK URL. In these instances, the API will not return the deleted resource, but instead a generic resource of type 
`knora-base:DeletedResource`. This resource will be similar to the requested resource, having e.g. the same IRI.
The resource will contain the deletion date and optionally the deletion comment.

The response to requesting a deleted resource will look as the following example:

```json
{
    "rdfs:label": "Deleted Resource",
    "knora-api:versionArkUrl": {
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0001/a=thingO.20211214T084407677335Z",
        "@type": "xsd:anyURI"
    },
    "knora-api:attachedToProject": {
        "@id": "http://rdfh.ch/projects/0001"
    },
    "knora-api:userHasPermission": "CR",
    "knora-api:attachedToUser": {
        "@id": "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
    },
    "knora-api:hasPermissions": "CR knora-admin:ProjectMember|V knora-admin:ProjectMember",
    "knora-api:isDeleted": true,
    "@type": "knora-api:DeletedResource",
    "@id": "http://rdfh.ch/0001/a-thing",
    "knora-api:deleteComment": "This resource is too boring.",
    "knora-api:arkUrl": {
        "@value": "http://0.0.0.0:3336/ark:/72163/1/0001/a=thingO",
        "@type": "xsd:anyURI"
    },
    "knora-api:creationDate": {
        "@value": "2021-12-14T08:44:07.677335Z",
        "@type": "xsd:dateTimeStamp"
    },
    "knora-api:deleteDate": {
        "@type": "xsd:dateTimeStamp",
        "@value": "2021-12-14T08:44:07.372543Z"
    },
    "@context": {
        "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
        "xsd": "http://www.w3.org/2001/XMLSchema#",
        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
    }
}
```

### Links to Deleted Resources

If resource `A` has a link to resource `B`, and resource
`B` is later marked as deleted, `A`'s link will still exist. DSP-API v2 will still return the link when `A` is queried,
but without any information about `B` (except for `B`'s IRI). If `A`'s link is necessary to meet the requirements of a
cardinality, marking `B` as deleted will not violate the cardinality.

The reason for this design is that `A` and `B` might be in different projects, and each project must retain control of
its resources and be able to mark them as deleted, even if they are used by another project.

## Erasing a Resource from the Triplestore

Normally, resources are not actually removed from the triplestore; they are only marked as deleted (see
[Deleting a Resource](#deleting-a-resource)). However, sometimes it is necessary to erase a resource from the
triplestore. To do so, use this route:

```
HTTP POST to http://host/v2/resources/erase
```

The request body is the same as for [Deleting a Resource](#deleting-a-resource), except that `knora-api:deleteComment`
is not relevant and will be ignored.

To do this, a user must be a system administrator or an administrator of the project containing the resource. The user's
permissions on the resource are not otherwise checked.

A resource cannot be erased if any other resource has a link to it. Any such links must first be changed or marked as
deleted (see [Updating a Value](editing-values.md#updating-a-value) and
[Deleting a Value](editing-values.md#deleting-a-value)). Then, when the resource is erased, the deleted link values that
referred to it will also be erased.

This operation cannot be undone (except by restoring the repository from a backup), so use it with care.
