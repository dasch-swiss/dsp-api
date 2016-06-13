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


Administration (Users, Projects, Groups)
=========================================

Scope
------

This Section includes management (creation, updating, deletion) of *Users*, *Groups*, and *Projects*.

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

Knora’s concept of access control is that an object (a resource or value) can grant permissions to groups of users (but
not to individual users). There are four built-in *smart* groups: *UnknownUser*, *KnownUser*, *ProjectMember*, *Owner*.
A user becomes implicitly a member of such a group by satisfying some condition.

**UnknownUser**:
    Any user who has not logged into the Knora API server is automatically assigned to this group.

**KnownUser**:
    Any user who has logged into the Knora API server is automatically assigned to this group.

**ProjectMember**:
    When checking a user’s permissions on an object, the user is automatically assigned to this group if
    she is a member of the project that the object belongs to.

**Owner**:
    When checking a user’s permissions on an object, the user is automatically assigned to this group if he is
    the owner of the object.


Access Controll Matrix
-----------------------
Some changes require administrative permissions. These permissions are given to a user by being a member of a smart
group. As before, a user becomes implicitly a member of such a group by satisfying some condition, i.e. having a
special property attached to the user.

**SystemAdmin**:
  This group gives the user all permissions. The ``root`` user is by default member of this group. Membership is
  received by setting the property ``knora-base:hasSystemAdminPermissions`` to ``true`` on a ``knora-base:User``.
  Can be assigned to a user only by the ``root`` user.

**ProjectAdmin**:
  This group gives a single user the administrative permissions for a specific *project*. Membership is received by
  adding the property ``knora-base:isProjectAdmin`` to the user and by pointing it to the project. Can be assigned to
  a user by ``SystemAdmin`` or other ``ProjectAdmin``. Received automatically by creating the project.

**GroupAdmin**:
  This group gives a single user the administrative permissions for a specific *group*. Membership is received by
  setting the property ``knora-base:isGroupAdmin`` to the user and by pointing to the group. Can be assigned to a user
  by ``SystemAdmin`` of other ``GroupAdmin``. Received automatically by creating the group.


The access control matrix defines what operations a *subject* (i.e. User), being a member of a special group
(represented by row headers), is permitted to perform on an *object* (represented by column headers). The different
operation abbreviations used are defined as follows:

*C*:
  *Create* - the subject inside the group is allowed to *create* the object.

*U*:
  *Update* - the subject inside the group is allowed to *update* the object.

*R*:
  *Read* - the subject inside the group is allowed to *read* **all** information about the object.

*D*:
  *Delete* - the subject inside the group is allowed to *delete* the object.


+-------------------+---------+---------+-----------------------------------+------------------------+------------------------+
|                   | Project | Group   | User                              | Resource               | Value                  |
+===================+=========+=========+===================================+========================+========================+
| **SystemAdmin**   | C R U D | C R U D | C R U D all                       | C R U D all            | C R U D all            |
+-------------------+---------+---------+-----------------------------------+------------------------+------------------------+
| **ProjectAdmin**  | R U     |         | add/remove to/from project        | C R U D inside project | C R U D inside project |
+-------------------+---------+---------+-----------------------------------+------------------------+------------------------+
| **GroupAdmin**    |         | R U     | group add/remove                  |                        |                        |
+-------------------+---------+---------+-----------------------------------+------------------------+------------------------+
| **KnownUser**     | C       | C       | C R U (D) himself                 | C                      | C (if allowed)         |
+-------------------+---------+---------+-----------------------------------+------------------------+------------------------+
| **Owner**         | -       | -       | -                                 | R U D                  | R U D                  |
+-------------------+---------+---------+-----------------------------------+------------------------+------------------------+

Default Permissions
--------------------

It will be possible to define default permissions for newly created resources / values on the *system*, *project*, and
*user* level. The following properties need to be defined to point to a list of IRI's of instances of
'knora-base:UserGroup': ``knora-base:hasDefaultRestrictedViewPermission``, ``knora-base:hasDefaultViewPermission``,
``knora-base:hasDefaultModifyPermission``, ``knora-base:hasDefaultDeletePermission``. These default permissions can then
be explicitly submitted by the client with each creation request. The user should be presented with a selection of
available default permissions, when creating a resource / value in the client.

The smart groups can be also used as values for the properties. The IRI is constructed by appending the name of the
built-in smart group to ``http://data.knora.org/groups/``, e.g., ``http://data.knora.org/groups/KnownUser``.


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

UC05: Project restricts possible permissions
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

**Description**:
  A project wants to restrict the permissions of newly created resources to a fixed set

**Solution**:
  Any permissions supplied during the resource creation request need to be checked and if needed overriden.

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
  
  
**Add/remove SystemAdmin status (-> update user)**:
  - Required permission: ``root``
  - Required information: user IRI
  - Effects: ``knora-base:hasSystemAdminPermissions`` with value ``true`` or ``false``


