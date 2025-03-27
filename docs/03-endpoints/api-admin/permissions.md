# Permissions Endpoint

For an extensive explanation on how DSP permissions are implemented, see
[here](../../05-internals/design/api-admin/administration.md#permissions).

| Route                                                       | Operations | Explanation                                                                                                                                                                               |
| ----------------------------------------------------------- | ---------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `/admin/permissions/{projectIri}`                           | `GET`      | [get all permissions of a project](#getting-permissions)                                                                                                                                  |
| `/admin/permissions/ap/{projectIri}`                        | `GET`      | [get all administrative permissions of a project](#getting-permissions)                                                                                                                   |
| `/admin/permissions/ap/{projectIri}/{groupIri}`             | `GET`      | [get all administrative permissions of a group](#getting-permissions)                                                                                                                     |
| `/admin/permissions/doap/{projectIri}`                      | `GET`      | [get all default object access permissions of a project](#getting-permissions)                                                                                                            |
| `/admin/permissions/ap`                                     | `POST`     | [create a new administrative permission](#creating-new-administrative-permissions)                                                                                                        |
| `/admin/permissions/doap`                                   | `POST`     | [create a new default object access permission](#creating-new-default-object-access-permissions)                                                                                          |
| `/admin/permissions/doap/{permissionIri}`                   | `PUT`      | [update an existing default object access permission](#updating-an-existing-default-object-access-permission)                                                                             |
| `/admin/permissions/{permissionIri}/group`                  | `PUT`      | [update for which group an administrative or default object access permission is used](#updating-a-permissions-group)                                                                     |
| `/admin/permissions/{permissionIri}/hasPermission`          | `PUT`      | [update the scope of an administrative or default object access permission](#updating-a-permissions-scope), i.e. what permissions are granted to which group when this permission applies |
| `/admin/permissions/{permissionIri}`                        | `DELETE`   | [delete an administrative or default object access permission](#deleting-a-permission)                                                                                                    |
| ~~`/admin/permissions/{doap_permissionIri}/resourceClass`~~ | `PUT`      | **deprecated**, use `/admin/permissions/doap/{permissionIri}` instead                                                                                                                     |
| ~~`/admin/permissions/{doap_permissionIri}/property`~~      | `PUT`      | **deprecated**,  use `/admin/permissions/doap/{permissionIri}` instead                                                                                                                    |

## Permission Operations

**Note:** For the following operations, the requesting user must be either a `systemAdmin`or a `projectAdmin`.

### Getting Permissions

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

### Creating New Administrative Permissions
 
- `POST: /admin/permissions/ap`: create a new administrative permission. The type of 
permissions, the project and group to which the permission should be added must be 
included in the request body, for example:

```json
{
    "forGroup":"http://rdfh.ch/groups/0001/thing-searcher", 
    "forProject":"http://rdfh.ch/projects/0001", 
    "hasPermissions":[
      {
        "additionalInformation":null,
        "name":"ProjectAdminGroupAllPermission",
        "permissionCode":null
      }
    ]
}
``` 

In addition, in the body of the request, it is possible to specify a custom IRI (of 
[DSP IRI](../api-v2/knora-iris.md#iris-for-data) form) for a permission through
the `@id` attribute which will then be assigned to the permission; otherwise the permission will get a unique random IRI.
A custom permission IRI must be `http://rdfh.ch/permissions/PROJECT_SHORTCODE/` (where `PROJECT_SHORTCODE`
is the shortcode of the project that the permission belongs to), plus a custom ID string. For example:

```json
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

`hasPermissions` contains permission types that must be granted. See [the complete description of administrative
permission types](../../05-internals/design/api-admin/administration.md#administrative-permissions).
In summary, each permission should contain followings:

- `additionalInformation`: should be left empty, otherwise will be ignored.
- `name` : indicates the type of the permission that can be one of the followings:
    - `ProjectAdminAllPermission`: gives the user the permission to do anything
     on project level, i.e. create new groups, modify all
     existing groups
    - `ProjectAdminGroupAllPermission`: gives the user the permission to modify
     *group info* and *group membership* on *all* groups
     belonging to the project.
    - `ProjectAdminGroupRestrictedPermission`: gives the user the permission to modify
     *group info* and *group membership* on *certain* groups
     belonging to the project.
    - `ProjectAdminRightsAllPermission`: gives the user the permission to change the
     *permissions* on all objects belonging to the project
     (e.g., default permissions attached to groups and
     permissions on objects).
    - `ProjectResourceCreateAllPermission`: gives the permission to create resources
     inside the project.
    - `ProjectResourceCreateRestrictedPermission`: gives restricted resource creation permission
     inside the project.
- `permissionCode`: should be left empty, otherwise will be ignored.


Note that during the creation of a new project, 
a default set of administrative permissions are added to its ProjectAdmin and ProjectMember groups 
(See [Default set of permissions for a new project](./projects.md#default-set-of-permissions-for-a-new-project)). 
Therefore, it is not possible to create new administrative permissions 
for the ProjectAdmin and ProjectMember groups of a project. 
However, the default permissions set for these groups can be modified 
(See [update permission](./permissions.md#updating-a-permissions-scope)).


### Creating New Default Object Access Permissions

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
    "hasPermissions":[
      {
        "additionalInformation":"http://www.knora.org/ontology/knora-admin#ProjectMember",
        "name":"D",
        "permissionCode":7
      }
    ]
}
```

`hasPermissions` contains permission types that must be granted. See [a complete description of object access 
permission types](../../05-internals/design/api-admin/administration.md#default-object-access-permissions). 
In summary, each permission should contain followings:

- `additionalInformation`: To whom the permission should be granted: project members, known users, unknown users, etc.
- `name` : indicates the type of the permission that can be one of the followings.
    - `RV`: restricted view permission (least privileged)
    - `V`: view permission
    - `M` modify permission
    - `D`: delete permission
    - `CR`: change rights permission (most privileged)
- `permissionCode`: The code assigned to a permission indicating its hierarchical level. These codes are as below:
    - `1`: for restricted view permission (least privileged)
    - `2`: for view permission
    - `6`: for modify permission
    - `7`: for delete permission
    - `8`: for change rights permission (most privileged)
    
Note that, at least either `name` or `permissionCode` must be provided. If one is missing, it will be extrapolated from the other.
For example, if `permissionCode= 1` is given but `name` was left empty, its value will be set to `name = RV`.    

Similar to the previous case a custom IRI can be assigned to a permission specified by the `id` in the request body. 
The example below shows the request body to create a new default object access permission with a custom IRI defined for 
a resource class of a specific project:

```json
{
    "id": "http://rdfh.ch/permissions/00FF/fSw7w1sI5IwDjEfFi1jOeQ",
    "forGroup": null,
    "forProject": "http://rdfh.ch/projects/00FF",
    "forProperty": null,
    "forResourceClass": "http://api.dasch.swiss/ontology/00FF/images/v2#bild",
    "hasPermissions": [
      {
        "additionalInformation": "http://www.knora.org/ontology/knora-admin#ProjectMember",
        "name": "D",
        "permissionCode": 7
      }
    ]
}
```

The response contains the newly created permission and its IRI, as:

```json
{
    "default_object_access_permission": {
        "forGroup": null,
        "forProject": "http://rdfh.ch/projects/00FF",
        "forProperty": null,
        "forResourceClass": "http://api.dasch.swiss/ontology/00FF/images/v2#bild",
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

Note that during the creation of a new project, 
a set of default object access permissions are created for its ProjectAdmin and ProjectMember groups 
(See [Default set of permissions for a new project](./projects.md#default-set-of-permissions-for-a-new-project)). 
Therefore, it is not possible to create new default object access permissions 
for the ProjectAdmin and ProjectMember groups of a project. 
However, the default permissions set for these groups can be modified; see below for more information.

### Updating an existing Default Object Access Permission

- `PUT: /admin/permissions/doap/<doap_permissionIri>` to change the attributes of an existing default object
  access permission, identified by its IRI `<doap_permissionIri>`.

This is an example of a request body to update an existing default object access permission:

```json
{
  "forProperty" : "http://api.dasch.swiss/ontology/00FF/images/v2#hasTitle",
  "forResourceClass": "http://api.dasch.swiss/ontology/0803/incunabula/v2#Book",
  "forGroup": null, 
  "hasPermissions": [
    {
      "additionalInformation": "http://www.knora.org/ontology/knora-admin#ProjectMember",
      "name": "D",
      "permissionCode": 7
    }
  ]
}
```

All attributes of the default object access permission are optional and may be combined.

!!! warning
    Only certain combinations of attributes are allowed. Only exactly one of the following combinations is allowed:

      - `forGroup`  <!-- markdownlint-disable MD046 -->
      - `forResourceClass`
      - `forProperty`
      - `forResourceClass` and `forProperty`

If the combination of attributes is not allowed, the request will fail with a `400 Bad Request` error.
Any valid combination of attributes will replace the existing values.

If present, the `hasPermissions` attribute must contain all permission types that must be granted. See [a complete description of object access
permission types](../../05-internals/design/api-admin/administration.md#default-object-access-permissions).
This is also described in the [Creating New Default Object Access Permissions](#creating-new-default-object-access-permissions) section.

The response is the updated default object access permission with its new attributes and is the same as when
[creating a new default object access permission](#creating-new-default-object-access-permissions).

### Updating a Permission's Group

!!! note
    For Default Object Access Permissions this endpoint is deprecated, 
    use [`PUT: /admin/permissions/doap/<permissionIri>`](#updating-an-existing-default-object-access-permission) instead.  
    For more information, see [here](../../../10-migration-guides#updating-doaps).

- `PUT: /admin/permissions/<permissionIri>/group` to change the group for which an administrative or a default object 
access permission, identified by its IRI `<permissionIri>`, is defined. The request body must contain the IRI of the new 
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

### Updating a Permission's Scope

!!! note
    For Default Object Access Permissions this endpoint is deprecated, 
    use [`PUT: /admin/permissions/doap/<permissionIri>`](#updating-an-existing-default-object-access-permission) instead.  
    For more information, see [here](../../../10-migration-guides#updating-doaps).

- `PUT: /admin/permissions/<permissionIri>/hasPermissions` to change the scope of permissions assigned to an administrative
  or a default object access permission identified by it IRI, `<permissionIri>`. The request body must contain the new set 
  of permission types as below:

```json
{
   "hasPermissions":[
     {
       "additionalInformation":"http://www.knora.org/ontology/knora-admin#ProjectMember",
       "name":"D",
       "permissionCode":7
     }
   ]
}
```

Each permission item given in `hasPermissions`, must contain the necessary parameters with respect to the type of the 
permission. For example, if you wish to change the scope of an administrative permission, follow the 
[guidelines](#creating-new-administrative-permissions) for the
content of its `hasPermissions` property. Similarly, if you wish to change the scope of a default object access permission, 
follow the [guidelines](#creating-new-default-object-access-permissions) given about the content of its `hasPermissions` property.  
Either the `name` or the `permissionCode` must be present; it is not necessary to provide both.

The previous permission set is *replaced* by the new permission set. In order to remove a permission for a group
entirely, you can provide a new set of permissions, leaving out the permission specification for the group.

### Deleting a Permission

- `DELETE: /admin/permissions/<permissionIri>` to delete an administrative, or a default object access permission. The 
IRI of the permission must be given in encoded form. 

