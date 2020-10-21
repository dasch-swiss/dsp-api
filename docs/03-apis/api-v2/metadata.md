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

# Metadata Endpoint

## Endpoint Overview
The metadata of a project contains information about its scope, content, contributors, funding, etc. modeled with respect 
to [dsp-ontologies](https://github.com/dasch-swiss/dsp-ontologies) data model. Metadata information must be available for 
any [DaSCH](http://dasch.swiss/) project so that researchers can go through the projects and get an idea about every project.

## Creating Project Metadata Graph:
Currently, the metadata information modeled using [dsp-ontologies](https://github.com/dasch-swiss/dsp-ontologies) can be 
stored in the triplestore as raw RDF data. The IRI of the project for which metadata must be created should be given as 
the segment of the `PUT` request, as shown below:

```
PUT http://localhost:3333/v2/metadata/<?encodedProjectIRI>
``` 
Only admin users of a project or a system admin can create metadata information for a project. The `Content-Type` of the 
request must be set to `text/turtle` and the metadata must be given in the body of the request in **Turtle** format, for 
example as:

```json
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
After successful creation of the metadata graph, the API returns a success message containing the IRI of the project. 
To modify the metadata of a project, the updated metadata must be submitted anew through a `PUT` request which will 
overwrite the existing metadata. 

## Retrieving Project Metadata Graph:

Any user can retrieve the metadata infromation of a project using its IRI through a `GET` request as below:

`GET http://localhost:3333/v2/metadata/<?encodedProjectIRI>`

Upon success, the API returns the metadata information as JSON-LD in the following form:

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