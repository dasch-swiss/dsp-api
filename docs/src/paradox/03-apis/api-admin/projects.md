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

**Project Operations:**
- `GET:  /admin/projects` : return all projects
- `POST: /admin/projects` : create a new project
- `GET: /admin/projects/[iri | shortname | shortcode]/<identifier>` : returns a single project identified either through iri, shortname, or shortcode
- `PUT: /admin/projects/iri/<identifier>` : update a project identified by iri
- `DELETE: /admin/projects/iri/<identifier>` : update project status to false

**Project Member Operations:**
- `GET: /admin/projects/[iri | shortname | shortcode]/<identifier>/members` : returns all members part of a project identified through iri, shortname or shortcode

**Project Admin Member Operations:**
- `GET: /admin/projects/[iri | shortname | shortcode]/<identifier>/admin-members` : returns all admin members part of a project identified through iri, shortname or shortcode

**Project Keyword Operations:**
- `GET: /admin/projects/Keywords` : returns all unique keywords for all projects as a list
- `GET: /admin/projects/iri/<identifier>/Keywords` : returns all keywords for a single project

**Project Restricted View Settings Operations:**
- `GET: /admin/projects/iri/<identifier>/RestrictedViewSettings` : returns the project's restricted view settings


## Project Operations

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
    ```JSON
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
  - PUT: `/admin/projects/iri/<projectIri>`
  - BODY:
    ```JSON
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

### Delete project (update project status):

  - Required permission: SystemAdmin / ProjectAdmin
  - Remark: The same as updating a project and changing `status` to
    `false`. To un-delete, set `status` to `true`.
  - DELETE: `/admin/projects/iri/<projectIri>`
  - BODY: empty


## Project Member Operations

### Get project members:

  - Required permission: SystemAdmin / ProjectAdmin
  - Required information: project identifier
  - GET: `/admin/projects/[iri | shortname | shortcode]/<identifier>/members`


## Project Admin Member Operations

### Get project members:

  - Required permission: SystemAdmin / ProjectAdmin
  - Required information: project identifier
  - GET: `/admin/projects/[iri | shortname | shortcode]/<identifier>/admin-members`


### Restricted View Settings Operations

Operates on the following properties:
 - `knora-admin:projectRestrictedViewSize` - takes the IIIF size value
 - `knora-admin:projectRestrictedViewWatermark` - takes the path to the watermark image. **Currently not used.**

#### Get the restricted view settings:

  - Required permission: ProjectAdmin
  - Required information: `identifier`. The `identifier` can be the project's IRI, shortname or shortcode.
  - GET: `/admin/projects/[iri | shortname | shortcode]/<identifier>/RestrictedViewSettings`

## Example Data

The following is an example for project information stored in the `admin` named graph:

```
<http://rdfh.ch/projects/00FF>
    rdf:type knora-admin:knoraProject ;
    knora-admin:projectShortname "images"^^xsd:string ;
    knora-admin:projectShortcode "00FF"^^xsd:string ;
    knora-admin:projectLongname "Image Collection Demo"^^xsd:string ;
    knora-admin:projectDescription "A demo project of a collection of images"@en ;
    knora-admin:projectKeyword "images"^^xsd:string,
                              "collection"^^xsd:string ;
    knora-admin:projectRestrictedViewSize "!512,512"^^xsd:string ;
    knora-admin:projectRestrictedViewWatermark "path_to_image"^^xsd:string ;
    knora-admin:belongsToInstitution <http://rdfh.ch/institutions/dhlab-basel> ;
    knora-admin:status "true"^^xsd:boolean ;
    knora-admin:hasSelfJoinEnabled "false"^^xsd:boolean .
```

