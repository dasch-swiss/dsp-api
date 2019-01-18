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

# Resource Permalinks

@@toc

Knora provides a permanent, citable URL for each resource. These
URLs use [Archival Resource Key (ARK) Identifiers](http://n2t.net/e/ark_ids.html),
and are designed to stay the same even if the resource itself is moved
from one Knora repository to another.

## Obtaining ARK URLs

The ARK URL of each resource is provided as the object of its `knora-api:arkUrl`
property (in both the simple schema and the complex schema). For example:

```jsonld
{
  "@id" : "http://rdfh.ch/0803/2a6221216701",
  "@type" : "incunabula:book",
  "knora-api:arkUrl" : {
    "@type" : "xsd:anyURI",
    "@value" : "http://ark.dasch.swiss/ark:/72163/1/0803/2a6221216701W"
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
  "knora-api:hasPermissions" : "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser|RV knora-base:UnknownUser",
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

## Resolving Knora ARK URLs

A Knora ARK URL is intended to be resolved by the Knora ARK resolver, a Python 3
program that can be found under `ark` in the Knora source
tree. See the file `README.md` in that directory for more information.

## Knora ARK URL Format

The format of a Knora ARK URL is as follows:

```
http://HOST/ark:/NAAN/VERSION/PROJECT/RESOURCE_UUID[.TIMESTAMP]
```

- `HOST`: the hostname of the ARK resolver.
- `NAAN`: the Name Assigning Authority Number (NAAN) that the ARK resolver uses.
- `VERSION`: the version of the Knora ARK URL format being used (always 1 for now).
- `PROJECT`: the @ref:[short code](knora-iris.md#project-short-codes) of the
  project that the resource belongs to.
- `RESOURCE_UUID`: the resource's @ref:[UUID](knora-iris.md#iris-for-data),
  after processing described below.
- `TIMESTAMP`: an optional timestamp indicating that the ARK URL represents
  the state of the resource at a specific time in the past. The format
  of the timestamp is an [ISO 8601](https://www.iso.org/iso-8601-date-and-time-format.html)
  date in Coordinated universal time (UTC), including date, time, and a 9-digit
  nano-of-second field, without the characters `-`, `:`, and `.` (because
  `-` and `.` are reserved characters in ARK, and `:` would have to be URL-encoded).
  Example: `20190118T102919000031660Z`.

The `RESOURCE_UUID` is processed as follows:

1. A check digit is calculated, using the algorithm in
  the Scala class `org.knora.webapi.util.Base64UrlCheckDigit`, and appended
  to the `RESOURCE_UUID`.
2. Any `-` characters in the resulting string are replaced with `=`, because
   `-` is a reserved character in ARK URLs.

For example, given the Knora resource IRI `http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ`,
and using the DaSCH's ARK resolver hostname and NAAN, the corresponding
ARK URL without a timestamp is:

```
http://ark.dasch.swiss/ark:/72163/1/0001/0C=0L1kORryKzJAJxxRyRQY
```

The same ARK URL with an optional timestamp is:

```
http://ark.dasch.swiss/ark:/72163/1/0001/0C=0L1kORryKzJAJxxRyRQY.20190118T102919000031660Z
```

Without a timestamp, a Knora ARK URL refers to the latest version of the
resource at the time when the URL is resolved. Knora currently returns ARK URLs
without timestamps, because querying past versions of resources is not yet
implemented (@github[#1115](#1115)). When it is implemented, Knora will return
ARK URLs with timestamps. The client can then remove the timestamp if necessary,
by removing the last `.` and all subsequent characters.
