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

