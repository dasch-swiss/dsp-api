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

.. _consistency-checking:

Consistency Checking
====================

`Ontotext GraphDB`_ provides a mechanism for checking the consistency of data in
a repository each time an update transaction is committed. Knora provides
GraphDB-specific consistency rules that take advantage of this feature to
provide an extra layer of consistency checks, in addition to the checks that
are implemented in the Knora API server.

Requirements
------------

The Knora API server is designed to prevent inconsistencies in RDF data, as
far as is practical, in a triplestore-independent way (see
:ref:`triplestore-updates`). However, it is also useful to enforce consistency
constraints in the triplestore itself, for two reasons:

1. To prevent inconsistencies resulting from bugs in the Knora API server.
2. To prevent users from inserting inconsistent data directly into the triplestore,
   bypassing the Knora API server.

The design of the ``knora-base`` ontology supports two ways of specifying constraints
on data (see :ref:`knora-ontologies` for details):

1. A property definition should specify the types that are allowed as subjects
   and objects of the property, using ``knora-base:subjectClassConstraint`` and
   (if it is an object property) ``knora-base:objectClassConstraint``. Every subproperty of
   ``knora-base:hasValue`` or a ``knora-base:hasLinkTo`` (i.e. every property of a resource
   that points to a ``knora-base:Value`` or to another resource) is required have this constraint,
   because the Knora API server relies on it to know what type of object to expect for the property.
   Use of ``knora-base:subjectClassConstraint`` is recommended but not required.
2. A class definition should use OWL cardinalities (see
   `OWL 2 Quick Reference Guide`_) to indicate the properties that instances of
   the class are allowed to have, and to constrain the number of objects that each
   property can have. Subclasses of ``knora-base:Resource`` are required to have
   a cardinality for each subproperty of ``knora-base:hasValue`` or a ``knora-base:hasLinkTo``
   that resources of that class can have.

Specifically, consistency checking should prevent the following:

- An object property or datatype property has a subject of the wrong class, or an
  object property has an object of the wrong class (GraphDB's consistency checke
  cannot check the types of literals).
- An object property has an object that does not exist (i.e. the object is an IRI
  that is not used as the subject of any statements in the repository). This can be treated
  as if the object is of the wrong type (i.e. it can cause a violation of
  ``knora-base:objectClassConstraint``, because there is no compatible ``rdf:type`` statement
  for the object).
- A class has ``owl:cardinality 1`` or ``owl:minCardinality 1`` on an object property
  or datatype property, and an instance of the class does not have that property.
- A class has ``owl:cardinality 1`` or ``owl:maxCardinality 1`` on an object property
  or datatype property, and an instance of the class has more than one object for that
  property.
- An instance of ``knora-base:Resource`` has an object property pointing to a
  ``knora-base:Value`` or to another ``Resource``, and its class has no cardinality
  for that property.
- An instance of ``knora-base:Value`` has a subproperty of ``knora-base:valueHas``,
  and its class has no cardinality for that property.
- A datatype property has an empty string as an object.

Cardinalities in base classes are inherited by derived classes. Derived classes
can override inherited cardinalities by making them more restrictive, i.e. by specifying
a subproperty of the one specified in the original cardinality.

Instances of ``Resource`` and ``Value`` can be marked as deleted, using the property
``isDeleted``. This must be taken into account as follows:

- With ``owl:cardinality 1`` or ``owl:maxCardinality 1``, if the object of the
  property can be marked as deleted, the property must not have more than one object that has
  not been marked as deleted. In other words, it's OK if there is more than one object, as
  long only one of them has ``knora-base:isDeleted false``.
- With ``owl:cardinality 1`` or ``owl:minCardinality 1``, the property must
  have an object, but it's OK if the property's only object is marked as deleted.
  We allow this because the subject and object may have different owners, and it may
  not be feasible for them to coordinate their work. The owner of the object
  should always be able to mark it as deleted. (It could be useful to notify
  the owner of the subject when this happens, but that is beyond the scope of
  consistency checking.)

Design
------

When a repository is created in GraphDB, a set of consistency rules can be
provided, and GraphDB's consistency checker can be turned on to ensure that
each update transaction respects these rules, as described in the section
Reasoning_ of the GraphDB documentation. Like custom inference rules,
consistency rules are defined in files with the ``.pie`` filename extension,
in a GraphDB-specific syntax.

We have added rules to the standard RDFS inference rules file
``builtin_RdfsRules.pie``, to create the file ``KnoraRules.pie``. The ``.ttl``
configuration file that is used to create the repository must contain these
settings:

::

    owlim:ruleset "/path/to/KnoraRules.pie" ;
    owlim:check-for-inconsistencies "true" ;


The path to ``KnoraRules.pie`` must be an absolute path. The scripts provided
with Knora to create test repositories set this path automatically.

