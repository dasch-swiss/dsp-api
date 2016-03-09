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


Overview
---------

During the initial deployment of a Knora server, the main administration user (*root*) is created. This *root* user has
the right to do anything. An user in Knora

Knora’s concept of access control is that an object (a resource or value) can grant permissions to groups of users (but
not to individual users). There are four built-in groups: *UnknownUser*, *KnownUser*, *ProjectMember*, *Owner*.

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



Webapi Components
------------------

The management of *users*, *projects*, and *groups* is accessible over three endpoints. These three endpoints correspond
to the three classes of objects that they have an effect on:

* Users Endpoint -> ``knora-base:User``
* Projects Endpoint -> ``knora-base:knoraProject``
* Groups Endpoint -> ``knora-base:UserGroup``


Users Endpoint
^^^^^^^^^^^^^^^^^^
**Create user**:
  - Required permission: none, self-registration is allowed
  - Required information: username, given name, family name, email, password
  - Optional information: phone
  - Returns IRI of newly created user
  - User information is stored in the ``http://www.knora.org/admin`` named graph


**Update user**
  - Required permission: system admin, user
  - Changeable information: username, given name, family name, email, password, phone


**Add/remove user to/from group**:
  - Required permission: system admin, group admin
  - Required information: group IRI, user IRI
  - Effects: ``knora-base:isInGroup``
  - Information is stored in the ``http://www.knora.org/admin`` named graph


**Add/remove user to/from project**:

  - Required permission: system admin, project admin
  - Required information: project IRI, user IRI
  - Effects: ``knora-base:isInProject``
  - Information is stored in the ``http://www.knora.org/admin`` named graph


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
                   knora-base:isInGroup <http://data.knora.org/groups/[UUID]> ,
                   knora-base:isInProject <http://data.knora.org/projects/[UUID]> .




Projects Endpoint
^^^^^^^^^^^^^^^^^^
**Create project**:

  - Required permission: system admin (e.g., Knora root user or user given system admin rights).
  - Required information: longname, shortname (unique; used for named graphs)
  - Optional information: description
  - (default permissions for built-in groups?)
  - Returns IRI of newly created project
  - Project information is stored in the ``http://www.knora.org/admin`` named graph


**Update project information**:

  - Required permission: system admin, project admin
  - Changeable information: longname, description
  - Effects property: ``<http://xmlns.com/foaf/0.1/name>``, ``knora-base:description``


**Update/Set default permissions for built-in and arbitrary groups**:

  - Required permission: system admin, project admin
  - Required information: ???
  - Changeable information: default permissions for built-in and arbitrary groups, e.g., max permission for each group.
  - Effects property: ???


Example Project Information stored in admin named graph:
::

   <http://data.knora.org/projects/[UUID]>
                    rdf:type knora-base:knoraProject ;
                    knora-base:projectBasepath "/imldata/SALSAH-TEST-01/images" ;
                    <http://xmlns.com/foaf/0.1/name> "Images Collection Demo" ;
                    knora-base:projectShortname "images" ;
                    knora-base:projectOntolgyGraph "http://www.knora.org/ontology/images" ;
                    knora-base:projectDataGraph "http://www.knora.org/data/images" .


Groups Endpoint
^^^^^^^^^^^^^^^^

* Create group:

  - Required permission: system admin
  - Required information: group name
  - Optional information: group description
  - Returns IRI of newly created group
  - Group information is stored in the ``http://www.knora.org/admin`` named graph


* Update group information:

  - Required permission: system admin, group admin
  - Changeable information: name, description
  - Effects property: ``<http://xmlns.com/foaf/0.1/name>``, ``knora-base:description``


Example Group Information stored in admin named graph:
::

   <http://data.knora.org/groups/[UUID]> rdf:type knora-base:UserGroup ;
                    <http://xmlns.com/foaf/0.1/name> "group name" ;
                    knora-base:description "A description of the group" .
