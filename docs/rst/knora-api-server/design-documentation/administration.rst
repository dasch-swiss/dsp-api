.. Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
   Tobias Schweizer, André Kilchenmann, and André Fatton.

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


Administration (Users, Projects, Groups, Institutions)
=======================================================

Scope
------

This Section includes management (creation, updating, deletion) of *Users*, *Projects*, *Groups*, and *Institutions*.

Implementation
---------------
All administration functions will be implemented as part of the Knora API in the ``webapi`` codebase. There will also be
a separate web-application as part of the ``salsah`` codebase.


Definitions
------------



Overview
---------

During the initial deployment of a Knora server, the main administration user (*root*) is created. This *root* user has
the right to do anything.

Knora’s concept of access control is that permissions can only be granted to groups (or the whole project, i.e. all
members of a project) and not to individual users. There are two distinct ways of granting permission. Firstly, an
object (a resource or value) can grant permissions to groups of users, and secondly, permissions can be granted directly
to a group of users (not bound to a specific object). There are six built-in groups: *UnknownUser*, *KnownUser*,
*Creator*, *ProjectMember*, *ProjectAdmin*, and *SystemAdmin*. These groups can be used in the same way as normal user
created groups for permission management, i.e. can be used to give certain groups of users, certain permissions, without
the need to explicitly create them.

A user becomes implicitly a member of such a group by satisfying certain conditions:

**UnknownUser**:
  Any user who has not logged into the Knora API server is automatically assigned to this group.

**KnownUser**:
  Any user who has logged into the Knora API server is automatically assigned to this group.

**Creator**:
  When checking a user’s permissions on an object, the user is automatically assigned to this group if he is
  the creator of the object.

**ProjectMember**:
  Membership by a user is received by being a member (```knora-base:isInGroup```) of a specific *ProjectMember* group
  attached to a project. This group is automatically created for each project at project creation time. Adding a user
  to a project, automatically adds him to this group.

**ProjectAdmin**:
  Membership by a user is received by being a member (```knora-base:isInGroup```) of a specific *ProjectAdmin* group
  attached to a project. This group is automatically created for each project at project creation time.

**SystemAdmin**:
  The ``root`` user is by default member of this group. Membership is received by setting the property
  ``knora-base:isInGroup`` to ``knora-base:SystemAdmin`` on a ``knora-base:User``.

To use these build-in groups as values for properties, the IRI is constructed by appending the name of the built-in
group to ``knora-base``, e.g., ``knora-base:KnownUser`` where ``knora-base`` corresponds to ``http://www.knora.org/ontology/knora-base#``.


Permissions
------------

As mentioned, there are two distinct groups of permissions. The first is called *object access permissions* and contains
permissions that point from explicit **objects** (resources/values) to groups. The second group of permissions is called
*administrative permissions* and contains permissions that are put directly either on a **project** or a **group**
inside a project.

Object Access Permissions
^^^^^^^^^^^^^^^^^^^^^^^^^^
An object (resource / value) can grant the following permissions:
  1. *knora-base:hasRestrictedViewPermission*: Allows a restricted view of the object, e.g. a view of an image with a
     watermark.
  2. *knora-base:hasViewPermission*: Allows an unrestricted view of the object. Having view permission on a resource
     only affects the user’s ability to view information about the resource other than its values. To view a value, she
     must have view permission on the value itself.
  3. *knora-base:hasModifyPermission*: For values, this permission allows a new version of a value to be created. For
     resources, this allows the user to create a new value (as opposed to a new version of an existing value), or to
     change information about the resource other than its values. When he wants to make a new version of a value, his
     permissions on the containing resource are not relevant. However, when he wants to change the target of a link,
     the old link must be deleted and a new one created, so he needs modify permission on the resource.
  4. *knora-base:hasDeletePermission*: Allows the item to be marked as deleted.
  5. *knora-base:hasChangeRightsPermission*: Allows the permissions granted by the object to be changed.

Each permission in the above list implies all lower-numbered permissions.

A user’s permission level on a particular object is calculated in the following way:

  1. Make a list of the groups that the user belongs to, including Creator and/or ProjectMember if applicable.
  2. If the user is the owner of the object, give her the highest level of permissions.
  3. Otherwise, make a list of the permissions that she can obtain on the object, by iterating over the permissions
     that the object grants. For each permission, if she is in the specified group, add the specified permission to the
     list of permissions she can obtain.
  4. From the resulting list, select the highest-level permission.
  5. If the result is that she would have no permissions, give her whatever permission *UnknownUser* would have.