**Add/Update/Remove default permissions for new resources / values (-> update user)**:
  - Required permission: SystemAdmin / User
  - Required information: ``knora-base:hasDefaultRestrictedViewPermission``, ``knora-base:hasDefaultViewPermission``,
    ``knora-base:hasDefaultModifyPermission``, ``knora-base:hasDefaultDeletePermission``. Each property needs to point
    to a list of ``UserGroups`` or if nothing is specified, then to an empty list.


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
       knora-base:isSystemAdmin "true"^^xsd:boolean ;
       knora-base:isInProject <http://data.knora.org/projects/[UUID]> ;
       knora-base:isProjectAdmin <http://data.knora.org/projects/[UUID]> ;
       knora-base:isInGroup <http://data.knora.org/groups/[UUID]> ;
       knora-base:isGroupAdmin <http://data.knora.org/groups/[UUID]> ;
       knora-base:hasDefaultRestrictedViewPermission <http://data.knora.org/groups/[UUID]> ;
       knora-base:hasDefaultViewPermission <http://data.knora.org/groups/[UUID]> ,
                                           <http://data.knora.org/groups/KnownUser> ;
       knora-base:hasDefaultModifyPermission <http://data.knora.org/groups/[UUID]> ;
       knora-base:hasDefaultDeletePermission <http://data.knora.org/groups/[UUID]> .


Projects Endpoint
^^^^^^^^^^^^^^^^^^
**Create project**:
  - Required permission: SystemAdmin / KnownUser
  - Required information: projectShortname (unique; used for named graphs), projectBasepath
  - Optional information: projectLongname, projectDescription, belongsTo, projectKeyword, projectLogo
  - Returns IRI of newly created project


**Update project information**:
  - Required permission: SystemAdmin / ProjectAdmin
  - Changeable information: longname, description
  - Effects property: ``knora-base:projectLongname``, ``knora-base:description``


**Add/remove user to/from project**:
  - Required permission: SystemAdmin / ProjectAdmin / User (if project self-assignment is enabled)
  - Required information: project IRI, user IRI
  - Optional information: admin status
  - Effects: ``knora-base:isInProject``
  

**Add/remove user as ProjectAdmin**:
  - Required permission: SystemAdmin / ProjectAdmin
  - Required information: project IRI, user IRI
  - Effects: ``knora-base:hasProjectAdminPermissions``


**Update/Set default permissions for new resources / values**:
  - Required permission: SystemAdmin / ProjectAdmin
  - Required information: ``knora-base:hasDefaultRestrictedViewPermission``, ``knora-base:hasDefaultViewPermission``,
    ``knora-base:hasDefaultModifyPermission``, ``knora-base:hasDefaultDeletePermission``. Each property needs to point
    to a list of ``UserGroups`` or if nothing is specified, then to an empty list.


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
        knora-base:hasSelfJoinEnabled "false"^^xsd:boolean ;
        knora-base:hasProjectAdmin <User-IRI> ;
        knora-base:hasDefaultRestrictedViewPermission <http://data.knora.org/groups/[UUID]> ;
        knora-base:hasDefaultViewPermission <http://data.knora.org/groups/[UUID]> ,
                                            <http://data.knora.org/groups/KnownUser> ;
        knora-base:hasDefaultModifyPermission <http://data.knora.org/groups/[UUID]> ;
        knora-base:hasDefaultDeletePermission <http://data.knora.org/groups/[UUID]> .


Groups Endpoint
^^^^^^^^^^^^^^^^

**Create group**:
  - Required permission: SystemAdmin / KnownUser
  - Required information: group name
  - Optional information: group description
  - Returns IRI of newly created group


**Update group information**:
  - Required permission: SystemAdmin / GroupAdmin
  - Changeable information: name, group description
  - Effects property: ``<http://xmlns.com/foaf/0.1/name>``, ``knora-base:groupDescription``


**Add/remove user to/from group**:
  - Required permission: SystemAdmin / GroupAdmin / User (if group self-assignment is enabled)
  - Required information: group IRI, user IRI
  - Optional information: admin status
  - Effects: ``knora-base:isInGroup``


**Add/remove user as GroupAdmin **:
  - Required permission: SystemAdmin / GroupAdmin
  - Required information: group IRI, user IRI
  - Effects: ``knora-base:hasGroupAdminPermissions``


**Enable/disable self-join**:
  - Required permission: SystemAdmin / GroupAdmin
  - Effects property: ``knora-base:hasSelfAssignmentEnabled`` with value ``true`` or ``false``

Example Group Information stored in admin named graph:
::

   <http://data.knora.org/groups/[UUID]> rdf:type knora-base:UserGroup ;
        knora-base:groupName "Name of the group" ;
        knora-base:groupDescription "A description of the group" ;
        knora-base:isActiveGroup "true"^^xsd:boolean ;
        knora-base:hasSelfJoinEnabled "false"^^xsd:boolean ;
        knora-base:hasGroupAdmin <User-IRI> .

Redesign / Questions June 2016
--------------------

**Permissions constrained to groups***
  - Why this constraint?

**Resource owner permission to desruptive**
  - knora-base:attachedToUser gives owner status to the person who created the resource.
  - **Proposed change:** remove this altogether or make institution/project owner of the resource.
  - Should hiwis be "owners" of resources they create on behalf of their professor?
  - If the creator should have max permission, then give it explicitly.
  
**Resource creation permission to corse**
  - beeing part of a projects gives resource creation permission. What if some project members are not allowed to create
    new resources (or only certain types; Lumiere Lausanne requirement), but are only allowed to change existing resources?  

**Support Default Permissions**
  - Allow for a project to define permissions that a newly created resource inside a project should receive (current Salsah behavior)
  - Lumiere Lausanne requirement
  
**Groups**
  - Do groups belong to projects, i.e. are they seen as extensions to projects?
  - Does someone need to be part of a project to belong to a group of that project?
  
**root**
  - Should the 'root' user have 'implicitly' or 'explicitly' all permissions?
  - Does the has all permissions also extend to projects? Is the root user going to be part of every project?
    If yes, then again implicitly or explicitly?