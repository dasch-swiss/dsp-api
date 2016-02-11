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

Plans for Knora API v2
=======================

Naming
-------

In API v1, the same data types are named inconsistently (``resinfo``,
``res_info``) or unclearly (``value_restype`` is actually a label).
Version 2 should adopt a clear, consistent naming convention.

Structure
----------

API v1 sometimes uses parallel array structures to represent multiple
complex objects, e.g. the values of resource properties or the items in
a resource's context. Version 2 should use nested structures instead.

Redundancy
-----------

Some information in API v1 is presented redundantly, e.g. ``resinfo``
and ``resdata``, and the ``__location__`` property. This should be
cleaned up.

Efficiency
------------

Some queries, like the resource context query, produce so much data that
they cannot be made efficient. We should consider breaking up these API
calls into smaller chunks.

It should be possible to customise GET requests so that they return only
as much data as the user wants. For example, in Fedora 4's equivalent of
the ``resources`` route with ``reqtype=full``, you can specify whether
you want child resources and incoming references. This would enable
clients to request only the information they actually need, improving
performance and reducing server load.

Suitability for non-GUI applications
-------------------------------------

When returning the 'full' information about a resource, the API currently
includes valueless properties to reflect the possible properties in the
resource type (if a property has no value, or only has values that the user
isn't allowed to see), *unless* a property already has a value that the user
isn't allowed to see, and its cardinality is ``MustHaveOne`` or
``MayHaveOne``. This makes sense from the point of a GUI: the valueless
properties are there to indicate that the user could add values for those
properties. If a property already has a value and its cardinality is
``MustHaveOne`` or ``MayHaveOne``, the user can't add a value for it, so there
is no reason to include it.

In version 2, it might make more sense to separate information about
resource types from information about resources (rather than mixing
these two kinds of information together in one API response), and to
separate *displaying* a resource from indicating which properties a
particular user can add.

Working with multiple projects
-------------------------------

The user will be able to choose which project to use for an update.

We will handle the case where Project A defines a resource class X, and
Project B declares a resource class Y with additional properties,
asserting that X is a subclass of Y, so that users in Project B can add
these extra properties to resources that already exist in Project A.
Users in project A will want to be able to ignore the extra properties
from Y, or optionally see and use them.

The ontology responder will distinguish between definitions in the
active project's named graph and definitions added elsewhere, so the
user can choose to see just what's defined in their own project or to
include definitions from elsewhere.

Annotating values
------------------

In API v1, only resources can be annotated. In v2, it will also be
possible to annotate values.

Typing
--------

Each data item should have a consistent data type. In an JSON object,
the same name should always contain a value of the same type. Numbers
should be represented as numbers rather than as strings.

JSON-LD
--------

Consider using `JSON-LD`_ to specify data types
and semantics within API responses, instead of providing separate JSON
schemas.

The basic idea is just that your API can return JSON like this:

::

    {
      "book": {
        "id": "http://data.knora.org/c5058f3a"
        "title": "Zeitglöcklein des Lebens und Leidens Christi"
      }
    }

and it can also include a "context" (which can be embedded in the same
JSON, or provided as the URL of a separate JSON document) specifying
that ``book`` is an ``incunabula:book``, and that ``title`` means
``dc:title``. So everything in an API response can have semantics and
type information specified. The idea is that the keys in the JSON stay
short and readable, so someone writing a simple browser-based client can
write ``book.title`` in JavaScript and it will work. At the same time, a
more complex, automated client can easily get the semantic and type
information.


.. _JSON-LD: http://json-ld.org/
