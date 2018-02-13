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

.. _responders_module:


Responders Module
==================

Version 1.0 Responders
----------------------

ResponderManagerV1
^^^^^^^^^^^^^^^^^^

CkanResponderV1
^^^^^^^^^^^^^^^

HierarchicalListsResponderV1
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

OntologyResponderV1
^^^^^^^^^^^^^^^^^^^

The ontology responder provides information derived from all the ontologies in
the repository, including Knora ontologies as well as project-specific
ontologies. Most importantly, it provides information about resource classes
and properties. This includes the cardinalities defined on each resource
class, and takes into account the rules of cardinality inheritance, as
described in the section **OWL Cardinalities** in :ref:`knora-ontologies`.

For performance reasons, all ontology data is loaded and cached at application
startup. Currently, to refresh the cache, you must restart the application.
To maximise performance, the SPARQL queries used by the ontology responder
are very simple, and the responder calculates class hierarchies and cardinality
inheritance in Scala.

ProjectsResponderV1
^^^^^^^^^^^^^^^^^^^

RepresentationsResponderV1
^^^^^^^^^^^^^^^^^^^^^^^^^^

ResourcesResponderV1
^^^^^^^^^^^^^^^^^^^^

SearchResponderV1
^^^^^^^^^^^^^^^^^

UsersResponderV1
^^^^^^^^^^^^^^^^

ValuesResponderV1
^^^^^^^^^^^^^^^^^

Shared
^^^^^^

  * ResponderV1
  * ValueUtilV1
