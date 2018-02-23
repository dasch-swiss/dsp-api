.. Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
   Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.

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

.. _triplestore-updates:

Triplestore Updates
===================

.. contents:: :local:

Requirements
------------

General
^^^^^^^

The supported update operations are:

- Create a new resource with its initial values.

- Add a new value.

- Change a value.

- Delete a value (i.e. mark it as deleted).

- Delete a resource (i.e. mark it as deleted).

Users must be able to edit the same data concurrently.

Each update must be atomic and leave the database in a consistent, meaningful state, respecting
ontology constraints and permissions.

The application must not use any sort of long-lived locks, because they tend to hinder concurrent edits,
and it is difficult to ensure that they are released when they are no longer needed. Instead, if a user
requests an update based on outdated information (because another user has just changed something, and
the first user has not found out yet), the update must be not performed, and the application must notify
the user who requested it, suggesting that the user should check the relevant data and try again if
necessary. (We may eventually provide functionality to help users merge edits in such a situation. The
application can also encourage users to coordinate with one another when they are working
on the same data, and may eventually provide functionality to facilitate this coordination.)

We can assume that each SPARQL update operation will run in its own database transaction
with an isolation level of 'read committed'. This is what GraphDB does when it receives a
SPARQL update over HTTP (see `GraphDB SE Transactions`_). We cannot assume that it is possible
to run more than one SPARQL update in a single database transaction. (The `SPARQL 1.1 Protocol`_
does not provide a way to do this, and currently it can be done only by embedding the triplestore
in the application and using a vendor-specific API, but we cannot require this in Knora.)

Permissions
^^^^^^^^^^^

To create a new value (as opposed to a new version of an existing value), the user must have
``knora-base:hasModifyPermission`` on the containing resource.

To create a new version of an existing value, the user needs only to have ``knora-base:hasModifyPermission``
on the current version of the value; no permissions on the resource are needed.

Since changing a link requires deleting the old link and creating a new one (as described in
:ref:`triplestore-linking-reqs`), a user wishing to change a link must have modify permission on both
the containing resource and the ``knora-base:LinkValue`` for the existing link.

When a new value is created, it is given the default permissions specified in the definition of its
property. These are subproperties of ``knora-base:hasDefaultPermission``, and are converted into
the corresponding subproperties of ``knora-base:hasPermission``. Similarly, when a new resource is
created, it is given the default permissions specified in the definition of its OWL class.

Ontology Constraints
^^^^^^^^^^^^^^^^^^^^

Knora must not allow an update that would violate an ontology constraint.

When creating a new value (as opposed to adding a new version of an existing value), Knora must not
allow the update if the containing resource's OWL class does not contain a cardinality restriction for the
submitted property, or if the new value would violate the cardinality restriction.

It must also not allow the update if the type of the submitted value does not
match the ``knora-base:objectClassConstraint`` of the property, or if the
property has no ``knora-base:objectClassConstraint``. In the case of a
property that points to a resource, Knora must ensure that the target resource
belongs to the OWL class specified in the property's
``knora-base:objectClassConstraint``, or to a subclass of that class.

Duplicate and Redundant Values
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

When creating a new value, or changing an existing value, Knora checks whether the submitted
value would duplicate an existing value for the same property in the resource. The definition of
'duplicate' depends on the type of value; it does not necessarily mean that the two values are
strictly equal. For example, if two text values contain the same Unicode string, they are considered
duplicates, even if they have different Standoff markup. If resource ``R`` has property ``P``
with value ``V1``, and ``V1`` is a duplicate of ``V2``, the API server must not add another instance
of property ``P`` with value ``V2``. However, if the requesting user does not have permission to
see ``V2``, the duplicate is allowed, because forbidding it would reveal the contents of ``V2``
to the user.

When creating a new version of a value, Knora also checks whether the new version is redundant,
given the existing value. It is possible for the definition of 'redundant' can depend on the
type of value, but in practice, it means that the values are strictly equal: any change, however
trivial, is allowed.

Versioning
^^^^^^^^^^

Each Knora value (i.e. something belonging to an OWL class derived from
``knora-base:Value``) is versioned. This means that once created, a value is
never modified. Instead, 'changing' a value means creating a new version of
the value --- actually a new value --- that points to the previous version
using ``knora-base:previousValue``. The versions of a value are a singly-
linked list, pointing backwards into the past. When a new version of a value
is made, the triple that points from the resource to the old version (using a
subproperty of ``knora-base:hasValue``) is removed, and a triple is added to
point from the resource to the new version. Thus the resource always points
only to the current version of the value, and the older versions are available
only via the current version's ``knora-base:previousValue`` predicate.

Unlike values, resources (members of OWL classes derived from
``knora-base:Resource``) are not versioned. The data that is attached to a
resource, other than its values, can be modified.

Deleting
~~~~~~~~

