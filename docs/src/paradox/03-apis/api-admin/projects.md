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

# Projects Endpoint

## Endpoint Overview

- GET: /admin/projects


  - **Create project**:

      - Required permission: SystemAdmin
      - Required information: shortname (unique; used for named graphs),
        status, selfjoin
      - Optional information: longname, description, keywords, logo
      - Returns information about the newly created project
      - Remark: There are two distinct use cases / payload combination:
        (1) change ontology and data graph: ontologygraph, datagraph,
        (2) basic project information: shortname, longname, description,
        keywords, logo, institution, status, selfjoin
      - TypeScript Docs: projectFormats - CreateProjectApiRequestV1
      - POST: `/v1/projects/`
      - BODY:

```
{
  "shortname": "newproject",
  "longname": "project longname",
  "description": "project description",
  "keywords": "keywords",
  "logo": "/fu/bar/baz.jpg",
  "status": true,
  "selfjoin": false
}
```

  - **Update project information**:

      - Required permission: SystemAdmin / ProjectAdmin
      - Changeable information: shortname, longname, description,
        keywords, logo, status, selfjoin
      - TypeScript Docs: projectFormats - ChangeProjectApiRequestV1
      - PUT: `/v1/projects/<projectIri>`
      - BODY:

```
{
  "shortname": "newproject",
  "longname": "project longname",
  "description": "project description",
  "keywords": "keywords",
  "logo": "/fu/bar/baz.jpg",
  "status": true,
  "selfjoin": false
}
```

  - **Get project members**

      - Required permission: SystemAdmin / ProjectAdmin
      - Required information: project IRI
      - GET: `/v1/projects/members/<projectIri>`

  - **Delete project (-\update project)**:

      - Required permission: SystemAdmin / ProjectAdmin
      - Remark: The same as updating a project and changing `status` to
        `false`. To un-delete, set `status` to `true`.
      - DELETE: `/v1/projects/<projectIri>`
      - BODY: empty

Example Project Information stored in admin named graph: :

```
<http://rdfh.ch/projects/[shortcode]>
     rdf:type knora-base:knoraProject ;
     knora-base:projectShortname "images" ;
     knora-base:projectShortcode "00FF" ;
     knora-base:projectLongname "Images Collection Demo" ;
     knora-base:projectOntology <http://www.knora.org/ontology/00FF/images> ;
     knora-base:isActiveProject "true"^^xsd:boolean ;
     knora-base:hasSelfJoinEnabled "false"^^xsd:boolean .
```

Migration Notes:

The `knora-base:projectOntologyGraph` was renamed to
`knora-base:projectOntology`. Also before it was a `xsd:string`, where
now it needs to be an IRI. The `knora-base:projectDataGraph` is removed.
The `knora-base:projectShortcode` property was added.

