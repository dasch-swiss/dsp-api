<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Projects Endpoint

| Scope           | Route                                                          | Operations | Explanation                                                            |
| --------------- | -------------------------------------------------------------- | ---------- | ---------------------------------------------------------------------- |
| projects        | `/admin/projects`                                              | `GET`      | [get all projects](#get-all-projects)                                  |
| projects        | `/admin/projects`                                              | `POST`     | [create a project](#create-a-new-project)                              |
| projects        | `/admin/projects/shortname/{shortname}`                        | `GET`      | [get a single project](#get-project-by-id)                             |
| projects        | `/admin/projects/shortcode/{shortcode}`                        | `GET`      | [get a single project](#get-project-by-id)                             |
| projects        | `/admin/projects/uuid/{uuid}`                                  | `GET`      | [get a single project](#get-project-by-id)                             |
| projects        | `/admin/projects/iri/{iri}`                                    | `GET`      | [get a single project](#get-project-by-id)                             |
| projects        | `/admin/projects/iri/{iri}`                                    | `PUT`      | [update a project](#update-project-information)                        |
| projects        | `/admin/projects/iri/{iri}`                                    | `DELETE`   | [delete a project](#delete-a-project)                                  |
| projects        | `/admin/projects/iri/{iri}/AllData`                            | `GET`      | [get all data of a project](#get-all-data-of-a-project)                |
| project members | `/admin/projects/shortname/{shortname}/members`                | `GET`      | [get all project members](#get-project-members-by-id)                  |
| project members | `/admin/projects/shortcode/{shortcode}/members`                | `GET`      | [get all project members](#get-project-members-by-id)                  |
| project members | `/admin/projects/uuid/{uuid}/members`                          | `GET`      | [get all project members](#get-project-members-by-id)                  |
| project members | `/admin/projects/iri/{iri}/members`                            | `GET`      | [get all project members](#get-project-members-by-id)                  |
| project members | `/admin/projects/shortname/{shortname}/admin-members`          | `GET`      | [get all project admins](#get-project-admins-by-id)                    |
| project members | `/admin/projects/shortcode/{shortcode}/admin-members`          | `GET`      | [get all project admins](#get-project-admins-by-id)                    |
| project members | `/admin/projects/uuid/{uuid}/admin-members`                    | `GET`      | [get all project admins](#get-project-admins-by-id)                    |
| project members | `/admin/projects/iri/{iri}/admin-members`                      | `GET`      | [get all project admins](#get-project-admins-by-id)                    |
| others          | `/admin/projects/Keywords`                                     | `GET`      | [get all project keywords](#get-all-keywords)                          |
| others          | `/admin/projects/iri/{iri}/Keywords`                           | `GET`      | [get project keywords of a single project](#get-keywords-of-a-project) |
| others          | `/admin/projects/shortname/{shortname}/RestrictedViewSettings` | `GET`      | [...](#restricted-view-settings)                                       |
| others          | `/admin/projects/shortcode/{shortcode}/RestrictedViewSettings` | `GET`      | [...](#restricted-view-settings)                                       |
| others          | `/admin/projects/uuid/{uuid}/RestrictedViewSettings`           | `GET`      | [...](#restricted-view-settings)                                       |
| others          | `/admin/projects/iri/{iri}/RestrictedViewSettings`             | `GET`      | [...](#restricted-view-settings)                                       |


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

### Create a New Project

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


### Get Project by ID

The ID can be shortcode, shortname, IRI or UUID.

Permissions: No permissions required

Request definition:

- `GET /admin/projects/shortcode/{shortcode}`
- `GET /admin/projects/shortname/{shortname}`
- `GET /admin/projects/iri/{iri}`
- `GET /admin/projects/uuid/{uuid}`

Description: Returns a single project identified by shortcode, shortname, IRI or UUID.

Example request:

```bash
curl --request GET --url http://localhost:3333/admin/projects/shortcode/0001
```

```bash
curl --request GET --url http://localhost:3333/admin/projects/shortname/anything
```

```bash
curl --request GET --url http://localhost:3333/admin/projects/uuid/Lw3FC39BSzCwvmdOaTyLqQ
```

```bash
curl --request GET --url \
    http://localhost:3333/admin/projects/iri/http%3A%2F%2Frdfh.ch%2Fprojects%2FLw3FC39BSzCwvmdOaTyLqQ
```

Example response:

```jsonc
{
  "project": {
    "description": [
      {
        "value": "Anything Project"
      }
    ],
    "id": "http://rdfh.ch/projects/Lw3FC39BSzCwvmdOaTyLqQ",
    "keywords": [
      "arbitrary test data",
      "things"
    ],
    "logo": null,
    "longname": "Anything Project",
    "ontologies": [
      "http://0.0.0.0:3333/ontology/0001/something/v2",
      "http://0.0.0.0:3333/ontology/0001/sequences/v2",
      "http://0.0.0.0:3333/ontology/0001/freetest/v2",
      "http://0.0.0.0:3333/ontology/0001/minimal/v2",
      "http://0.0.0.0:3333/ontology/0001/anything/v2"
    ],
    "selfjoin": false,
    "shortcode": "0001",
    "shortname": "anything",
    "status": true
  }
}
```

Errors:

- `400 Bad Request` if the provided ID is not valid.
- `404 Not Found` if no project with the provided ID is found.

NB:

- IRI must be URL-encoded.
- UUID must be [Base64 encoded with stripped padding](../api-v2/knora-iris.md).


### Update Project Information

Permissions: SystemAdmin / ProjectAdmin

Request definition: `PUT /admin/projects/iri/{iri}`

Description: Update a project identified by its IRI.

Payload: The following properties can be changed:

- `longname`
- `description`
- `keywords`
- `logo`
- `status`
- `selfjoin`

Example request:

```bash
curl --request PUT \
  --url http://localhost:3333/admin/projects/iri/http%3A%2F%2Frdfh.ch%2Fprojects%2FLw3FC39BSzCwvmdOaTyLqQ \
  --header 'Authorization: Basic cm9vdEBleGFtcGxlLmNvbTp0ZXN0' \
  --header 'Content-Type: application/json' \
  --data '{
  "longname": "other longname"
}'
```

Example response:

```jsonc
{
  "project": {
    "description": [
      {
        "value": "Anything Project"
      }
    ],
    "id": "http://rdfh.ch/projects/Lw3FC39BSzCwvmdOaTyLqQ",
    "keywords": [
      "arbitrary test data",
      "things"
    ],
    "logo": null,
    "longname": "other longname",
    "ontologies": [
      "http://www.knora.org/ontology/0001/something",
      "http://www.knora.org/ontology/0001/sequences",
      "http://www.knora.org/ontology/0001/freetest",
      "http://www.knora.org/ontology/0001/minimal",
      "http://www.knora.org/ontology/0001/anything"
    ],
    "selfjoin": false,
    "shortcode": "0001",
    "shortname": "anything",
    "status": true
  }
}
```

Errors:

- `400 Bad Request`
  - if the provided IRI is not valid.
  - if the provided payload is not valid.
- `404 Not Found` if no project with the provided IRI is found.


### Delete a Project

Permissions: SystemAdmin / ProjectAdmin

Request definition: `DELETE /admin/projects/iri/{iri}`

Description: Mark a project as deleted (by setting the `status` flag to `false`).

```bash
curl --request DELETE \
  --url http://localhost:3333/admin/projects/iri/http%3A%2F%2Frdfh.ch%2Fprojects%2FLw3FC39BSzCwvmdOaTyLqQ \
  --header 'Authorization: Basic cm9vdEBleGFtcGxlLmNvbTp0ZXN0' \
  --header 'Content-Type: application/json'
```

Example response:

```jsonc
{
  "project": {
    "description": [
      {
        "value": "Anything Project"
      }
    ],
    "id": "http://rdfh.ch/projects/Lw3FC39BSzCwvmdOaTyLqQ",
    "keywords": [
      "arbitrary test data",
      "things"
    ],
    "logo": null,
    "longname": "other longname",
    "ontologies": [
      "http://www.knora.org/ontology/0001/something",
      "http://www.knora.org/ontology/0001/sequences",
      "http://www.knora.org/ontology/0001/freetest",
      "http://www.knora.org/ontology/0001/minimal",
      "http://www.knora.org/ontology/0001/anything"
    ],
    "selfjoin": false,
    "shortcode": "0001",
    "shortname": "anything",
    "status": false
  }
}
```

Errors:

- `400 Bad Request` if the provided IRI is not valid.
- `404 Not Found` if no project with the provided IRI is found.


### Get all Data of a Project

Permissions: ProjectAdmin / SystemAdmin

Request definition: `POST /admin/projects/iri/{iri}/AllData`

Description: Gets all data of a project as a TriG file (ontologies, resource data, admin data, and permissions).

Example request:

```bash
curl --request GET \
  --url http://localhost:3333/admin/projects/iri/http%3A%2F%2Frdfh.ch%2Fprojects%2FMTvoB0EJRrqovzRkWXqfkA/AllData \
  --header 'Authorization: Basic cm9vdEBleGFtcGxlLmNvbTp0ZXN0'
```

Example response:

```trig
@prefix images: <http://www.knora.org/ontology/00FF/images#> .
@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
@prefix knora-base: <http://www.knora.org/ontology/knora-base#> .
@prefix owl: <http://www.w3.org/2002/07/owl#> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix salsah-gui: <http://www.knora.org/ontology/salsah-gui#> .
@prefix standoff: <http://www.knora.org/ontology/standoff#> .
@prefix xml: <http://www.w3.org/XML/1998/namespace> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

<http://www.knora.org/ontology/00FF/images> {
    <http://www.knora.org/ontology/00FF/images>
            rdf:type                      owl:Ontology ;
            rdfs:label                    "The images demo ontology" ;
            knora-base:attachedToProject  <http://rdfh.ch/projects/MTvoB0EJRrqovzRkWXqfkA> ;
            knora-base:lastModificationDate  "2012-12-12T12:12:12.12Z"^^xsd:dateTime .
    images:lastname  rdf:type        owl:ObjectProperty ;
            rdfs:comment             "Nachname einer Person"@de ;
            rdfs:comment             "Last name of a person"@en ;
            rdfs:label               "Name"@de ;
            rdfs:subPropertyOf       knora-base:hasValue ;
            knora-base:objectClassConstraint  knora-base:TextValue ;
            knora-base:subjectClassConstraint  images:person ;
            salsah-gui:guiAttribute  "size=32" ;
            salsah-gui:guiAttribute  "maxlength=32" ;
            salsah-gui:guiElement    salsah-gui:SimpleText .
    # ...
}

<http://www.knora.org/data/00FF/images> {
    <http://rdfh.ch/00FF/0cb8286054d5>
            rdf:type                      images:bild ;
            rdfs:label                    "1 Alpinismus" ;
            images:bearbeiter             <http://rdfh.ch/00FF/0cb8286054d5/values/0b80b43aee0f04> ;
            images:titel                  <http://rdfh.ch/00FF/0cb8286054d5/values/cea90774ee0f04> ;
            images:urheber                <http://rdfh.ch/00FF/df1260ad43d5> ;
            images:urheberValue           <http://rdfh.ch/00FF/0cb8286054d5/values/e346ff38-6b03-4a27-a11b-b0818a2e5ee3> ;
            knora-base:attachedToProject  <http://rdfh.ch/projects/MTvoB0EJRrqovzRkWXqfkA> ;
            knora-base:attachedToUser     <http://rdfh.ch/users/c266a56709> ;
            knora-base:creationDate       "2016-03-02T15:05:57Z"^^xsd:dateTime ;
            knora-base:hasPermissions     "CR knora-admin:ProjectMember,knora-admin:Creator|V knora-admin:KnownUser|RV knora-admin:UnknownUser" ;
            knora-base:hasStillImageFileValue  <http://rdfh.ch/00FF/0cb8286054d5/values/c66133bf942f01> ;
            knora-base:isDeleted          false .
    # ...
}

<http://www.knora.org/data/admin> {
  # ...
}

<http://www.knora.org/data/permissions> {
  # ...
}
```

---

<!-- TODO: not reworked from here on, only placeholders -->

## Project Member Operations

### Get Project Members by ID

Permissions:

Request definition: `GET /admin/projects/iri/...`

Description: 

Example request:

```bash

```

Example response:

```jsonc

```

Errors:

### Get Project Admins by ID

Permissions:

Request definition: `GET /admin/projects/iri/...`

Description: 

Example request:

```bash

```

Example response:

```jsonc

```

Errors:

## Other Project Operations

### Get all Keywords

Permissions:

Request definition: `GET /admin/projects/iri/...`

Description: 

Example request:

```bash

```

Example response:

```jsonc

```

Errors:

### Get Keywords of a Project

Permissions:

Request definition: `GET /admin/projects/iri/...`

Description: 

Example request:

```bash

```

Example response:

```jsonc

```

Errors:

### Restricted View Settings

Permissions:

Request definition: `GET /admin/projects/iri/...`

Description: 

Example request:

```bash

```

Example response:

```jsonc

```

Errors:

---

<!-- TODO: Old stuff -->


**Project Member Operations:**  

- `GET: /admin/projects/[iri | shortname | shortcode | uuid]/<identifier>/members` : returns all members part of a project identified through iri, shortname, shortcode or UUID

**Project Admin Member Operations:**  

- `GET: /admin/projects/[iri | shortname | shortcode]/<identifier>/admin-members` : returns all admin members part of a project identified through iri, shortname or shortcode  

**Project Keyword Operations:**  

- `GET: /admin/projects/Keywords` : returns all unique keywords for all projects as a list  

- `GET: /admin/projects/iri/<identifier>/Keywords` : returns all keywords for a single project  

**Project Restricted View Settings Operations:**  

- `GET: /admin/projects/iri/<identifier>/RestrictedViewSettings` : returns the project's restricted view settings  

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