Administrative Permissions (Roles)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The following permissions can be set for the whole project (*ProjectMember* group) or any other group belonging to the
project. For users that are members of a number of groups with permissions, the final set of permissions is additive and
most permissive:

  1. Resource Creation Permissions:
  
      a) *hasProjectResourceCreateAllPermission*:

        - description: gives the permission to create resources inside the project
        - value: ``"true"^^xsd:boolean``

      b) *hasProjectResourceCreateRestrictedPermission*:
      
        - description: restricted resource creation permission
        - value: a list of *ResourceClasses* the user should only be able to create instances of.

  2. Project Administration Permissions:
  
      a) *hasProjectAllAdminPermission*:
      
        - description: gives the user the permission to do anything on project level, i.e. create new groups, modify all
          existing groups (*group info*, *group membership*, *resource creation permissions*, *project administration
          permissions*, and *default permissions*)
        - value: ``"true"^^xsd:boolean``
      
      b) *hasProjectAllGroupAdminPermission*:

        - description: gives the user the permission to modify *group info* and *group membership* on *all* groups belonging
          to the project.
        - value: ``"true"^^xsd:boolean``

      b) *hasProjectRestrictedGroupAdminPermission*:

        - description: gives the user the permission to modify *group info* and *group membership* on *certain* groups
          belonging to the project.
        - value: a list of ``knora-base:UserGroup``

      c) *hasProjectRightsAdminPermission*:

        - description: gives the user the permission to change the *permissions* on all objects belonging to the
          project (e.g., default permissions attached to groups and permissions on objects).
        - value: ``"true"^^xsd:boolean``

      d) *hasProjectOntologyAdminPermission*:

        - description: gives the user the permission to administer the project ontologies
        - value: ``"true"^^xsd:boolean``

  3. Default Permissions:

      a) *knora:base:hasDefaultRestrictedViewPermission*:

        - description: any object, created by a user inside a group holding this permission, is restricted to carry this
          permission
        - value: a list of ``knora-base:UserGroup``

      b) *knora-base:hasDefaultViewPermission*:

        - description: any object, created by a user inside a group holding this permission, is restricted to carry this
          permission
        - value: a list of ``knora-base:UserGroup``

      c) *knora-base:hasDefaultModifyPermission* accompanied by a list of groups.

        - description: any object, created by a user inside a group holding this permission, is restricted to carry this
          permission
        - value: a list of ``knora-base:UserGroup``

      d) *knora-base:hasDefaultDeletePermission* accompanied by a list of groups.

        - description: any object, created by a user inside a group holding this permission, is restricted to carry this
          permission
        - value: a list of ``knora-base:UserGroup``

      e) *knora-base:hasDefaultChangeRightsPermission* accompanied by a list of groups.

        - description: any object, created by a user inside a group holding this permission, is restricted to carry this
          permission
        - value: a list of ``knora-base:UserGroup``


Default Permissions
--------------------

As described earlier, it is possible to define default permissions for newly created resources / values by attaching the
special properties to groups. The groups these properties are attached to, can either be user created or one of the
built-in groups.

A the time a resource / value is created, it will be possible to supply a set of permissions, with which
the resource / value should be created. These supplied permissions will only be used if no default permissions are
defined. In the case that default permissions are defined, any supplied permissions will be *discarded*.

These default permissions are going to be given for each newly created project:

  - ``knora-base:SystemAdmin`` Group:
     - receives implicitly *hasProjectResourceCreateAllPermission* for all projects
     - receives implicitly *knora-base:hasChangeRightsPermission* on all objects from all projects

  - ``ProjectAdmin`` Group:
     - receives *hasProjectResourceCreateAllPermission*
     - receives *hasProjectAllAdminPermission*
     - receives implicitly *knora-base:hasChangeRightsPermission* on all objects

  - ``ProjectMember`` Group:
     - receives *hasProjectResourceCreateAllPermission*
     - receives *knora-base:hasDefaultChangeRightsPermission* for *knora-base:Creator*
     - receives *knora-base:hasDefaultModifyPermission* for this *ProjectMember* group
     - receives *knora-base:hasDefaultViewPermission* for *knora-base:KnownUser*


