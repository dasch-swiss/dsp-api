<!---
Copyright © 2015-2018 the contributors (see Contributors.md).

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
-->

# Introduction

@@toc { depth=2 }

## Resource Description Framework (RDF)

Knora uses a hierarchy of ontologies based on the Resource Description
Framework
([RDF](http://www.w3.org/TR/2014/NOTE-rdf11-primer-20140624/)), RDF
Schema ([RDFS](http://www.w3.org/TR/2014/REC-rdf-schema-20140225/)), and
the Web Ontology Language
([OWL](https://www.w3.org/TR/owl2-quick-reference/)). Both RDFS and OWL
are expressed in RDF. RDF expresses information as a set of statements
(called *triples*). A triple consists of a subject, a predicate, and an
object:

![Figure 1](introduction-fig1.dot.png "Figure 1")

The object may be either a literal value (such as a name or number) or
another subject. Thus it is possible to create complex graphs that
connect many subjects, like this:

![Figure 2](introduction-fig2.dot.png "Figure 2")

In RDF, each subject and predicate has a unique, URL-like identifier
called an Internationalized Resource Identifier
(@extref[IRI](rfc:3987)). Within a given project,
IRIs typically differ only in their last component (the "local part"),
which is often the fragment following a `#` character. Such IRIs share a
long "prefix". In [Turtle](http://www.w3.org/TR/turtle/) and similar
formats for writing RDF, a short prefix label can be defined to
represent the long prefix. Then an IRI can be written as a prefix label
and a local part, separated by a colon (`:`). For example, if the
"example" project's long prefix is `http://www.example.org/rdf#`, and it
contains subjects with IRIs like `http://www.example.org/rdf#book`, we
can define the prefix label `ex` to represent the prefix label, and
write prefixed names for IRIs:

![Figure 3](introduction-fig3.dot.png "Figure 3")

## Built-in Ontologies and User-Created Ontologies

To ensure the interoperability of data produced by different projects,
each project must describe its data model by creating one or more ontologies that
extend Knora's built-in ontologies. The main built-in ontology in Knora
is @ref:[knora-base](knora-base.md).

## Shared Ontologies

Knora does not normally allow a project to use classes or properties defined in
an ontology that belongs to another project. Each project must be free to change
its own ontologies, but this is not possible if they have been used in ontologies
or data created by other projects.

However, an ontology can be defined as shared, meaning that it can be used by
multiple projects, and that its creators will not change it in ways that could
affect other ontologies or data that are based on it. Specifically, in a shared
ontology, existing classes and properties cannot safely be changed, but new ones
can be added. (It is not even safe to add an optional cardinality to an existing
class, because this could cause subclasses to violate the rule that a class cannot
have a cardinality on property P as well as a cardinality on a subproperty of P;
see @ref:[Restrictions on Classes](knora-base.md#restrictions-on-classes).)

A standardisation process for shared ontologies is planned (issue @github[#523](#523)).

For more details about shared ontologies, see
@ref:[Shared Ontology IRIs](../03-apis/api-v2/knora-iris.md#shared-ontology-iris).
