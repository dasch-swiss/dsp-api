<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Projects Endpoint

| Scope    | Route                                   | Operations | Explanation                               |
| -------- | --------------------------------------- | ---------- | ----------------------------------------- |
| projects | `/admin/projects`                       | `GET`      | [get all projects](#get-all-projects)     |
| projects | `/admin/projects`                       | `POST`     | [create a project](#create-a-new-project) |
| projects | `/admin/projects/iri/{iri}`             | `GET`      | [get a single project]()                  |
| projects | `/admin/projects/shortname/{shortname}` | `GET`      | [get a single project]()                  |
| projects | `/admin/projects/shortcode/{shortcode}` | `GET`      | [get a single project]()                  |
| projects | `/admin/projects/uuid/{uuid}`           | `GET`      | [get a single project]()                  |


## Project Operations

### Get All Projects

Permissions: No permissions required

Request definition: `GET /admin/projects`

Description: Returns a list of all projects.

Example request:

```bash
curl --request GET --url http://localhost:3333/admin/projects
```

Example response:

```jsonc
{
  "projects": [
    {
      "description": [
        {
          "value": "A demo project of a collection of images",
          "language": "en"
        }
      ],
      "id": "http://rdfh.ch/projects/MTvoB0EJRrqovzRkWXqfkA",
      "keywords": [
        "collection",
        "images"
      ],
      "logo": null,
      "longname": "Image Collection Demo",
      "ontologies": [
        "http://0.0.0.0:3333/ontology/00FF/images/v2"
      ],
      "selfjoin": false,
      "shortcode": "00FF",
      "shortname": "images",
      "status": true
    },
    {
      // ...
    }
  ]
}
```

### Create a New Project:

Permissions: SystemAdmin

Request definition: `POST /admin/projects`

Description: Create a new project.

Required payload:

- `shortcode` (unique, 4-digits)
- `shortname` (unique, it should be in the form of a [xsd:NCNAME](https://www.w3.org/TR/xmlschema11-2/#NCName) and it 
  should be URL safe)
- `description` (collection of descriptions as strings with language tag)
- `keywords` (collection of keywords)
- `status` (true, if project is active. false, if project is inactive)
- `selfjoin`

Optional payload:

- `id` (unique, custom DSP IRI, e.g. used for migrating a project from one server to another)
- `longname`
- `logo`


Example request:

```bash
curl --request POST \
  --url http://localhost:3333/admin/projects \
  --header 'Authorization: Basic cm9vdEBleGFtcGxlLmNvbTp0ZXN0' \
  --header 'Content-Type: application/json' \
  --data '{
    "shortname": "newproject",
    "shortcode": "3333",
    "longname": "project longname",
    "description": [
        {
            "value": "project description",
            "language": "en"
        }
    ],
    "keywords": [
        "test project"
    ],
    "logo": "/fu/bar/baz.jpg",
    "status": true,
    "selfjoin": false
}'
```

Example response:

```jsonc
{
    "project": {
        "description": [
            {
                "value": "project description",
                "language": "en"
            }
        ],
        "id": "http://rdfh.ch/projects/SvsqNHGeT_ao7Z-Ani5VNg",
        "keywords": [
            "test project"
        ],
        "logo": "/fu/bar/baz.jpg",
        "longname": "project longname",
        "ontologies": [],
        "selfjoin": false,
        "shortcode": "3333",
        "shortname": "newproject",
        "status": true
    }
}
```

Errors:

- `400 Bad Request` if the project already exists or any of the provided properties is invalid.
- `401 Unauthorized` if authorization failed.
    

#### Default set of permissions for a new project:
When a new project is created, following default permissions are added to its admins and members:

- ProjectAdmin group receives an administrative permission to do all project level operations and to create resources 
within the new project. This administrative permission is retrievable through its IRI:
`http://rdfh.ch/permissions/[projectShortcode]/defaultApForAdmin`

- ProjectAdmin group also gets a default object access permission to change rights (which includes delete, modify, view, 
and restricted view permissions) of any entity that belongs to the project. This default object access permission is retrievable 
through its IRI: 
`http://rdfh.ch/permissions/[projectShortcode]/defaultDoapForAdmin`

- ProjectMember group receives an administrative permission to create resources within the new project. This 
administrative permission is retrievable through its IRI:
`http://rdfh.ch/permissions/[projectShortcode]/defaultApForMember`

- ProjectMember group also gets a default object access permission to modify (which includes view and restricted view 
permissions) of any entity that belongs to the project. This default object access permission is retrievable through its IRI: 
`http://rdfh.ch/permissions/[projectShortcode]/defaultDoapForMember`



---


**Project Operations:**  

- `GET: /admin/projects/[iri | shortname | shortcode | uuid]/<identifier>` : returns a single project identified either through iri, shortname, shortcode or UUID

- `PUT: /admin/projects/iri/<identifier>` : update a project identified by iri  

- `DELETE: /admin/projects/iri/<identifier>` : update project status to false  

- `GET: /admin/projects/iri/<identifier>/AllData` : returns a TriG file containing the project's data  

**Project Member Operations:**  

- `GET: /admin/projects/[iri | shortname | shortcode | uuid]/<identifier>/members` : returns all members part of a project identified through iri, shortname, shortcode or UUID

**Project Admin Member Operations:**  

- `GET: /admin/projects/[iri | shortname | shortcode]/<identifier>/admin-members` : returns all admin members part of a project identified through iri, shortname or shortcode  

**Project Keyword Operations:**  

- `GET: /admin/projects/Keywords` : returns all unique keywords for all projects as a list  

- `GET: /admin/projects/iri/<identifier>/Keywords` : returns all keywords for a single project  

**Project Restricted View Settings Operations:**  

- `GET: /admin/projects/iri/<identifier>/RestrictedViewSettings` : returns the project's restricted view settings  

## Project Operations


### Update project information:

  - Required permission: SystemAdmin / ProjectAdmin
  - Changeable information: shortname, longname, description,
    keywords, logo, status, selfjoin. The payload must at least contain a new value for one of these properties.
  - TypeScript Docs: projectFormats - ChangeProjectApiRequestV1
  - PUT: `/admin/projects/iri/<projectIri>`
  - BODY:

```json
    {
      "shortname": "newproject",
      "longname": "project longname",
      "description": [{"value": "a new description", "language": "en"}],
      "keywords": ["a new key"],
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

### Dump project data:

Returns a [TriG](https://www.w3.org/TR/trig/) file containing the project's
ontologies, resource data, admin data, and permissions.

  - Required permission: SystemAdmin / ProjectAdmin
  - Required information: project IRI
  - `GET: /admin/projects/iri/<identifier>/AllData`

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
<http://rdfh.ch/projects/MTvoB0EJRrqovzRkWXqfkA>
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