Default Permissions Matrix for new Projects
-------------------------------------------

The access control matrix defines what are the default operations a *subject* (i.e. User), being a member of a built-in
group (represented by row headers), is permitted to perform on an *object* (represented by column headers). The
different operation abbreviations used are defined as follows:

*C*:
  *Create* - the subject inside the group is allowed to *create* the object.

*U*:
  *Update* - the subject inside the group is allowed to *update* the object.

*R*:
  *Read* - the subject inside the group is allowed to *read* **all** information about the object.

*D*:
  *Delete* - the subject inside the group is allowed to *delete* the object.

*P*:
  *Permission* - the subject inside the group is allowed to change the *permissions* on the object.

*-*:
  *none* - none or not applicable 


.. table:: Default Permissions Matrix for new Projects

   ==================== ======== ========= ===================== ======================== ======================= 
   Built-In Group       Project  Group     User                  Resource                 Value
   ==================== ======== ========= ===================== ======================== =======================
   **SystemAdmin**      ``CRUD`` ``CRUDP`` ``CRUDP`` all         ``CRUDP`` all            ``CRUDP`` all
   **ProjectAdmin**     ``-RUD`` ``CRUDP`` ``CRUDP`` +/- project ``CRUDP`` (in project)   ``CRUDP`` (in project)
   **ProjectMember**    ``----`` ``-----`` ``-----``             ``CRUD-`` (in project)   ``-----`` (in project)
   **Creator**          ``----`` ``-----`` ``-----``             ``-RUDP`` (his resource) ``-----`` (his value)
   **KnownUser**        ``C---`` ``C----`` ``CRUD-`` himself     ``R----`` (in project)   ``R----`` (in project)
   ==================== ======== ========= ===================== ======================== =======================


Implementation
---------------

A the time the ``UserProfile`` is queried, all group memberships and the permissions carried by those groupes are
queried for all projects the user is a member of. This information is then stored as an easy accessible object inside
the ``UserProfile`` so that this information is readily available where needed. This is a somewhat expensive operation,
but will only be executed so often since there is a ``UserProfile`` caching mechanism in place.


Use Cases
----------

UC01: Teaching a Class
^^^^^^^^^^^^^^^^^^^^^^^

**Description**:
  I'm teaching a class and I have the names and email addresses of all the students. I want to create a project, divide
  the students into groups (which will only be relevant to this project, e.g. one group for each section of the class),
  and put some students in each group. I don't want people to be able to join the project or the group by themselves.

**Solution**:
  The teacher creates different groups and adds users to those groups. Additionally, the teacher can give TA's
  *GroupAdmin* privileges, and let the TA's add students to the different groups.

UC02: Unibas Librarian
^^^^^^^^^^^^^^^^^^^^^^^
**Description**:
  I'm a Unibas librarian managing several archiving projects. I need to give everyone at the university permission to
  view all these projects. I want to create a group called *UnibasUser* that everyone with a Unibas email address will
  automatically belong to. Most of the resources in those projects can then grant view permission to *UnibasUser*. Or
  perhaps the group will be *SwitchUser*, for anyone at a Swiss university. Or something even broader.

**Solution**:
  These can be solved by creating *Smart Groups*, where the user can define what properties need to be set, so that
  an user is automatically part of this group. This will be implemented at a later time, as it is not trivial and should
  also include all special groups (e.g., KnownUser, ProjectMember, ProjectAdmin, etc.) that are currently hard-coded
  inside the system.

UC03: Crowdsourcing Project
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

**Description**:
  I'm doing a crowdsourcing project, which involves several different groups that work on different tasks. I'm hoping
  for thousands of users, and I'd like anyone to be able to join the project and add themselves to any group they want
  (as long as Knora verifies their email address), without needing approval from me.

**Solution**:
  This can be solved by allowing self-assignment to a group.

UC04: User "left" Knora
^^^^^^^^^^^^^^^^^^^^^^^^

**Description**:
  An user who was an active collaborator, decides to "quit", and wants to delete his user.

**Solution**:
  The user's IRI is saved on each value change as part of the versioning mechanism. Exchanging the user's IRI in
  those places would count as 'rewriting history'. So deleting a user will not be possible, instead the user will be
  set as ``not active``.

