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

**Old style:**
- `GET:  /admin/projects` : return all projects
- `POST: /admin/projects` : create a new project
- `GET: /admin/projects/[identifier]` : returns a single project identified either through iri, shortname, or shortcode
- `PUT: /admin/projects/[identifier]` : update a project identified by iri
- `DELETE: /admin/projects/[identifier]` : update project status to false
- `GET: /admin/projects/members/[identifier]` : returns all members part of a project identified through iri, shortname or shortcode
- `GET: /admin/projects/admin-members/[identifier]` : returns all admin members part of a project identified through iri, shortname or shortcode
- `GET: /admin/projects/keywords` : returns all unique keywords for all projects as a list
- `GET: /admin/projects/keywords/[identifier]` : returns all keywords for a single project

**New style:**
- `GET: /admin/projects/[identifier]/commands/`


### Create a new project:

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
      - POST: `/admin/projects/`
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

### Update project information:

      - Required permission: SystemAdmin / ProjectAdmin
      - Changeable information: shortname, longname, description,
        keywords, logo, status, selfjoin
      - TypeScript Docs: projectFormats - ChangeProjectApiRequestV1
      - PUT: `/admin/projects/<projectIri>`
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

### Get project members:

      - Required permission: SystemAdmin / ProjectAdmin
      - Required information: project IRI
      - GET: `/admin/projects/members/<projectIri>`


### Delete project (update project status):

      - Required permission: SystemAdmin / ProjectAdmin
      - Remark: The same as updating a project and changing `status` to
        `false`. To un-delete, set `status` to `true`.
      - DELETE: `/admin/projects/<projectIri>`
      - BODY: empty


## Example Data

The following is an example for project information stored in admin named graph:

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