Consistency checking in GraphDB relies on reasoning. GraphDB's reasoning
is Forward-chaining_, which means that reasoning is applied to the contents
of each update, before the update transaction is committed, and the inferred
statements are added to the repository.

A GraphDB rules file can contain two types of rules: inference rules and
consistency rules. Before committing an update transaction, GraphDB applies
inference rules, then consistency rules. If any of the consistency rules are
violated, the transaction is rolled back.

An inference rule has this form:

::

    Id: <rule_name>
        <premises> <optional_constraints>
        -------------------------------
        <consequences> <optional_constraints>

The premises are a pattern that tries to match statements found in the data.
Optional constraints, which are enclosed in square brackets, make it possible
to specify the premises more precisely, or to specify a named graph (see
examples below). Consequences are the statements that will be inferred if the
premises match. A line of hyphens separates premises from consequences.

A GraphDB consistency rule has a similar form:

::

    Consistency: <rule_name>
        <premises> <optional_constraints>
        -------------------------------
        <consequences> <optional_constraints>

The differences between inference rules and consistency rules are:

- A consistency rule begins with ``Consistency`` instead of ``Id``.
- In a consistency rule, the consequences are optional. Instead of representing
  statements to be inferred, they represent statements that must exist if the premises
  are satisfied. In other words, if the premises are satisfied and the consequences
  are not found, the rule is violated.
- If a consistency rule doesn't specify any consequences, and the premises are
  satisfied, the rule is violated.

Rules use variable names for subjects, predicates, and objects, and they can use actual
property names.

Empty string as object
~~~~~~~~~~~~~~~~~~~~~~

If subject ``i`` has a predicate ``p`` whose object is an empty string,
the constraint is violated:

::

    Consistency: empty_string
        i p ""
        ------------------------------------

Subject and object class constraints
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If subject ``i`` has a predicate ``p`` that requires a subject of type ``t``,
and ``i`` is not a ``t``, the constraint is violated:

::

    Consistency: subject_class_constraint
        p <knora-base:subjectClassConstraint> t
        i p j
        ------------------------------------
        i <rdf:type> t

If subject ``i`` has a predicate ``p`` that requires an object of type ``t``,
and the object of ``p`` is not a ``t``, the constraint is violated:

::

    Consistency: object_class_constraint
        p <knora-base:objectClassConstraint> t
        i p j
        ------------------------------------
        j <rdf:type> t

Cardinality constraints
~~~~~~~~~~~~~~~~~~~~~~~

A simple implementation of a consistency rule to check ``owl:maxCardinality
1``, for objects that can be marked as deleted, could look like this:

::

    Consistency: max_cardinality_1_with_deletion_flag
        i <rdf:type> r
        r <owl:maxCardinality> "1"^^xsd:nonNegativeInteger
        r <owl:onProperty> p
        i p j
        i p k [Constraint j != k]
        j <knora-base:isDeleted> "false"^^xsd:boolean
        k <knora-base:isDeleted> "false"^^xsd:boolean
        ------------------------------------

This means: if resource ``i`` is a subclass of an ``owl:Restriction`` ``r``
with ``owl:maxCardinality 1`` on property ``p``, and the resource has two
different objects for that property, neither of which is marked as
deleted, the rule is violated. Note that this takes advantage of the
fact that ``Resource`` and ``Value`` have ``owl:cardinality 1`` on ``isDeleted``
(``isDeleted`` must be present even if false), so we do not need to check
whether ``i`` is actually something that can be marked as deleted.

However, this implementation would be much too slow. We therefore use
two optimisations suggested by Ontotext:

1. Add custom inference rules to make tables (i.e. named graphs) of pre-calculated
   information about the cardinalities on properties of subjects,
   and use those tables to simplify the consistency rules.
2. Use the ``[Cut]`` constraint to avoid generating certain redundant compiled rules
   (see `Entailment rules`_).

For example, to construct a table of subjects belonging to classes that have
``owl:maxCardinality 1`` on some property ``p``, we use the following custom
inference rule:

::

    Id: maxCardinality_1_table
        i <rdf:type> r
        r <owl:maxCardinality> "1"^^xsd:nonNegativeInteger
        r <owl:onProperty> p
        ------------------------------------
        i p r [Context <onto:_maxCardinality_1_table>]

The constraint ``[Context <onto:_maxCardinality_1_table>]`` means that the
inferred triples are added to the context (i.e. the named graph)
``http://www.ontotext.com/_maxCardinality_1_table``.  (Note that we have defined the prefix
``onto`` as ``http://www.ontotext.com/`` in the ``Prefices`` section of the rules file.)
As the GraphDB documentation on Rules_ explains:

    If the context is provided, the statements produced as rule consequences are
    not ‘visible’ during normal query answering. Instead, they can only be used as
    input to this or other rules and only when the rule premise explicitly uses
    the given context.

