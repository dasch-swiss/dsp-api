<!---
Copyright © 2015-2021 the contributors (see Contributors.md).

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

# Metadata Endpoint

## Endpoint Overview

The metadata of a project contains information about its scope, content, contributors, funding, etc. modeled according to
to the [dsp-ontologies](https://github.com/dasch-swiss/dsp-ontologies) data model. Metadata information must be available for 
any [DaSCH](http://dasch.swiss/) project so that researchers can go through the projects and get an idea about every project.

## Creating and Updating Project Metadata

Project metadata must correspond to the [dsp-ontologies](https://github.com/dasch-swiss/dsp-ontologies).

To create or update project metadata for a project, submit it in a `PUT` request, specifying the project
IRI in the URL path:

```
PUT http://host/v2/metadata/PROJECT_IRI
```

Currently, all the metadata for a project must be submitted in a single request. The submitted metadata
replaces any metadata that has already been stored for the project. Only an administrator of the project,
or a system administrator, can create or update project metadata.

The metadata can be submitted in  **Turtle**, **JSON-LD**, or **RDF/XML** format. The request must
include a `Content-Type` header with one of the following values:

| Format  | MIME Type             |
|---------|-----------------------|
| JSON-LD | `application/ld+json` |
| Turtle  | `text/turtle`         |
| RDF/XML | `application/rdf+xml` |

An example request in Turtle format:

```turtle
@prefix dsp-repo: <http://ns.dasch.swiss/repository#> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@base <http://ns.dasch.swiss/repository#> .

<beol> rdf:type dsp-repo:Project .
<beol> dsp-repo:hasName "Bernoulli-Euler Online (BEOL)" .
<beol> dsp-repo:hasKeywords "mathematics" .
<beol> dsp-repo:hasKeywords "science" .
<beol> dsp-repo:hasKeywords "history of science" .
<beol> dsp-repo:hasKeywords "history of mathematics" .
<beol> dsp-repo:hasCategories "mathematics" .
<beol> dsp-repo:hasStartDate "2016.07" .
<beol> dsp-repo:hasEndDate "2020.01" .
<beol> dsp-repo:hasFunder "Schweizerischer Nationalfonds (SNSF)" .
```

After successful creation of the metadata graph, the API returns HTTP 200 with a confirmation message.

## Retrieving Project Metadata

Any user can retrieve the metadata information for a project by providing its IRI in a `GET` request:

```
GET http://host/v2/metadata/PROJECT_IRI
```

The metadata can be returned in any of the formats listed in the previous section. By default, JSON-LD
is returned. To request another format, specify it in the `Accept` header of the request.

An example response in JSON-LD format:

```json
{
    "http://ns.dasch.swiss/repository#hasName": "Bernoulli-Euler Online (BEOL)",
    "http://ns.dasch.swiss/repository#hasFunder": "Schweizerischer Nationalfonds (SNSF)",
    "http://ns.dasch.swiss/repository#hasKeywords": [
        "science",
        "mathematics",
        "history of science",
        "history of mathematics"
    ],
    "http://ns.dasch.swiss/repository#hasEndDate": "2020.01",
    "http://ns.dasch.swiss/repository#hasCategories": "mathematics",
    "@type": "http://ns.dasch.swiss/repository#Project",
    "http://ns.dasch.swiss/repository#hasStartDate": "2016.07",
    "@id": "http://ns.dasch.swiss/beol"
}
```
