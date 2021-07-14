<!---
Copyright © 2015-2021 the contributors (see Contributors.md).

This file is part of DSP — DaSCH Service Platform.

DSP is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

DSP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public
License along with DSP. If not, see <http://www.gnu.org/licenses/>.
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

### Creating New Administrative Permissions:
 
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

In addition, in the body of the request, it is possible to specify a custom IRI (of [Knora IRI](../api-v2/knora-iris.md#iris-for-data) form) for a permission through
the `@id` attribute which will then be assigned to the permission; otherwise the permission will get a unique random IRI.
A custom permission IRI must be `http://rdfh.ch/permissions/PROJECT_SHORTCODE/` (where `PROJECT_SHORTCODE`
is the shortcode of the project that the permission belongs to), plus a custom ID string. For example:
```
"id": "http://rdfh.ch/permissions/0001/jKIYuaEUETBcyxpenUwRzQ",
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
Note that during the creation of a new project, a default set of administrative permissions are added to its ProjectAdmin and 
ProjectMember groups (See [Default set of permissions for a new project](./projects.md#default-set-of-permissions-for-a-new-project)). 
Therefore, it is not possible to create new administrative permissions for the ProjectAdmin and ProjectMember groups of 
a project. However, the default permissions set for these groups can be modified (See [update permission](./permissions.md#updating-a-permissions-scope)).


### Creating New Default Object Access Permissions:

- `POST: /admin/permissions/doap` : create a new default object access permission. 
A single instance of `knora-admin:DefaultObjectAccessPermission` must
always reference a project, but can only reference **either** a group
(`knora-admin:forGroup` property), a resource class
(`knora-admin:forResourceClass`), a property (`knora-admin:forProperty`),
or a combination of resource class **and** property. For example, to create a new 
default object access permission for a group of a project the request body would be
 
```json
{
    "forGroup":"http://rdfh.ch/groups/0001/thing-searcher",
    "forProject":"http://rdfh.ch/projects/0001",
    "forProperty":null,
    "forResourceClass":null,
    "hasPermissions":[{"additionalInformation":"http://www.knora.org/ontology/knora-admin#ProjectMember","name":"D","permissionCode":7}]
}
```
`hasPermissions` contains permission types that must be granted. Each permission type should contain the followings:
- `additionalInformation` : To whom the permission should be granted: project members, known users, unknown users, etc.
- `name` : indicates the type of the type of the permission. 


Similar to the previous case a custom IRI can be assigned to a permission specified by the `id` in the request body. 
The example below shows the request body to create a new default object access permission with a custom IRI defined for 
a resource class of a specific project:

```json
{
    "id": "http://rdfh.ch/permissions/00FF/fSw7w1sI5IwDjEfFi1jOeQ",
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
        "iri": "http://rdfh.ch/permissions/00FF/fSw7w1sI5IwDjEfFi1jOeQ"
    }
}
```
Note that during the creation of a new project, a set of default object access permissions are created for its 
ProjectAdmin and ProjectMember groups (See [Default set of permissions for a new project](./projects.md#default-set-of-permissions-for-a-new-project)). 
Therefore, it is not possible to create new default object access permissions for the ProjectAdmin and ProjectMember 
groups of a project. However, the default permissions set for these groups can be modified; see below for more information.

### Updating a Permission's Group:
- `PUT: /admin/permissions/<permissionIri>/group` to change the group for which an administrative or a default object 
access permission, identified by it IRI `<permissionIri>`, is defined. The request body must contain the IRI of the new 
group as below:
 ```json
{
    "forGroup": "http://www.knora.org/ontology/knora-admin#ProjectMember"
}
```
When updating an administrative permission, its previous `forGroup` value will be replaced with the new one.
When updating a default object access permission, if it originally had a `forGroup` value defined, it will be replaced 
with the new group. Otherwise, if the default object access permission was defined for a resource class or a property or 
the combination of both, the permission will be defined for the newly specified group and its previous 
`forResourceClass` and `forProperty` values will be deleted.

### Updating a Permission's Scope:
- `PUT: /admin/permissions/<permissionIri>/hasPermissions` to change the scope of permissions assigned to an administrative
 or a default object access permission identified by it IRI, `<permissionIri>`. The request body must contain the new set 
 of permission types as below:
 ```json
{
   "hasPermissions":[{"additionalInformation":"http://www.knora.org/ontology/knora-admin#ProjectMember","name":"D","permissionCode":7}]
}
```

### Updating a Default Object Access Permission's Resource Class:
- `PUT: /admin/permissions/<doap_permissionIri>/resourceClass` to change the resource class for which a default object 
access permission, identified by it IRI `<doap_permissionIri>`, is defined. This operation is only valid for 
updating a default object acceess permission. The IRI of the new resource class must be given in the request body as:
 ```json
{
    "forResourceClass": "http://www.knora.org/ontology/0803/incunabula#book"
}
```
Note that if the default object access permission was originally defined for a group, with this operation, the permission 
will be defined for the given resource class instead of the group. That means the value of the `forGroup` will 
be deleted.

### Updating a Default Object Access Permission's Property:
- `PUT: /admin/permissions/<doap_permissionIri>/property` to change the property for which a default object 
access permission, identified by it IRI `<doap_permissionIri>`, is defined. This operation is only valid for 
updating a default object acceess permission. The IRI of the new property must be given in the request body as:
 ```json
{
   "forProperty":"http://www.knora.org/ontology/00FF/images#titel"
}
```
Note that if the default object access permission was originally defined for a group, with this operation, the permission 
will be defined for the given property instead of the group. That means the value of the `forGroup` will 
be deleted.

### Deleting a permission:
- `DELETE: /admin/permissions/<permissionIri>` to delete an administrative, or a default object access permission. The 
IRI of the permission must be given in encoded form. 