Now, to find out whether a subject belongs to a class with that cardinality on
a given property, we only need to match one triple. The revised implementation
of the rule ``max_cardinality_1_with_deletion_flag`` is as follows:

::

    Consistency: max_cardinality_1_with_deletion_flag
        i p r [Context <onto:_maxCardinality_1_table>]
        i p j [Constraint j != k]
        i p k [Cut]
        j <knora-base:isDeleted> "false"^^xsd:boolean
        k <knora-base:isDeleted> "false"^^xsd:boolean
        ------------------------------------

The constraint ``[Constraint j != k]`` means that the premises will be satisfied only
if the variables ``j`` and ``k`` do not refer to the same thing.

With these optimisations, the rule is faster by several orders of magnitude.

Since properties whose objects can be marked as deleted must be handled differently
to properties whose objects cannot be marked as deleted, the ``knora-base`` ontology
provides a property called ``objectCannotBeMarkedAsDeleted``. All properties in
``knora-base`` whose objects cannot take the ``isDeleted`` flag (including datatype
properties) should be derived from this property. This is how it is used to check
``owl:maxCardinality 1`` for objects that cannot be marked as deleted:

::

    Consistency: max_cardinality_1_without_deletion_flag
        i p r [Context <onto:_maxCardinality_1_table>]
        p <rdfs:subPropertyOf> <knora-base:objectCannotBeMarkedAsDeleted>
        i p j [Constraint j != k]
        i p k [Cut]
        ------------------------------------

To check ``owl:minCardinality 1``, we do not care whether the object can
be marked as deleted, so we can use this simple rule:

::

    Consistency: min_cardinality_1_any_object
        i p r [Context <onto:_minCardinality_1_table>]
        ------------------------------------
        i p j

This means: if a subject ``i`` belongs to a class that has
``owl:minCardinality 1`` on property ``p``, and ``i`` has no object for ``p``,
the rule is violated.

To check ``owl:cardinality 1``, we need two rules: one that checks whether
there are too few objects, and one that checks whether there are too many.
To check whether there are too few objects, we don't care whether the objects
can be marked as deleted, so the rule is the same as
``min_cardinality_1_any_object``, except for the cardinality:

::

    Consistency: cardinality_1_not_less_any_object
        i p r [Context <onto:_cardinality_1_table>]
        ------------------------------------
        i p j

To check whether there are too many objects, we need to know whether
the objects can be marked as deleted or not. In the case where the objects
can be marked as deleted, the rule is the same as
``max_cardinality_1_with_deletion_flag``, except for the cardinality:

::

    Consistency: cardinality_1_not_greater_with_deletion_flag
        i p r [Context <onto:_cardinality_1_table>]
        i p j [Constraint j != k]
        i p k [Cut]
        j <knora-base:isDeleted> "false"^^xsd:boolean
        k <knora-base:isDeleted> "false"^^xsd:boolean
        ------------------------------------

In the case where the objects cannot be marked as deleted, the rule is the
same as ``max_cardinality_1_without_deletion_flag``, except for the
cardinality:

::

    Consistency: cardinality_1_not_less_any_object
        i p r [Context <onto:_cardinality_1_table>]
        ------------------------------------
        i p j


Knora allows a subproperty of ``knora-base:hasValue`` or
``knora-base:hasLinkTo`` to be a predicate of a resource only if the resource's
class has some cardinality for the property. For convenience,
``knora-base:hasValue`` and ``knora-base:hasLinkTo`` are subproperties of
``knora-base:resourceProperty``, which is used to check this constraint in the
following rule:

::

    Consistency: resource_prop_cardinality_any
        i <knora-base:resourceProperty> j
        ------------------------------------
        i p j
        i <rdf:type> r
        r <owl:onProperty> p

If resource ``i`` has a subproperty of ``knora-base:resourceProperty``,
and ``i`` is not a member of a subclass of an ``owl:Restriction`` ``r``
with a cardinality on that property (or on one of its base
properties), the rule is violated.

A similar rule, ``value_prop_cardinality_any``, ensures that if a value has
a subproperty of ``knora-base:valueHas``, the value's class has some cardinality
for that property.


.. _Ontotext GraphDB: https://ontotext.com/products/graphdb/
.. _OWL 2 Quick Reference Guide: https://www.w3.org/TR/owl2-quick-reference/
.. _Reasoning: http://graphdb.ontotext.com/documentation/standard/reasoning.html
.. _Rules: http://graphdb.ontotext.com/documentation/standard/reasoning.html#rules
.. _Entailment rules: http://graphdb.ontotext.com/documentation/standard/reasoning.html#entailment-rules
.. _Forward-chaining: http://graphdb.ontotext.com/documentation/standard/introduction-to-semantic-web.html#reasoning-strategies