Webapi Components
------------------

For the management of *users*, *projects*, and *groups*, the Knora API following a resource centric approach, provides
three endpoints corresponding to the three classes of objects that they have an effect on, namely:

* Users Endpoint: ``http://server:port/v1/users`` -> ``knora-base:User``
* Projects Endpoint: ``http://server:port/v1/projects`` -> ``knora-base:knoraProject``
* Groups Endpoint: ``http://server:port/v1/groups`` -> ``knora-base:UserGroup``

All information regarding users, projects and groups is stored in the ``http://www.knora.org/admin`` named graph.


Users Endpoint
^^^^^^^^^^^^^^^^^^
**Create user**:
  - Required permission: none, self-registration is allowed
  - Required information: username, given name, family name, email, password
  - Optional information: phone
  - Returns IRI of newly created user


**Update user**:
  - Required permission: SystemAdmin / User
  - Changeable information: username, given name, family name, email, password, phone


**Delete user (-> update user)**:
  - Required permission: SystemAdmin / User
  - Effects property: ``knora-base:isActiveUser`` with value ``true`` or ``false``


Example User Information stored in admin graph:
::

  <http://data.knora.org/users/91e19f1e01> rdf:type knora-base:User ;
       knora-base:userid "root" ;
       foaf:familyName "Admin" ;
       foaf:givenName "Administrator" ;
       knora-base:password "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3" ;
       knora-base:passwordSalt "" ;
       knora-base:email "test@test.ch" ;
       knora-base:phone "123456" ;
       knora-base:preferredLanguage "de" ;
       knora-base:isActiveUser "true"^^xsd:boolean ;
       knora-base:isInProject <http://data.knora.org/projects/[UUID]> ;
       knora-base:isInGroup <http://www.knora.org/ontology/knora-base#SystemAdmin> ,
                            <http://data.knora.org/projects/[UUID]> .


Projects Endpoint
^^^^^^^^^^^^^^^^^^
**Create project**:
  - Required permission: SystemAdmin
  - Required information: projectShortname (unique; used for named graphs), projectBasepath
  - Optional information: projectLongname, projectDescription, projectKeyword, projectLogo
  - Returns IRI of newly created project
  - Effects:
      - create project
      - create group named *ProjectAdmin*, give group *hasProjectAllAdminPermission* and *hasProjectResourceCreateAllPermission*
      - create group named *ProjectMember*, give group *hasProjectResourceCreateAllPermission*,
        *knora-base:hasDefaultChangeRightsPermission* for *knora-base:Creator*,
        *knora-base:hasDefaultModifyPermission* for this *ProjectMember* group, and
        *knora-base:hasDefaultViewPermission* for *knora-base:KnownUser*


**Update project information**:
  - Required permission: SystemAdmin / ProjectAdmin
  - Changeable information: longname, description
  - Effects property: ``knora-base:projectLongname``, ``knora-base:description``


**Add/remove user to/from project**:
  - Required permission: SystemAdmin / ProjectAdmin / User (if project self-assignment is enabled)
  - Required information: project IRI, user IRI
  - Optional information: admin status
  - Effects: ``knora-base:isInProject`` and ``knora-base:isInGroup`` named ``ProjectMember`` of current project


**Delete/Un-Delete project (-> update project)**:
  - Required permission: SystemAdmin / ProjectAdmin
  - Effects property: ``knora-base:isActiveProject`` with value ``true`` or ``false``


**Enable/disable self-join**:
  - Required permission: SystemAdmin / ProjectAdmin
  - Effects property: ``knora-base:hasSelfAssignmentEnabled`` with value ``true`` or ``false``