Knora does not actually delete resources or values; it only marks them as
deleted. Deleted data is normally hidden. All resources and values must have
the predicate ``knora- base:isDeleted``, whose object is a boolean. If a
resource or value has been marked as deleted, it has
``knora-base:isDeleted true`` and has a ``knora-base:deleteDate``. An
optional ``knora-base:deleteComment`` may be added to explain why the
resource or value has been marked as deleted.

Normally, a value is marked as deleted without creating a new version of it.
However, link values must be treated as a special case. Before a ``LinkValue`` can be
marked as deleted, its reference count must be decremented to 0. Therefore, a
new version of the ``LinkValue`` is made, with a reference count of 0, and it
is this new version that is marked as deleted.

Since it is necessary to be able to find out when a resource was deleted, it
is not possible to undelete a resource. Moreover, to simplify the checking
of cardinality constraints, and for consistency with resources, it is not possible
to undelete a value, and no new versions of a deleted value can be made.
Instead, if desired, a new resource or value can be created by copying data from a
deleted resource or value.

.. _triplestore-linking-reqs:

Linking
^^^^^^^

Knora API v1 treats a link between two resources as a value, but in RDF, links must be treated
differently to other types of values. Knora needs to maintain information about the link,
including permissions and a version history. Since the link does not have a unique IRI of its own, Knora
uses RDF reifications_ for this purpose. Each link between two resources has exactly one (non-deleted)
``knora-base:LinkValue``. The resource itself has a predicate that points to the ``LinkValue``, using a
naming convention in which the word ``Value`` is appended to the name of the link predicate to produce
the link value predicate. For example, if a resource representing a book has a predicate called
``hasAuthor`` that points to another resource, it must also have a predicate called ``hasAuthorValue``
that points to the ``LinkValue`` in which information about the link is stored. To find a particular
``LinkValue``, one can query it either by using its IRI (if known), or by using its ``rdf:subject``,
``rdf:predicate``, and ``rdf:object`` (and excluding link values that are marked as deleted).

Like other values, link values are versioned. The link value predicate always points from
the resource to the current version of the link value, and previous versions are available only via
the current version's ``knora-base:previousValue`` predicate. Deleting a link means deleting the triple
that links the two resources, and making a new version of the link value, marked with
``knora-base:isDeleted``. A triple then points from the resource to this new, deleted version
(using the link value property).

The API allows a link to be 'changed' so that it points to a different target resource. This is
implemented as follows: the existing triple connecting the two resources is removed, and a new triple
is added using the same link property and pointing to the new target resource. A new version of the
old link's ``LinkValue`` is made, marked with ``knora-base:isDeleted``. A new ``LinkValue`` is made
for the new link. The new ``LinkValue`` has no connection to the old one.

When a resource contains ``knora-base:TextValue`` with Standoff markup that includes a reference
to another resource, this reference is materialised as a direct link between the two resources, to
make it easier to query. A special link property, ``knora-base:hasStandoffLinkTo``, is used for this
purpose. The corresponding link value property, ``knora-base:hasStandoffLinkToValue``, points to a
``LinkValue``. This ``LinkValue`` contains a reference count, indicated by
``knora-base:valueHasRefCount``, that represents the number of text values in the containing resource
that include one or more Standoff references to the specified target resource. Each time this number
changes, a new version of this ``LinkValue`` is made. When the reference count reaches zero, the triple
with ``knora-base:hasStandoffLinkTo`` is removed, and a new version of the ``LinkValue`` is made and
marked with ``knora-base:isDeleted``. If the same resource reference later appears again in a text value,
a new triple is added using ``knora-base:hasStandoffLinkTo``, and a new ``LinkValue`` is made, with
no connection to the old one.

For consistency, every ``LinkValue`` contains a reference count. If the link property is not
``knora-base:hasStandoffLinkTo``, the reference count will always be either 1 (if the link exists)
or 0 (if it has been deleted, in which case the link value will also be marked with
``knora-base:isDeleted``).

When a ``LinkValue`` is created for a standoff resource reference, it is given the same permissions
as the text value containing the reference.

Design
------

Responsibilities of Responders
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

``ResourcesResponderV1`` has sole responsibility for generating SPARQL to
create and updating resources, and ``ValuesResponderV1`` has sole
responsibility for generating SPARQL to create and update values. When a new
resource is created with its values, ``ValuesResponderV1`` generates SPARQL
statements that can be included in the ``WHERE`` and ``INSERT`` clauses of a
SPARQL update to create the values, and ``ResourcesResponderV1`` adds these
statements to the SPARQL update that creates the resource. This ensures that
the resource and its values are created in a single SPARQL update operation,
and hence in a single triplestore transaction.


Application-level Locking
^^^^^^^^^^^^^^^^^^^^^^^^^

