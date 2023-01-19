<!---
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Projects Endpoint

| Scope           | Route                                                          | Operations | Explanation                                                             |
| --------------- | -------------------------------------------------------------- | ---------- | ----------------------------------------------------------------------- |
| projects        | `/admin/projects`                                              | `GET`      | [get all projects](#get-all-projects)                                   |
| projects        | `/admin/projects`                                              | `POST`     | [create a project](#create-a-new-project)                               |
| projects        | `/admin/projects/shortname/{shortname}`                        | `GET`      | [get a single project](#get-project-by-id)                              |
| projects        | `/admin/projects/shortcode/{shortcode}`                        | `GET`      | [get a single project](#get-project-by-id)                              |
| projects        | `/admin/projects/iri/{iri}`                                    | `GET`      | [get a single project](#get-project-by-id)                              |
| projects        | `/admin/projects/iri/{iri}`                                    | `PUT`      | [update a project](#update-project-information)                         |
| projects        | `/admin/projects/iri/{iri}`                                    | `DELETE`   | [delete a project](#delete-a-project)                                   |
| projects        | `/admin/projects/iri/{iri}/AllData`                            | `GET`      | [get all data of a project](#get-all-data-of-a-project)                 |
| project members | `/admin/projects/shortname/{shortname}/members`                | `GET`      | [get all project members](#get-project-members-by-id)                   |
| project members | `/admin/projects/shortcode/{shortcode}/members`                | `GET`      | [get all project members](#get-project-members-by-id)                   |
| project members | `/admin/projects/iri/{iri}/members`                            | `GET`      | [get all project members](#get-project-members-by-id)                   |
| project members | `/admin/projects/shortname/{shortname}/admin-members`          | `GET`      | [get all project admins](#get-project-admins-by-id)                     |
| project members | `/admin/projects/shortcode/{shortcode}/admin-members`          | `GET`      | [get all project admins](#get-project-admins-by-id)                     |
| project members | `/admin/projects/iri/{iri}/admin-members`                      | `GET`      | [get all project admins](#get-project-admins-by-id)                     |
| keywords        | `/admin/projects/Keywords`                                     | `GET`      | [get all project keywords](#get-all-keywords)                           |
| keywords        | `/admin/projects/iri/{iri}/Keywords`                           | `GET`      | [get project keywords of a single project](#get-keywords-of-a-project)  |
| view settings   | `/admin/projects/shortname/{shortname}/RestrictedViewSettings` | `GET`      | [get restricted view settings for a project](#restricted-view-settings) |
| view settings   | `/admin/projects/shortcode/{shortcode}/RestrictedViewSettings` | `GET`      | [get restricted view settings for a project](#restricted-view-settings) |
| view settings   | `/admin/projects/iri/{iri}/RestrictedViewSettings`             | `GET`      | [get restricted view settings for a project](#restricted-view-settings) |


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
      "id": "http://rdfh.ch/projects/00FF",
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
        "id": "http://rdfh.ch/projects/3333",
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

The ID can be shortcode, shortname or IRI.

Permissions: No permissions required

Request definition:

- `GET /admin/projects/shortcode/{shortcode}`
- `GET /admin/projects/shortname/{shortname}`
- `GET /admin/projects/iri/{iri}`

Description: Returns a single project identified by shortcode, shortname or IRI.

Example request:

```bash
curl --request GET --url http://localhost:3333/admin/projects/shortcode/0001
```

```bash
curl --request GET --url http://localhost:3333/admin/projects/shortname/anything
```

```bash
curl --request GET --url \
    http://localhost:3333/admin/projects/iri/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001
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
    "id": "http://rdfh.ch/projects/0001",
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
  --url http://localhost:3333/admin/projects/iri/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001 \
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
    "id": "http://rdfh.ch/projects/0001",
    "keywords": [
      "arbitrary test data",
      "things"
    ],
    "logo": null,
    "longname": "other longname",
    "ontologies": [
      "http://api.knora.org/ontology/0001/something/v2",
      "http://api.knora.org/ontology/0001/sequences/v2",
      "http://api.knora.org/ontology/0001/freetest/v2",
      "http://api.knora.org/ontology/0001/minimal/v2",
      "http://api.knora.org/ontology/0001/anything/v2"
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
  --url http://localhost:3333/admin/projects/iri/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001 \
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
    "id": "http://rdfh.ch/projects/0001",
    "keywords": [
      "arbitrary test data",
      "things"
    ],
    "logo": null,
    "longname": "other longname",
    "ontologies": [
      "http://api.knora.org/ontology/0001/something/v2",
      "http://api.knora.org/ontology/0001/sequences/v2",
      "http://api.knora.org/ontology/0001/freetest/v2",
      "http://api.knora.org/ontology/0001/minimal/v2",
      "http://api.knora.org/ontology/0001/anything/v2"
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
  --url http://localhost:3333/admin/projects/iri/http%3A%2F%2Frdfh.ch%2Fprojects%2F00FF/AllData \
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
            knora-base:attachedToProject  <http://rdfh.ch/projects/00FF> ;
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
            knora-base:attachedToProject  <http://rdfh.ch/projects/00FF> ;
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

## Project Member Operations

### Get Project Members by ID

Permissions: SystemAdmin / ProjectAdmin

Request definition: 

- `GET /admin/projects/shortcode/{shortcode}/members`
- `GET /admin/projects/shortname/{shortname}/members`
- `GET /admin/projects/iri/{iri}/members`

Description: returns all members part of a project identified through iri, shortname or shortcode

Example request:

```bash
curl --request GET 'http://0.0.0.0:3333/admin/projects/shortcode/0001/members' \
--header 'Authorization: Basic cm9vdEBleGFtcGxlLmNvbTp0ZXN0'
```

```bash
curl --request GET 'http://0.0.0.0:3333/admin/projects/shortname/anything/members' \
--header 'Authorization: Basic cm9vdEBleGFtcGxlLmNvbTp0ZXN0'
```

```bash
curl --request GET 'http://0.0.0.0:3333/admin/projects/iri/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001/members'
--header 'Authorization: Basic cm9vdEBleGFtcGxlLmNvbTp0ZXN0'
```

Example response:

```jsonc
{
    "members": [
        {
            "email": "anything.user01@example.org",
            "familyName": "UserFamilyName",
            "givenName": "UserGivenName",
            "groups": [],
            "id": "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ",
            "lang": "de",
            "password": null,
            "permissions": {
                "administrativePermissionsPerProject": {
                    "http://rdfh.ch/projects/0001": [
                        {
                            "additionalInformation": null,
                            "name": "ProjectResourceCreateAllPermission",
                            "permissionCode": null
                        }
                    ]
                },
                "groupsPerProject": {
                    "http://rdfh.ch/projects/0001": [
                        "http://www.knora.org/ontology/knora-admin#ProjectMember"
                    ]
                }
            },
            "projects": [
                {
                    "description": [
                        {
                            "value": "Anything Project"
                        }
                    ],
                    "id": "http://rdfh.ch/projects/0001",
                    "keywords": [
                        "arbitrary test data",
                        "things"
                    ],
                    "logo": null,
                    "longname": "Anything Project",
                    "ontologies": [
                        "http://0.0.0.0:3333/ontology/0001/something/v2",
                        "http://0.0.0.0:3333/ontology/0001/anything/v2"
                    ],
                    "selfjoin": false,
                    "shortcode": "0001",
                    "shortname": "anything",
                    "status": true
                }
            ],
            "sessionId": null,
            "status": true,
            "token": null,
            "username": "anything.user01"
        }
    ]
}
```

Errors:

- `400 Bad Request` if the provided ID is not valid.
- `404 Not Found` if no project with the provided ID is found.

NB:

- IRI must be URL-encoded.

### Get Project Admins by ID

Permissions: SystemAdmin / ProjectAdmin

Request definition: 
- `GET /admin/projects/shortcode/{shortcode}/admin-members`
- `GET /admin/projects/shortname/{shortname}/admin-members`
- `GET /admin/projects/iri/{iri}/admin-members`

Description: returns all admin members part of a project identified through iri, shortname or shortcode

Example request:

```bash
curl --request GET 'http://0.0.0.0:3333/admin/projects/shortcode/0001/admin-members' \
--header 'Authorization: Basic cm9vdEBleGFtcGxlLmNvbTp0ZXN0'
```

```bash
curl --request GET 'http://0.0.0.0:3333/admin/projects/shortname/anything/admin-members' \
--header 'Authorization: Basic cm9vdEBleGFtcGxlLmNvbTp0ZXN0'
```

```bash
curl --request GET 'http://0.0.0.0:3333/admin/projects/iri/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001/admin-members'
--header 'Authorization: Basic cm9vdEBleGFtcGxlLmNvbTp0ZXN0'
```

Example response:

```jsonc
{
    "members": [
        {
            "email": "anything.admin@example.org",
            "familyName": "Admin",
            "givenName": "Anything",
            "groups": [],
            "id": "http://rdfh.ch/users/AnythingAdminUser",
            "lang": "de",
            "password": null,
            "permissions": {
                "administrativePermissionsPerProject": {
                    "http://rdfh.ch/projects/0001": [
                        {
                            "additionalInformation": null,
                            "name": "ProjectResourceCreateAllPermission",
                            "permissionCode": null
                        },
                        {
                            "additionalInformation": null,
                            "name": "ProjectAdminAllPermission",
                            "permissionCode": null
                        }
                    ]
                },
                "groupsPerProject": {
                    "http://rdfh.ch/projects/0001": [
                        "http://www.knora.org/ontology/knora-admin#ProjectMember",
                        "http://www.knora.org/ontology/knora-admin#ProjectAdmin"
                    ]
                }
            },
            "projects": [
                {
                    "description": [
                        {
                            "value": "Anything Project"
                        }
                    ],
                    "id": "http://rdfh.ch/projects/0001",
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
            ],
            "sessionId": null,
            "status": true,
            "token": null,
            "username": "anything.admin"
        }
    ]
}

```

Errors:

- `400 Bad Request` if the provided ID is not valid.
- `404 Not Found` if no project with the provided ID is found.

NB:

- IRI must be URL-encoded.

## Other Project Operations

### Get all Keywords

Permissions:

Request definition: `GET /admin/projects/Keywords`

Description: returns keywords of all projects as a list

Example request:

```bash
curl --request GET 'http://0.0.0.0:3333/admin/projects/Keywords'
```

Example response:

```jsonc
{
    "keywords": [
        "Annotation",
        "Arabe",
        "Arabic",
        "Arabisch",
        "Audio",
        "Basel",
        "Basler Frühdrucke",
        "Bilder",
        "Bilderfolgen",
        "Contectualisation of images",
        "Cyrillic",
        "Cyrillique",
        "Data and Service Center for the Humanities (DaSCH)",
        "Grec",
        "Greek",
        "Griechisch",
        "Hebrew",
        "Hebräisch",
        "Hieroglyphen",
        "Hébreu",
        "Inkunabel",
        "Japanese",
        "Japanisch",
        "Japonais",
        "Keilschrift",
        "Kunsthistorisches Seminar Universität Basel",
        "Kyrillisch",
        "Late Middle Ages",
        "Letterpress Printing",
        "Markup",
        "Narrenschiff",
        "Objekte",
        "Sebastian Brant",
        "Sonderzeichen",
        "Texteigenschaften",
        "Textquellen",
        "Wiegendrucke",
        "XML",
        "arbitrary test data",
        "asdf",
        "audio",
        "caractères spéciaux",
        "collection",
        "cuneiform",
        "cunéiforme",
        "early print",
        "hieroglyphs",
        "hiéroglyphes",
        "images",
        "incunabula",
        "objects",
        "objets",
        "propriétés de texte",
        "ship of fools",
        "sources",
        "special characters",
        "textual properties",
        "textual sources",
        "things"
    ]
}
```


### Get Keywords of a Project

Permissions:

Request definition:
- `GET /admin/projects/iri/{iri}/Keywords`

Description: returns the keywords of a single project

Example request:

```bash
curl --request GET 'http://0.0.0.0:3333/admin/projects/iri/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001/Keywords'
--header 'Authorization: Basic cm9vdEBleGFtcGxlLmNvbTp0ZXN0'
```

Example response:

```jsonc
{
    "keywords": [
        "arbitrary test data",
        "things"
    ]
}

```

### Restricted View Settings

Permissions: ProjectAdmin

Request definition:
- `GET /admin/projects/shortcode/{shortcode}/RestrictedViewSettings`
- `GET /admin/projects/shortname/{shortname}/RestrictedViewSettings`
- `GET /admin/projects/iri/{iri}/RestrictedViewSettings`

Description: returns the project's restricted view settings

Example request:

```bash
curl --request GET 'http://0.0.0.0:3333/admin/projects/shortcode/0001/RestrictedViewSettings' \
--header 'Authorization: Basic cm9vdEBleGFtcGxlLmNvbTp0ZXN0'
```

```bash
curl --request GET 'http://0.0.0.0:3333/admin/projects/shortname/anything/RestrictedViewSettings' \
--header 'Authorization: Basic cm9vdEBleGFtcGxlLmNvbTp0ZXN0'
```

```bash
curl --request GET 'http://0.0.0.0:3333/admin/projects/iri/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001/RestrictedViewSettings'
--header 'Authorization: Basic cm9vdEBleGFtcGxlLmNvbTp0ZXN0'
```

Example response:

```jsonc
{
    "settings": {
        "size": "!512,512",
        "watermark": "path_to_image"
    }
}
```

Operates on the following properties:
 - `knora-admin:projectRestrictedViewSize`: the IIIF size value
 - `knora-admin:projectRestrictedViewWatermark`: the path to the watermark image. **Currently not used!**