Example Project Information stored in admin named graph:
::

   <http://data.knora.org/projects/[UUID]>
        rdf:type knora-base:knoraProject ;
        knora-base:projectBasepath "/imldata/SALSAH-TEST-01/images" ;
        knora-base:projectShortname "images" ;
        knora-base:projectLongname "Images Collection Demo" ;
        knora-base:projectOntolgyGraph "http://www.knora.org/ontology/images" ;
        knora-base:projectDataGraph "http://www.knora.org/data/images" ;
        knora-base:isActiveProject "true"^^xsd:boolean ;
        knora-base:hasSelfJoinEnabled "false"^^xsd:boolean .


   <http://data.knora.org/groups/[UUID]>
        rdf:type knora-base:UserGroup ;
        knora-base:groupName "ProjectAdmin" ;
        knora-base:groupDescription "Default Project Admin Group" ;
        knora-base:belongsToProject <http://data.knora.org/projects/[UUID]> ;
        knora-base:hasProjectAllAdminPermission "true"^^xsd:boolean ;
        knora-base:hasProjectResourceCreateAllPermission "true"^^xsd:boolean .


   <http://data.knora.org/groups/[UUID]>
        rdf:type knora-base:UserGroup ;
        knora-base:groupName "ProjectMember" ;
        knora-base:groupDescription "Default Project Member Group" ;
        knora-base:belongsToProject <http://data.knora.org/projects/[UUID]> ;
        knora-base:hasProjectResourceCreateAllPermission "true"^^xsd:boolean ;
        knora-base:hasDefaultChangeRightsPermission knora-base:Creator ;
        knora-base:hasDefaultViewPermission knora-base:KnownUser .


Groups Endpoint
^^^^^^^^^^^^^^^^

**Create group**:
  - Required permission: SystemAdmin / hasProjectAllAdminPermission / hasProjectAllGroupAdminPermission
  - Required information: group name (unique inside project), project IRI
  - Optional information: group description
  - Returns IRI of newly created group


**Update group information**:
  - Required permission: SystemAdmin / hasProjectAllAdminPermission /  hasProjectAllGroupAdminPermission /
    hasProjectRestrictedGroupAdminPermission (for this group)
  - Changeable information: name, group description
  - Effects property: ``<http://xmlns.com/foaf/0.1/name>``, ``knora-base:groupDescription``


**Add/remove user to/from group** (not *SystemAdmin*):
  - Required permission: SystemAdmin / hasProjectAllAdminPermission / hasProjectAllGroupAdminPermission /
    hasProjectRestrictedGroupAdminPermission (for this group) / User (if group self-assignment is enabled)
  - Required information: group IRI, user IRI
  - Effects: ``knora-base:isInGroup``