The 'read committed' isolation level cannot prevent a scenario where two users
want to add the same data at the same time. It is possible that both requests
would do pre-update checks and simultaneously find that it is OK to add the
data, and that both updates would then succeed, inserting redundant data and
possibly violating ontology constraints. Therefore, Knora uses short-lived,
application-level write locks on resources, to ensure that only one request at
a time can update a given resource. Before each update, the application
acquires a resource lock. It then does the pre-update checks and the update,
then releases the lock. The lock implementation (in ``ResourceLocker``)
requires each API request message to include a random UUID, which is generated
in the :ref:`api-routing` package. Using application-level locks allows us to
do pre-update checks in their own transactions, and finally to do the SPARQL
update in its own transaction.

Ensuring Data Consistency
^^^^^^^^^^^^^^^^^^^^^^^^^

Knora enforces consistency constraints using three redundant mechanisms:

1. By doing pre-update checks using SPARQL SELECT queries and cached ontology data.
2. By doing checks in the ``WHERE`` clauses of SPARQL updates.
3. By using GraphDB's built-in consistency checker (see :ref:`consistency-checking`).

We take the view that redundant consistency checks are a good thing.

Pre-update checks are SPARQL ``SELECT`` queries that are executed while
holding an application-level lock on the resource to be updated. These checks
should work with any triplestore, and can return helpful, Knora-specific
error messages to the client if the request would violate a consistency
constraint.

However, the SPARQL update itself is our only chance to do pre-update checks
in the same transaction that will perform the update. The design of the
`SPARQL 1.1 Update`_ standard makes it possible to ensure that if certain
conditions are not met, the update will not be performed. In our SPARQL update
code, each update contains a ``WHERE`` clause, possibly a ``DELETE`` clause,
and an ``INSERT`` clause. The ``WHERE`` clause is executed first. It performs
consistency checks and provides values for variables that are used in the
``DELETE`` and/or ``INSERT`` clauses. In our updates, if the expectations of
the ``WHERE`` clause are not met (e.g. because the data to be updated does not
exist), the ``WHERE`` clause should return no results; as a result, the update
will not be performed.

Regardless of whether the update changes the contents of the triplestore, it
returns nothing. If the update did nothing because the conditions of the WHERE
clause were not met, the only way to find out is to do a ``SELECT``
afterwards. Moreover, in this case, there is no straightforward way to
find out which conditions was not met. This is one reason why Knora does
pre-update checks using separate ``SELECT`` queries and/or cached ontology
data, *before* performing the update. This makes it possible to return specific
error messages to the user to indicate why an update cannot be performed.

Moreover, while some checks are easy to do in a SPARQL update, others are
difficult, impractical, or impossible. Easy checks include checking whether a
resource or value exists or is deleted, and checking that the
``knora-base:objectClassConstraint`` of a predicate matches the ``rdf:type`` of
its intended object. Cardinality checks are not very difficult, but they perform
poorly on Jena. Knora does not do permission checks in SPARQL, because its
permission-checking algorithm is too complex to be implemented in SPARQL. For
this reason, Knora's check for duplicate values cannot be done in SPARQL
update code, because it relies on permission checks.

In a bulk import operation, which can create a large number of resources in
a single SPARQL update, a ``WHERE`` clause can become very expensive for the
triplestore, in terms of memory as well as execution time. Moreover, RDF4J
(and hence GraphDB) uses a recursive algorithm to parse SPARQL queries with
``WHERE`` clauses, so the size of a ``WHERE`` clause is limited by the stack space
available to the Java Virtual Machine. Therefore, in bulk import operations,
Knora uses ``INSERT DATA``, which does not involve a ``WHERE`` clause. Bulk
imports thus rely on checks (1) and (3) above.

SPARQL Update Examples
----------------------

The following sample SPARQL update code is simpler than what Knora actually does. It is included here to
illustrate the way Knora's SPARQL updates are structured and how concurrent updates are handled.

.. _find-value-in-version-history:

Finding a value IRI in a value's version history
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

We will need this query below. If a value is present in a resource
property's version history, the query returns everything known about the
value, or nothing otherwise:

::

    prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
    prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    prefix knora-base: <http://www.knora.org/ontology/knora-base#>

    SELECT ?p ?o
    WHERE {
        BIND(IRI("http://data.knora.org/c5058f3a") as ?resource)
        BIND(IRI("http://www.knora.org/ontology/incunabula#book_comment") as ?property)
        BIND(IRI("http://data.knora.org/c5058f3a/values/testComment002") as ?searchValue)

        ?resource ?property ?currentValue .
        ?currentValue knora-base:previousValue* ?searchValue .
        ?searchValue ?p ?o .
    }

Creating the initial version of a value
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

