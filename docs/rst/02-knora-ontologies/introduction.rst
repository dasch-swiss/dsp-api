.. Copyright © 2015-2018 the contributors (see Contributors.md).

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

.. _knora-ontologies-intro:

************
Introduction
************

.. contents:: :local:

Resource Description Framework (RDF)
====================================

Knora uses a hierarchy of ontologies based on the Resource Description
Framework (RDF_), RDF Schema (RDFS_), and the Web Ontology Language (OWL_).
Both RDFS and OWL are expressed in RDF. RDF expresses information as a set of
statements (called *triples*). A triple consists of a subject, a predicate,
and an object:

.. graphviz::

   digraph {
       rankdir = LR

       node [style = filled, fontcolor = white]

       subject [color = navy, fillcolor = slateblue4]
       object [color = tomato3, fillcolor = tomato2]

       subject -> object [label = "predicate", fontsize = 11, color = cyan4]
   }

The object may be either a literal value (such as a name or number) or
another subject. Thus it is possible to create complex graphs that
connect many subjects, like this:

.. graphviz::

   digraph {
       rankdir = LR

       {
           node [color = navy, fillcolor = slateblue4, style = filled, fontcolor = white]

           sub1 [label = "subject no. 1"]
           sub2 [label = "subject no. 2"]
           sub3 [label = "subject no. 3"]
       }

       {
           node [shape = box, color = firebrick]

           lit1 [label = "literal no. 1"]
           lit2 [label = "literal no. 2"]
           lit3 [label = "literal no. 3"]
       }

       edge [fontsize = 11, color = cyan4]

       sub1 -> lit1 [label = "predicate no. 1"]
       sub1 -> lit2 [label = "predicate no. 2"]
       sub1 -> sub2 [label = "predicate no. 3"]

       sub2 -> lit3 [label = "predicate no. 4"]
       sub2 -> sub3 [label = "predicate no. 5"]

       // Add invisible edges to order the nodes from top to bottom.

       {
           rank = same
           lit1 -> lit2 -> sub2 [style = invis]
           rankdir = TB
       }

       {
           rank = same
           lit3 -> sub3 [style = invis]
           rankdir = TB
       }
   }

In RDF, each subject and predicate has a unique, URL-like identifier
called an Internationalized Resource Identifier (IRI_). Within a given project,
IRIs typically differ only in their last component (the “local part”), which
is often the fragment following a ``#`` character. Such IRIs share a
long “prefix”. In Turtle_ and similar formats for
writing RDF, a short prefix label can be defined to represent the long
prefix. Then an IRI can be written as a prefix label and a local part,
separated by a colon (``:``). For example, if the “example” project’s
long prefix is ``http://www.example.org/rdf#``, and it contains subjects
with IRIs like ``http://www.example.org/rdf#book``, we can define the
prefix label ``ex`` to represent the prefix label, and write prefixed
names for IRIs:

.. graphviz::

   digraph {
       {
           node [color = navy, fillcolor = slateblue4, style = filled, fontcolor = white]

           book [label = "ex:book1"]
           page [label = "ex:page1"]
       }

       {
           node [shape = box, color = firebrick]

           title [label = "‘Das Narrenschiff’"]
           author [label = "‘Sebastian Brant’"]
           pagename [label = "‘a4r’"]
       }

       edge [fontsize = 11, color = cyan4]

       book -> title [label = "ex:title"]
       book -> author [label = "ex:author"]
       page -> book [label = "ex:pageOf"]
       page -> pagename [label = "ex:pagename"]
    }


Built-in Ontologies and Project-specific Ontologies
===================================================

To ensure the interoperability of data produced by different projects, each project must
describe its data model by creating ontologies that extend Knora's built-in ontologies.
The main built-in ontology in Knora is :ref:`knora-base`.

.. _Turtle: http://www.w3.org/TR/turtle/

.. _RDFS: http://www.w3.org/TR/2014/REC-rdf-schema-20140225/

.. _OWL: https://www.w3.org/TR/owl2-quick-reference/

.. _IRI: http://tools.ietf.org/html/rfc3987

.. _RDF: http://www.w3.org/TR/2014/NOTE-rdf11-primer-20140624/