**Add/remove user to/from group** (*SystemAdmin* group):
  - Required permission: SystemAdmin
  - Required information: group IRI (http://www.knora.org/ontology/knora-base#SystemAdmin), user IRI
  - Effects: ``knora-base:isInGroup``


**Add/remove user to/from group** (*ProjectAdmin* group):
  - Required permission: SystemAdmin, ProjectAdmin
  - Required information: project IRI, group IRI, user IRI
  - Effects: ``knora-base:isInGroup``


**Enable/disable self-join**:
  - Required permission: SystemAdmin / hasProjectAllAdminPermission / hasProjectAllGroupAdminPermission /
    hasProjectRestrictedGroupAdminPermission (for this group)
  - Effects property: ``knora-base:hasSelfAssignmentEnabled`` with value ``true`` or ``false``


**Add/change administrative permissions to a group**:
  - Required permission: SystemAdmin / hasProjectAllAdminPermission / hasProjectRightsAdminPermission
  - Effects property: resource creation permissions, project administration permissions, default permissions


**Delete group**:
  - Required permission: SystemAdmin / hasProjectAllAdminPermission
  - Effect: ``knora-base:isInGroup`` / removes group from any object permissions


Example Group Information stored in admin named graph:
::

   <http://data.knora.org/groups/[UUID]>
        rdf:type knora-base:UserGroup ;
        knora-base:groupName "Name of the group" ;
        knora-base:groupDescription "A description of the group" ;
        knora-base:belongsToProject <http://data.knora.org/projects/[UUID]> ;
        knora-base:hasSelfJoinEnabled "false"^^xsd:boolean ;
        knora-base:hasProjectResourceCreateAllPermission "true"^^xsd:boolean ;
        knora-base:hasProjectResourceCreateRestrictedPermission <http://www.knora.org/ontology/knora-base#Resource>
        knora-base:hasProjectAllAdminPermission "true"^^xsd:boolean ;
        knora-base:hasProjectRestrictedGroupAdminPermission <http://data.knora.org/groups/[UUID]> ;
        knora-base:hasProjectRightsAdminPermission "true"^^xsd:boolean ;
        knora-base:hasProjectOntologyAdminPermission "true"^^xsd:boolean ;
        knora-base:hasDefaultRestrictedViewPermission <http://data.knora.org/groups/[UUID]> ;
        knora-base:hasDefaultViewPermission <http://data.knora.org/groups/[UUID]> ;
        knora-base:hasDefaultModifyPermission <http://data.knora.org/groups/[UUID]> ;
        knora-base:hasDefaultDeletePermission <http://data.knora.org/groups/[UUID]> ;
        knora-base:hasDefaultChangeRightsPermission <http://data.knora.org/groups/[UUID]> .


Redesign / Questions June 2016
-------------------------------

**Permissions constrained to groups**
  - Why this constraint?
  - => This is just the way we are doing it. Makes it a bit simpler.

**Resource owner permission to disruptive**

  - knora-base:attachedToUser gives owner status to the person who created the resource.
  - **Proposed change:** remove this altogether or make institution/project owner of the resource.
  - Should hiwis be "owners" of resources they create on behalf of their professor?
  - If the creator should have max permission, then give it explicitly.
  - => Owner will be renamed to creator. We need this for provenance. Does not give any permissions automatically. The
    permissions depend on what is defined for the project and the *creator* smart group.
  
**Resource creation permission to course**

  - being part of a projects gives resource creation permission. What if some project members are not allowed to create
    new resources (or only certain types; Lumiere Lausanne requirement), but are only allowed to change existing
    resources?
  - => These kind of permissions can be set on groups. A project can have different groups, giving different kind of
    permissions.  

**Support Default Permissions**

  - Allow for a project to define permissions that a newly created resource inside a project should receive (current
    Salsah behavior)
  - Lumiere Lausanne requirement
  - => Will be allowed.
  
**Groups**

  - Do groups belong to projects, i.e. are they seen as extensions to projects?
  - Does someone need to be part of a project to belong to a group of that project?
  - => Every group needs to belong to a project. No GroupAdmins. ProjectAdmins with additional GroupAdmin permissions.
  
**root**

  - Should the 'root' / SystemAdmin user have 'implicitly' or 'explicitly' all permissions?
  - => Has implicitly all permissions.
  
  - Does the has all permissions also extend to projects? Is the root user going to be part of every project?
    If yes, then again implicitly or explicitly?
  - => Since 'root' / SystemAdmin already has all permissions, doesn't realy matter if part of a project or group
    
**Ivan's Use Case**

  - The system administrator creates the project and sets Ivan as the project administrator. As the project
    administrator, I have all permissions on all objects (Resources/Values; Project Groups) belonging to the project
    (knora-base:attachedToProject). Nobody outside of the project should be allowed to see anything that is created as
    part of Ivan's project. He wants to be able to create two groups: *Reviewer*, *Creator*.
    The *Reviewer* group should only give *read-access* to someone inside the group to resources pointing to this group,
    but allow the creation of annotations. Further, annotations should only be readable by users inside the *Reviewer*
    group.
    The *Creator* group should give a user create permission and modify permission on the objects the user has created.
    Any resources created belong to the project. The *Creator* group is meant for contributors helping out with the
    project, e.g., Hiwis.
  - => Covered
  
**Lausanne Projects**

  - A project wants to restrict the permissions of newly created resources to a fixed set
  - => Covered. Will be able do define 'default permissions' and restrict the creation of new resources to these
    permissions
  
  - This means for the current implementation, that any permissions supplied during the resource creation request need
    to be checked and if needed overriden.
  - => Covered. Also in the new design, the backend will need to always check the suplied permissions for newly created
    resources as we cannot ve sure that the GUI will behave correctly (e.g., many different "Salsah" implementations)
  
  - Restrict creation/access of certain classes of resources to certain groups, e.g., group A is able to create/access
    resources of class A but not of class B.
  - => Covered. Will be able to give a certain group only create permission for specific classes
    
**Results**

  - *Owner* renamed to *Creator*
  - Some permissions are attached to groups (e.g., Add Resource (Class), Modify Ontology, etc.),
    and some are attached to resources (e.g., this group has read/modify permission, etc.)
  - Ontologien Benutzung einschränken (nur auf bestimmte Gruppen, oder frei zur Verfügung)
  - System Admin Rechte implizit
  - Gruppen immer an Projekt gebunden
  - Keine Gruppen-Admins. Soll über Rollen vom Projekt-Admin geregelt werden können.