::

    prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
    prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    prefix knora-base: <http://www.knora.org/ontology/knora-base#>

    WITH <http://www.knora.org/ontology/incunabula>
    INSERT {
        ?newValue rdf:type ?valueType ;
                  knora-base:valueHasString """Comment 1""" ;
                  knora-base:attachedToUser <http://data.knora.org/users/91e19f1e01> ;
                  knora-base:attachedToProject <http://data.knora.org/projects/77275339> ;
                  knora-base:hasPermissions "V knora-admin:KnownUser,knora-admin:UnknownUser|M knora-admin:ProjectMember"  ;
                  knora-base:valueTimestamp ?currentTime .

        ?resource ?property ?newValue .
    } WHERE {
        BIND(IRI("http://data.knora.org/c5058f3a") as ?resource)
        BIND(IRI("http://www.knora.org/ontology/incunabula#book_comment") as ?property)
        BIND(IRI("http://data.knora.org/c5058f3a/values/testComment001") AS ?newValue)
        BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType)
        BIND(NOW() AS ?currentTime)

        # Do nothing if the resource doesn't exist.
        ?resource rdf:type ?resourceClass .

        # Do nothing if the submitted value has the wrong type.
        ?property knora-base:objectClassConstraint ?valueType .
    }

To find out whether the insert succeeded, the application can use the
query in :ref:`find-value-in-version-history` to look for the new IRI in the
property's version history.

Adding a new version of a value
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

::

    prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
    prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    prefix knora-base: <http://www.knora.org/ontology/knora-base#>

    WITH <http://www.knora.org/ontology/incunabula>
    DELETE {
        ?resource ?property ?currentValue .
    } INSERT {
        ?newValue rdf:type ?valueType ;
                  knora-base:valueHasString """Comment 2""" ;
                  knora-base:previousValue ?currentValue ;
                  knora-base:attachedToUser <http://data.knora.org/users/91e19f1e01> ;
                  knora-base:attachedToProject <http://data.knora.org/projects/77275339> ;
                  knora-base:hasPermissions "V knora-admin:KnownUser,knora-admin:UnknownUser|M knora-admin:ProjectMember"  ;
                  knora-base:valueTimestamp ?currentTime .

        ?resource ?property ?newValue .
    } WHERE {
        BIND(IRI("http://data.knora.org/c5058f3a") as ?resource)
        BIND(IRI("http://data.knora.org/c5058f3a/values/testComment001") AS ?currentValue)
        BIND(IRI("http://data.knora.org/c5058f3a/values/testComment002") AS ?newValue)
        BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType)
        BIND(NOW() AS ?currentTime)

        ?resource ?property ?currentValue .
        ?property knora-base:objectClassConstraint ?valueType .
    }

The update request must contain the IRI of the most recent version of
the value (``http://data.knora.org/c5058f3a/values/c3295339``). If this
is not in fact the most recent version (because someone else has done an
update), this operation will do nothing (because the ``WHERE`` clause
will return no rows). To find out whether the update succeeded, the
application will then need to do a SELECT query using the
query in :ref:`find-value-in-version-history`. In the case of concurrent updates,
there are two possibilities:

1. Users A and B are looking at version 1. User A submits an update and
   it succeeds, creating version 2, which user A verifies using a
   SELECT. User B then submits an update to version 1 but it fails,
   because version 1 is no longer the latest version. User B's SELECT
   will find that user B's new value IRI is absent from the value's
   version history.

2. Users A and B are looking at version 1. User A submits an update and
   it succeeds, creating version 2. Before User A has time to do a
   SELECT, user B reads the new value and updates it again. Both users
   then do a SELECT, and find that both their new value IRIs are present
   in the value's version history.

Getting all versions of a value
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

::

    prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
    prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    prefix knora-base: <http://www.knora.org/ontology/knora-base#>

    SELECT ?value ?valueTimestamp ?previousValue
    WHERE {
        BIND(IRI("http://data.knora.org/c5058f3a") as ?resource)
        BIND(IRI("http://www.knora.org/ontology/incunabula#book_comment") as ?property)
        BIND(IRI("http://data.knora.org/c5058f3a/values/testComment002") AS ?currentValue)

        ?resource ?property ?currentValue .
        ?currentValue knora-base:previousValue* ?value .

        OPTIONAL {
            ?value knora-base:valueTimestamp ?valueTimestamp .
        }

        OPTIONAL {
            ?value knora-base:previousValue ?previousValue .
        }
    }

This assumes that we know the current version of the value. If the
version we have is not actually the current version, this query will
return no rows.

.. _GraphDB SE Transactions: http://graphdb.ontotext.com/documentation/free/storage.html#transaction-control
.. _SPARQL 1.1 Protocol: http://www.w3.org/TR/sparql11-protocol/
.. _SPARQL 1.1 Update: http://www.w3.org/TR/sparql11-update/
.. _reifications: http://www.w3.org/TR/rdf-schema/#ch_reificationvocab

