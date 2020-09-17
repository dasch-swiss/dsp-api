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

# Permissions Endpoint

##Permission Operations:

**Note:** For the following operations, the requesting user must be either a `systemAdmin`or 
a `projectAdmin`.

### Getting Permissions: 
- `GET: /admin/permissions/<projectIri>` : return all permissions for a project.
As a response, the IRI and the type of all `permissions` of a project are returned.
  
- `GET: /admin/permissions/ap/<projectIri>`: return all administrative permissions 
for a project. As a response, all `administrative_permissions` of a project are returned.

- `GET: /admin/permissions/ap/<projectIri>/<groupIri>`: return the administrative 
permissions for a project group. As a response, the `administrative_permission` defined 
for the group is returned. 

- `GET: /admin/permissions/doap/<projectIri>`: return all default object access 
permissions for a project. As a response, all `default_object_acces_permissions` of a 
project are returned. 

### Creating New Permissions:
 
- `POST: /admin/permissions/ap`: create a new administrative permission. The type of 
permissions, the project and group to which the permission should be added must be 
included in the request body, for example:

```json
{
    "forGroup":"http://rdfh.ch/groups/0001/thing-searcher", 
    "forProject":"http://rdfh.ch/projects/0001", 
    "hasPermissions":[{"additionalInformation":null,"name":"ProjectAdminGroupAllPermission","permissionCode":null}]
}
``` 

In addition, in the body of the request, it is possible to specify a custom IRI (of [Knora IRI](knora-iris.md#iris-for-data) form) for a permission through
the `@id` attribute which will then be assigned to the permission; otherwise the permission will get a unique random IRI.
A custom permission IRI must be `http://rdfh.ch/permissions/PROJECT_SHORTCODE/` (where `PROJECT_SHORTCODE`
is the shortcode of the project that the permission belongs to), plus a custom ID string. For example:
```
"id": "http://rdfh.ch/permissions/0001/AP-with-customIri",
```

As a response, the created administrative permission and its IRI are returned as below:

```json
{
    "administrative_permission": {
        "forGroup": "http://rdfh.ch/groups/0001/thing-searcher",
        "forProject": "http://rdfh.ch/projects/0001",
        "hasPermissions": [
            {
                "additionalInformation": null,
                "name": "ProjectAdminGroupAllPermission",
                "permissionCode": null
            }
        ],
        "iri": "http://rdfh.ch/permissions/0001/mFlyBEiMQtGzwy_hK0M-Ow"
    }
}
```

- `POST: /admin/permissions/doap` : create a new default object access permission. 
A single instance of `knora-admin:DefaultObjectAccessPermission` must
always reference a project, but can only reference **either** a group
(`knora-admin:forGroup` property), a resource class
(`knora-admin:forResourceClass`), a property (`knora-admin:forProperty`),
or a combination of resource class **and** property. For example, to create a new 
default object access permission for a group of a project the request body would be
 
```
{
    "forGroup":"http://rdfh.ch/groups/0001/thing-searcher",
    "forProject":"http://rdfh.ch/projects/0001",
    "forProperty":null,
    "forResourceClass":null,
    "hasPermissions":[{"additionalInformation":"http://www.knora.org/ontology/knora-admin#ProjectMember","name":"D","permissionCode":7}]
}
```

Similar to the previous case a custom IRI can be assigned to a permission through 
the `@id` attribute. The example below shows the request body to create a new default 
object access permission with a custom IRI defined for a resource class 
of a specific project:

```json
{
    "id": "http://rdfh.ch/permissions/00FF/DOAP-with-customIri",
    "forGroup":null,
    "forProject":"http://rdfh.ch/projects/00FF",
    "forProperty":null,
    "forResourceClass":"http://www.knora.org/ontology/00FF/images#bild",
    "hasPermissions":[{"additionalInformation":"http://www.knora.org/ontology/knora-admin#ProjectMember","name":"D","permissionCode":7}]
}
```

The response contains the newly created permission and its IRI, as:

```json
{
    "default_object_access_permission": {
        "forGroup": null,
        "forProject": "http://rdfh.ch/projects/00FF",
        "forProperty": null,
        "forResourceClass": "http://www.knora.org/ontology/00FF/images#bild",
        "hasPermissions": [
            {
                "additionalInformation": "http://www.knora.org/ontology/knora-admin#ProjectMember",
                "name": "D",
                "permissionCode": 7
            }
        ],
        "iri": "http://rdfh.ch/permissions/00FF/DOAP-with-customIri"
    }
}
```

