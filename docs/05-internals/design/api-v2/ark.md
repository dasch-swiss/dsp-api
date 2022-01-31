<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Archival Resource Key (ARK) Identifiers

## Requirements

Knora must produce an ARK URL for each resource and each value. The ARK identifiers used
by Knora must respect
[the draft ARK specification](https://tools.ietf.org/html/draft-kunze-ark-22).
The format of Knora’s ARK URLs must be able to change over
time, while ensuring that previously generated ARK URLs still work.

## Design

### ARK URL Format

The format of a Knora ARK URL is as follows:

```
http://HOST/ark:/NAAN/VERSION/PROJECT/RESOURCE_UUID[/VALUE_UUID][.TIMESTAMP]
```

- `HOST`: the hostname of the ARK resolver.

- `NAAN`: the Name Assigning Authority Number (NAAN) that the ARK resolver uses.

- `VERSION`: the version of the Knora ARK URL format being used (always 1 for now).

- `PROJECT`: the [short code](../../../03-apis/api-v2/knora-iris.md#project-short-codes) of the
  project that the resource belongs to.

- `RESOURCE_UUID`: the resource's unique ID, which is normally a
  [base64url-encoded](https://tools.ietf.org/html/rfc4648#section-5) UUID, as described in
  [IRIs for Data](../../../03-apis/api-v2/knora-iris.md#iris-for-data).

- `VALUE_UUID`: optionally, the `knora-base:valueHasUUID` of one of the
  resource's values, normally a
  [base64url-encoded](https://tools.ietf.org/html/rfc4648#section-5) UUID, as described in
  [IRIs for Data](../../../03-apis/api-v2/knora-iris.md#iris-for-data).

- `TIMESTAMP`: an optional timestamp indicating that the ARK URL represents
  the state of the resource at a specific time in the past. The format
  of the timestamp is an [ISO 8601](https://www.iso.org/iso-8601-date-and-time-format.html)
  date in Coordinated universal time (UTC), including date, time, and an optional
  nano-of-second field (of at most 9 digits), without the characters `-`, `:`, and `.` (because
  `-` and `.` are reserved characters in ARK, and `:` would have to be URL-encoded).
  Example: `20180528T155203897Z`.

Following the ARK ID spec, `/`
[represents object hierarchy](https://tools.ietf.org/html/draft-kunze-ark-22#section-2.5.1)
and `.` [represents an object variant](https://tools.ietf.org/html/draft-kunze-ark-22#section-2.5.2).
A value is thus contained in a resource, which is contained in its project,
which is contained in a repository (represented by the URL version number).
A timestamp is a type of variant.

Since sub-objects are optional, there is also implicitly an ARK URL
for each project, as well as for the repository as a whole.

The `RESOURCE_UUID` and `VALUE_UUID` are processed as follows:

1. A check digit is calculated, using the algorithm in
   the Scala class `org.knora.webapi.util.Base64UrlCheckDigit`, and appended
   to the UUID.

2. Any `-` characters in the resulting string are replaced with `=`, because
   `base64url` encoding uses `-`, which is a reserved character in ARK URLs.

For example, given a project with ID `0001`, and using the DaSCH's ARK resolver
hostname and NAAN, the ARK URL for the project itself is:

```
http://ark.dasch.swiss/ark:/72163/1/0001
```

Given the Knora resource IRI `http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ`,
the corresponding ARK URL without a timestamp is:

```
http://ark.dasch.swiss/ark:/72163/1/0001/0C=0L1kORryKzJAJxxRyRQY
```

The same ARK URL with an optional timestamp is:

```
http://ark.dasch.swiss/ark:/72163/1/0001/0C=0L1kORryKzJAJxxRyRQY.20180528T155203897Z
```

Given a value with `knora-api:valueHasUUID "4OOf3qJUTnCDXlPNnygSzQ"` in the resource
`http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ`, and using the DaSCH's ARK resolver
hostname and NAAN, the corresponding ARK URL without a timestamp is:

```
http://ark.dasch.swiss/ark:/72163/1/0001/0C=0L1kORryKzJAJxxRyRQY/4OOf3qJUTnCDXlPNnygSzQX
```

The same ARK URL with an optional timestamp is:

```
http://ark.dasch.swiss/ark:/72163/1/0001/0C=0L1kORryKzJAJxxRyRQY/4OOf3qJUTnCDXlPNnygSzQX.20180604T085622513Z
```

### Serving ARK URLs

`SmartIri` converts Knora resource IRIs to ARK URLs. This conversion is invoked in `ReadResourceV2.toJsonLD`,
when returning a resource's metadata in JSON-LD format.

### Resolving Knora ARK URLs

A Knora ARK URL is intended to be resolved by the [Knora ARK resolver](https://github.com/dhlab-basel/ark-resolver).
