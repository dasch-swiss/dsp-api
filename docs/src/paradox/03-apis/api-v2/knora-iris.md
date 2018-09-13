<!---
Copyright © 2015-2018 the contributors (see Contributors.md).

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

# Knora IRIs

@@toc

The IRIs used in Knora repositories and in the Knora API v2 follow
certain conventions.

## Project Short-Codes

A project short-code is a hexadecimal number of at least four digits,
assigned by the [DaSCH](http://dasch.swiss/) to uniquely identify a
Knora project regardless of where it is hosted. The IRIs of ontologies that
are built into Knora do not contain shortcodes; these ontologies implicitly
belong to the Knora system project.

A project-specific ontology IRI must always include its project shortcode.

Project ID `0000` is reserved for shared ontologies
(see @ref:[Shared Ontologies](#shared-ontologies)).

The range of project IDs from `0001` to `00FF` inclusive is reserved for
local testing. Thus, the first useful project will be `0100`.

In the beginning, Unil will use the IDs `0100` to `07FF`, and Unibas
`0800` to `08FF`.

## IRIs for Ontologies and Ontology Entities

### Internal Ontology IRIs

Knora makes a distinction between internal and external ontologies. Internal
ontologies are used in the triplestore, while external ontologies are used in
API v2. For each internal ontology, there is a corresponding external ontology. Some
internal ontologies are built into Knora, while others are
project-specific. Knora automatically generates external
ontologies based on project-specific internal ontologies.

Each internal ontology has an IRI, which is also the IRI of the named
graph that contains the ontology in the triplestore. An internal
ontology IRI has the form:

```
http://www.knora.org/ontology/PROJECT_SHORTCODE/ONTOLOGY_NAME
```

For example, the internal ontology IRI based on project code `0001` and ontology
name `example` would be:

```
http://www.knora.org/ontology/0001/example
```

An ontology name must be a valid XML
[NCName](https://www.w3.org/TR/xml-names/#NT-NCName).
The following names are reserved for built-in internal Knora ontologies:

  - `knora-base`
  - `standoff`
  - `salsah-gui`

Names starting with `knora` are reserved for future built-in Knora
ontologies. A project-specific ontology name may not start with the
letter `v` followed by a digit, and may not contain these reserved
words:

  - `knora`
  - `ontology`
  - `simple`
  - `shared`

### External Ontology IRIs

Unlike internal ontology IRIs, external ontology IRIs are meant to be
dereferenced as URLs. When an ontology IRI is dereferenced, the ontology
itself can be served either in a machine-readable format or as
human-readable documentation.

The IRI of an external Knora ontology has the form:

```
http://HOST[:PORT]/ontology/PROJECT_SHORTCODE/ONTOLOGY_NAME/API_VERSION
```

For built-in and shared ontologies, the host is always `api.knora.org`. Otherwise,
the hostname and port configured in `application.conf` under
`app.http.knora-api.host` and `app.http.knora-api.http-port` are used
(the port is omitted if it is 80).

This means that when a built-in or shared external ontology IRI is dereferenced,
the ontology can be served by a Knora API server running at
`api.knora.org`. When the external IRI of a non-shared, project-specific ontology is
dereferenced, the ontology can be served by Knora that
hosts the project. During development and testing, this could be
`localhost`.

The name of an external ontology is the same as the name of the
corresponding internal ontology, with one exception: the external form
of `knora-base` is called `knora-api`.

The API version identifier indicates not only the version of the API,
but also an API 'schema'. The Knora API v2 is available in two schemas:

  - A complex schema, which is suitable both for reading and for editing
    data. The complex schema represents values primarily as complex
    objects. Its version identifier is `v2`.
  - A simple schema, which is suitable for reading data but not for
    editing it. The simple schema facilitates interoperability between
    Knora ontologies and non-Knora ontologies, since it represents
    values primarily as literals. Its version identifier is `simple/v2`.

Other schemas could be added in the future for more specific use cases.

When requesting an ontology, the client requests a particular schema.
(This will also be true of most Knora API v2 requests: the client will
be able to specify which schema the response should be provided in.)

For example, suppose a Knora API server is running at
`knora.example.org` and hosts an ontology whose internal IRI is
`http://www.knora.org/ontology/0001/example`. That ontology can then be
requested using either of these IRIs:

  - `http://knora.example.org/ontology/0001/example/v2` (in the complex
    schema)
  - `http://knora.example.org/ontology/0001/example/simple/v2` (in the
    simple schema)

While the internal `example` ontology refers to definitions in
`knora-base`, the external `example` ontology that is served by the API
refers instead to a `knora-api` ontology, whose IRI depends on the
schema being used:

  - `http://api.knora.org/ontology/knora-api/v2` (in the complex
    schema)
  - `http://api.knora.org/ontology/knora-api/simple/v2` (in the simple
    schema)

### Ontology Entity IRIs

Knora ontologies use 'hash namespaces' (see [URI
Namespaces](https://www.w3.org/2001/sw/BestPractices/VM/http-examples/2006-01-18/#naming)).
This means that the IRI of an ontology entity (a class or property
definition) is constructed by adding a hash character (`#`) to the
ontology IRI, followed by the name of the entity. In Knora, an entity
name must be a valid XML
[NCName](https://www.w3.org/TR/xml-names/#NT-NCName).
Thus, if there is a class called `ExampleThing` in an ontology whose
internal IRI is `http://www.knora.org/ontology/0001/example`, that class
has the following IRIs:

  - `http://www.knora.org/ontology/0001/example#ExampleThing` (in the
    internal ontology)
  - `http://HOST[:PORT]/ontology/0001/example/v2#ExampleThing` (in the
    API v2 complex schema)
  - `http://HOST[:PORT]/ontology/0001/example/simple/v2#ExampleThing`
    (in the API v2 simple schema)

### Shared Ontologies

Knora does not normally allow a project to use classes or properties defined in
an ontology that belongs to another project. Each project must be free to change
its own ontologies, but this is not possible if they have been used in ontologies
or data created by other projects.

However, an ontology can be defined as shared, meaning that it can be used by
multiple projects, and that its creators will not change it in ways that could
affect other ontologies or data that are based on it. Specifically, in a shared
ontology, existing classes and properties cannot safely be changed, but new ones
can be added. (It is not even safe to add an optional cardinality to an existing
class, because this could cause subclasses to violate the rule that a class cannot
have a cardinality on property P as well as a cardinality on a subproperty of P;
see @ref:[Restrictions on Classes](../../02-knora-ontologies/knora-base.md#restrictions-on-classes).)

A standardisation process for shared ontologies is planned (issue @github[#523](#523)).

There is currently one project for shared ontologies:

```
http://www.knora.org/ontology/knora-base#DefaultSharedOntologiesProject
```

Its project code is `0000`. Additional projects for shared ontologies may be supported
in future.

The internal and external IRIs of shared ontologies always use the hostname
`api.knora.org`, and have an additional segment, `shared`, after `ontology`.
The project code can be omitted, in which case the default shared ontology
project, `0000`, is assumed. The sample shared ontology, `example-box`, has these IRIs:

  - `http://www.knora.org/ontology/shared/example-box` (internal)
  - `http://api.knora.org/ontology/shared/example-box/v2` (external, complex schema)
  - `http://api.knora.org/ontology/shared/example-box/simple/v2` (external, simple schema)

## IRIs for Data

Knora generates IRIs for data that it creates in the triplestore. Each
generated data IRI contains one or more @extref[UUID](rfc:4122)
identifiers to make it unique. To keep data IRIs relatively short, each UUID is
@extref[Base64](rfc:4648) encoded, using the 'URL
and Filename safe Base64 Alphabet' specified in Table 2 of @extref[RFC 4648](rfc:4648),
without padding; thus each UUID is a 22-character string.

Data IRIs are not currently intended to be dereferenced as URLs.
Instead, each Knora resource will have a corresponding
[ARK](https://tools.ietf.org/html/draft-kunze-ark-18) URL, which will be
handled by a server that redirects requests to the relevant Knora API
server (see @ref:[Knora Resource Permalinks](#knora-resource-permalinks)). However, every
generated data IRI begins with `http://rdfh.ch`. This domain is not
currently used, but it is owned by the [DaSCH](http://dasch.swiss/), so
it would be possible to make resource IRIs directly dereferenceable in
the future.

The formats of generated data IRIs for different types of objects are as
follows:

  - Resource: `http://rdfh.ch/PROJECT_SHORTCODE/RESOURCE_UUID`.
  - Value:
    `http://rdfh.ch/PROJECT_SHORTCODE/RESOURCE_UUID/values/VALUE_UUID`
  - Standoff tag:
    `http://rdfh.ch/PROJECT_SHORTCODE/RESOURCE_UUID/values/VALUE_UUID/STANDOFF_UUID`
  - XML-to-standoff mapping:
    `http://rdfh.ch/PROJECT_SHORTCODE/mappings/MAPPING_NAME`
  - XML-to-standoff mapping element:
    `http://rdfh.ch/PROJECT_SHORTCODE/mappings/MAPPING_NAME/elements/MAPPING_ELEMENT_UUID`
  - Project: `http://rdfh.ch/projects/PROJECT_SHORTCODE`
  - Group: `http://rdfh.ch/groups/PROJECT_SHORTCODE/GROUP_UUID`
  - Permission:
    `http://rdfh.ch/permissions/PROJECT_SHORTCODE/PERMISSION_UUID`
  - Lists: `http://rdfh.ch/lists/PROJECT_SHORTCODE/LIST_UUID`
  - User: `http://rdfh.ch/users/USER_UUID`

## Knora Resource Permalinks

TODO: document the use of
[ARK](https://tools.ietf.org/html/draft-kunze-ark-18) permalinks for
Knora resources.
