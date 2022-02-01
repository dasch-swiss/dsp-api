<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Permalinks

Knora provides a permanent, citable URL for each resource and value.
These URLs use [Archival Resource Key (ARK) Identifiers](http://n2t.net/e/ark_ids.html),
and are designed to remain valid even if the resource itself is moved
from one Knora repository to another.

## Obtaining ARK URLs

In the [complex schema](introduction.md#api-schema), a resource or value
is always returned with two ARK URLs: one that will always refer
to the latest version of the resource or value (`knora-api:arkUrl`), and one that refers
specifically to the version being returned (`knora-api:versionArkUrl`).
For example:

```json
{
  "@id" : "http://rdfh.ch/0803/2a6221216701",
  "@type" : "incunabula:book",
  "incunabula:book_comment" : {
    "@id" : "http://rdfh.ch/0803/2a6221216701/values/56c287fc9505",
    "@type" : "knora-api:TextValue",
    "knora-api:arkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://ark.dasch.swiss/ark:/72163/1/0803/2a6221216701W/dhaRsvZATjmOxhCOOzHqewB"
    },
    "knora-api:versionArkUrl" : {
      "@type" : "xsd:anyURI",
      "@value" : "http://ark.dasch.swiss/ark:/72163/1/0803/2a6221216701W/dhaRsvZATjmOxhCOOzHqewB.20160302T150521Z"
    },
    "knora-api:attachedToUser" : {
      "@id" : "http://rdfh.ch/users/91e19f1e01"
    },
    "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser",
    "knora-api:userHasPermission" : "V",
    "knora-api:valueAsString" : "Katalogaufnahme anhand ISTC und v.d.Haegen",
    "knora-api:valueCreationDate" : {
      "@type" : "xsd:dateTimeStamp",
      "@value" : "2016-03-02T15:05:21Z"
    },
    "knora-api:valueHasUUID" : "dhaRsvZATjmOxhCOOzHqew"
  },
  "knora-api:arkUrl" : {
    "@type" : "xsd:anyURI",
    "@value" : "http://ark.dasch.swiss/ark:/72163/1/0803/2a6221216701W"
  },
  "knora-api:versionArkUrl" : {
    "@type" : "xsd:anyURI",
    "@value" : "http://ark.dasch.swiss/ark:/72163/1/0803/2a6221216701W.20160302T150521Z"
  },
  "knora-api:attachedToProject" : {
    "@id" : "http://rdfh.ch/projects/0803"
  },
  "knora-api:attachedToUser" : {
    "@id" : "http://rdfh.ch/users/91e19f1e01"
  },
  "knora-api:creationDate" : {
    "@type" : "xsd:dateTimeStamp",
    "@value" : "2016-03-02T15:05:21Z"
  },
  "knora-api:hasPermissions" : "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser",
  "knora-api:userHasPermission" : "V",
  "rdfs:label" : "Reise ins Heilige Land",
  "@context" : {
    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
    "incunabula" : "http://0.0.0.0:3333/ontology/0803/incunabula/v2#",
    "xsd" : "http://www.w3.org/2001/XMLSchema#"
  }
}
```

In the [simple schema](introduction.md#api-schema), resources are returned
with ARK URLs, but values are returned as literals, so ARK URLs are not provided
for values.

For more information on getting past versions of resources and values, see:

- [Get a Full Representation of a Version of a Resource by IRI](reading-and-searching-resources.md#get-a-full-representation-of-a-version-of-a-resource-by-iri)
- [Get a Version of a Value in a Resource](reading-and-searching-resources.md#get-a-version-of-a-value-in-a-resource)
- [Get the Version History of a Resource](reading-and-searching-resources.md#get-the-version-history-of-a-resource)

## Resolving Knora ARK URLs

A Knora ARK URL is intended to be resolved by the [Knora ARK resolver](https://github.com/dhlab-basel/ark-resolver).

## Knora ARK URL Format

For details, see [Archival Resource Key (ARK) Identifiers](../../05-internals/design/api-v2/ark.md).

### ARK URLs for Projects

The format of a Knora project ARK URL is as follows:

```
http://HOST/ark:/NAAN/VERSION/PROJECT
```

`NAAN` is a
[Name Assigning Authority Number](https://tools.ietf.org/html/draft-kunze-ark-22#section-2.3),
`VERSION` is the version number of the Knora ARK URL format (currently always 1),
and `PROJECT` is the project's [short-code](knora-iris.md#project-short-codes).

For example, given a project with ID `0001`, and using the DaSCH's ARK resolver
hostname and NAAN, the ARK URL for the project itself is:

```
http://ark.dasch.swiss/ark:/72163/1/0001
```

This could redirect to a page describing the project.

### ARK URLs for Resources

The format of a Knora resource ARK URL is as follows:

```
http://HOST/ark:/NAAN/VERSION/PROJECT/RESOURCE_UUID[.TIMESTAMP]
```

`NAAN` is a
[Name Assigning Authority Number](https://tools.ietf.org/html/draft-kunze-ark-22#section-2.3),
`VERSION` is the version number of the Knora ARK URL format (currently always 1),
`PROJECT` is the project's [short-code](knora-iris.md#project-short-codes),
and `RESOURCE_UUID` is the resource's [UUID](knora-iris.md#iris-for-data).

For example, given the Knora resource IRI `http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ`,
and using the DaSCH's ARK resolver hostname and NAAN, the corresponding
ARK URL without a timestamp is:

```
http://ark.dasch.swiss/ark:/72163/1/0001/0C=0L1kORryKzJAJxxRyRQY
```

The same ARK URL with an optional timestamp is:

```
http://ark.dasch.swiss/ark:/72163/1/0001/0C=0L1kORryKzJAJxxRyRQY.20180604T085622513Z
```

Without a timestamp, a Knora resource ARK URL refers to the latest version of the
resource at the time when the URL is resolved.

### ARK URLs for Values

The format of a Knora value ARK URL is as follows:

```
http://HOST/ark:/NAAN/VERSION/PROJECT/RESOURCE_UUID/VALUE_UUID[.TIMESTAMP]
```

`NAAN` is a
[Name Assigning Authority Number](https://tools.ietf.org/html/draft-kunze-ark-22#section-2.3),
`VERSION` is the version number of the Knora ARK URL format (currently always 1),
`PROJECT` is the project's [short-code](knora-iris.md#project-short-codes),
`RESOURCE_UUID` is the resource's [UUID](knora-iris.md#iris-for-data), and `VALUE_UUID`
is the value's `knora-api:valueHasUUID`.

For example, given a value with `knora-api:valueHasUUID "4OOf3qJUTnCDXlPNnygSzQ"` in the resource
`http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ`, and using the DaSCH's ARK resolver
hostname and NAAN, the corresponding ARK URL without a timestamp is:

```
http://ark.dasch.swiss/ark:/72163/1/0001/0C=0L1kORryKzJAJxxRyRQY/4OOf3qJUTnCDXlPNnygSzQX
```

The same ARK URL with an optional timestamp is:

```
http://ark.dasch.swiss/ark:/72163/1/0001/0C=0L1kORryKzJAJxxRyRQY/4OOf3qJUTnCDXlPNnygSzQX.20180604T085622513Z
```

Without a timestamp, a Knora value ARK URL refers to the latest version of the
value at the time when the URL is resolved.
