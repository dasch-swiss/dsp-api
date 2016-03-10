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

****************************
A Sample Project: Incunabula
****************************

Before reading this document, it will be helpful to have some familiarity with
the basic concepts explained in :download:`The Knora Base Ontology </latex/knora-base/knora-base.pdf>`.

Knora comes with two sample projects, called ``incunabula`` and ``images-demo``.
Here we will consider the ``incunabula`` sample to illustrate some
typical elements of the ontology and data of a Knora project. This sample
project is a reduced version of a real research project on early printed books
called *incunabula*. It contains an image of each page of each book, as well
as RDF data about books, pages, their contents, and relationships between
them. At the moment, only the RDF data is provided in the sample project, not
the images.

The ``incunabula`` ontology is in the file ``incunabula-onto.ttl``, and its
data is in the file ``incunabula-data.ttl``. Both these files are in a
standard RDF file format called Turtle_. The Knora distribution includes
sample scripts (in the ``webapi/scripts`` directory) for importing these files
directly into different triplestores. If you are starting a new project from
scratch, you can adapt these scripts to import your ontology (and any existing
RDF data) into your triplestore for use with Knora.

The syntax of Turtle is fairly simple: it is basically a sequence of triples.
We will consider some details of Turtle syntax as we go along.

The Incunabula Ontology
-----------------------

Here we will just focus on some of the main aspects of the ontology. An
ontology file typically begins by defining prefixes for the IRIs of other
ontologies that will be referred to. First there are some prefixes for
ontologies that are very commonly used in RDF:

::

    @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
    @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
    @prefix owl: <http://www.w3.org/2002/07/owl#> .
    @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
    @prefix foaf: <http://xmlns.com/foaf/0.1/> .

The ``rdf``, ``rdfs``, and ``owl`` ontologies contain basic properties that
are used to define ontology entities. The ``xsd`` ontology contains
definitions of literal data types such as ``string`` and ``integer``. The
``foaf`` ontology contains classes and properties for representing people.

Then we define prefixes for Knora ontologies:

::

    @prefix knora-base: <http://www.knora.org/ontology/knora-base#> .
    @prefix dc: <http://www.knora.org/ontology/dc#> .
    @prefix salsah-gui: <http://www.knora.org/ontology/salsah-gui#> .

The ``knora-base`` ontology contains Knora's core abstractions, and is
described in the document
:download:`The Knora Base Ontology </latex/knora-base/knora-base.pdf>`.
The ``dc`` ontology is Knora's version of `Dublin
Core`_. It is intended to make it possible to define properties in a Knora
project in terms of Dublin Core abstractions, to facilitate queries that
search for data across multiple projects. The ``salsah-gui`` ontology includes
properties that Knora projects must use to enable SALSAH, Knora's generic
virtual research environment.

For convenience, we can use the empty prefix to refer to the ``incunabula``
ontology itself:

::

    @prefix : <http://www.knora.org/ontology/incunabula#> .

However, outside the ontology file, it would make more sense to define an
``incunabula`` prefix to refer to the ``incunabula`` ontology.

Properties
^^^^^^^^^^

Now we need some RDF property definitions. The project contains books, which
have properties like ``title``. Here are some of the main parts of the
definition of the ``title`` property:

::

    :title rdf:type owl:ObjectProperty ;

           rdfs:subPropertyOf dc:title ;

           rdfs:label "Titel"@de ,
               "Titre"@fr ,
               "Titolo"@it ,
               "Title"@en ;

           knora-base:subjectClassConstraint :book ;

           knora-base:objectClassConstraint knora-base:TextValue ;

           salsah-gui:guiOrder "1"^^xsd:integer ;

           salsah-gui:guiElement salsah-gui:SimpleText ;

           salsah-gui:guiAttribute "size=80" ,
               "maxlength=255" .

The definition of ``incunabula:title`` consists of a list of triples, all of
which have ``:title`` as their subject. To avoid repeating ``:title`` for each
triple, Turtle syntax allows us to use a semicolon (``;``) to separate triples
that have the same subject. Moreover, some triples also have the same
predicate; a comma (``,``) is used to avoid repeating the predicate. The
definition of ``:title`` says:

* ``rdf:type owl:ObjectProperty``: It is an ``owl:ObjectProperty``. There are
  two kinds of OWL properties: object properties and datatype properties.
  Object properties point to objects, which have IRIs and can have their own
  properties. Datatype properties point to literal values, such as strings and
  integers.
* ``rdfs:subPropertyOf dc:title``: It is a subproperty of ``dc:title``, so if
  you do a search for resources that have a certain ``dc:title``, and there is
  a resource with a matching ``incunabula:title``, the search results could
  include that resource. (This feature is planned but not yet implemented in
  the Knora API server.) It is important to note that ``dc:title`` is a
  subproperty of ``knora-base:hasValue``. It would have been possible to
  define ``incunabula:title`` as a direct subproperty of ``knora-
  base:hasValue``, and indeed many properties in Knora projects are defined in
  that way. Any property that points to a ``knora-base:Value`` must be a
  subproperty of ``knora-base:hasValue``.
* ``rdfs:label "Titel"@de``, etc.: It has the specified labels in various
  languages. These are needed, for example, by user interfaces, to prompt the
  user to enter a value.
* ``knora-base:subjectClassConstraint :book``: The subject of the property
  must be an ``incunabula:book``.
* ``knora-base:objectClassConstraint knora-base:TextValue``: The object of
  this property must be a ``knora-base:TextValue`` (which is a subclass of
  ``knora-base:Value``).
* ``salsah-gui:guiOrder "1"^^xsd:integer``: When a resource with this and
  other properties is displayed in SALSAH, this property will be displayed
  first. The notation ``"1"^^xsd:integer`` means that the literal ``"1"`` is
  of type ``xsd:integer``.
* ``salsah-gui:guiElement salsah-gui:SimpleText``: When SALSAH asks a user to
  enter a value for this property, it should use a simple text field.
* ``salsah-gui:guiAttribute "size=80" , "maxlength=255"``: The SALSAH text
  field for entering a value for this property should be 80 characters wide,
  and should accept at most 255 characters.

The ``incunabula`` ontology contains several property definitions that are
basically similar. Note that different subclasses of ``Value`` are used. For
example, ``incunabula:pubdate``, which represents the publication date of a
book, points to a ``knora-base:DateValue``. The ``DateValue`` class stores a
date range, with a specified degree of precision and a preferred calendar
system for display.

A Knora property can point to a Knora resource instead of a Knora value. For
example, in the ``incunabula`` ontology, there are resources representing
pages and books, and each page is part of some book. This relationship is
expressed using the property ``incunabula:partOf``:

::

    :partOf rdf:type owl:ObjectProperty ;

            rdfs:subPropertyOf knora-base:isPartOf ;

            rdfs:label "ist ein Teil von"@de ,
                       "est un part de"@fr ,
                       "e una parte di"@it ,
                       "is a part of"@en ;

            rdfs:comment """Diese Property bezeichnet eine Verbindung zu einer anderen Resource, in dem ausgesagt wird, dass die vorliegende Resource ein integraler Teil der anderen Resource ist. Zum Beispiel ist eine Buchseite ein integraler Bestandteil genau eines Buches."""@de ;

            knora-base:subjectClassConstraint :page ;

            knora-base:objectClassConstraint :book ;

            salsah-gui:guiOrder "2"^^xsd:integer ;

            salsah-gui:guiElement salsah-gui:Searchbox .

The key things to notice here are:

* ``rdfs:subPropertyOf knora-base:isPartOf``: The Knora base ontology provides
  a generic ``isPartOf`` property to express part-whole relationships. Like
  many properties defined in ``knora-base``, a project cannot use ``knora-
  base:isPartOf`` directly, but must make a subproperty such as
  ``incunabula:partOf``.  It is important to note that ``knora-base:isPartOf``
  is a subproperty of ``knora-base:hasLinkTo``. Any property that points to a
  ``knora-base:Resource`` must be a subproperty of ``knora-base:hasLinkTo``.
  In Knora terminology, such a property is called a *link property*.
* ``knora-base:objectClassConstraint :book``: The object of this property must
  be a member of the class ``incunabula:book``, which, as we will see below,
  is a subclass of ``knora-base:Resource``.
* ``salsah-gui:guiElement salsah-gui:Searchbox``: When SALSAH prompts a user
  to select the book that a page is part of, it should provide a search box
  enabling the user to find the desired book.

Because ``incunabula:partOf`` is a link property, it must always accompanied
by a *link value property*, which enables Knora to store metadata about each
link that is created with the link property. This metadata includes the date
and time when the link was created, its owner, the permissions it grants, and
whether it has been deleted. Storing this metadata allows Knora to authorise
users to see or modify the link, as well as to query a previous state of a
repository in which a deleted link had not yet been deleted. (The ability to
query previous states of a repository is planned for Knora API version 2.)

The name of a link property and its link value property must be related by the
following naming convention: to find the name of the link value property, add
the word ``Value`` to the name of the link property. Hence, the ``incunabula``
ontology defines the property ``partOfValue``:

::

    :partOfValue rdf:type owl:ObjectProperty ;

                     rdfs:subPropertyOf knora-base:isPartOfValue ;

                     knora-base:subjectClassConstraint :page ;

                     knora-base:objectClassConstraint knora-base:LinkValue .

As a link value property, ``incunabula:partOfValue`` must point to a ``knora-
base:LinkValue``. The ``LinkValue`` class is an RDF *reification* of a triple
(in this case, the triple that links a page to a book). For more details about
this, see :download:`The Knora Base Ontology </latex/knora-base/knora-
base.pdf>`.




### ###########################################
### incunabula:book

:book rdf:type owl:Class ;

      rdfs:subClassOf knora-base:Resource ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :title ;
                         owl:minCardinality "1"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :hasAuthor ;
                         owl:minCardinality "0"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :publisher ;
                         owl:minCardinality "0"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :publoc ;
                         owl:maxCardinality "1"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :pubdate ;
                         owl:maxCardinality "1"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :location ;
                         owl:maxCardinality "1"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :url ;
                         owl:maxCardinality "1"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :description ;
                         owl:maxCardinality "1"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :physical_desc ;
                         owl:maxCardinality "1"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :note ;
                         owl:minCardinality "0"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :citation ;
                         owl:minCardinality "0"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :book_comment ;
                         owl:minCardinality "0"^^xsd:nonNegativeInteger                      ] ;

      knora-base:resourceIcon "book.gif" ;

      rdfs:label "Buch"@de ,
                 "Livre"@fr ,
                 "Libro"@it ,
                 "Book"@en ;

      rdfs:comment """Diese Resource-Klasse beschreibt ein Buch"""@de ;

      knora-base:hasDefaultRestrictedViewPermission knora-base:UnknownUser ;

      knora-base:hasDefaultViewPermission knora-base:KnownUser ;

      knora-base:hasDefaultModifyPermission knora-base:ProjectMember ,
                                            knora-base:Owner .


### ###########################################
### incunabula:page

:page rdf:type owl:Class ;

      rdfs:subClassOf knora-base:StillImageRepresentation ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :pagenum ;
                         owl:maxCardinality "1"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :partOfValue ;
                         owl:cardinality "1"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :partOf ;
                         owl:cardinality "1"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :seqnum ;
                         owl:maxCardinality "1"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :description ;
                         owl:maxCardinality "1"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :citation ;
                         owl:minCardinality "0"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :page_comment ;
                         owl:minCardinality "0"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :origname ;
                         owl:cardinality "1"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :hasLeftSidebandValue ;
                         owl:maxCardinality "1"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :hasLeftSideband ;
                         owl:maxCardinality "1"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :hasRightSidebandValue ;
                         owl:maxCardinality "1"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :hasRightSideband ;
                         owl:maxCardinality "1"^^xsd:nonNegativeInteger                      ] ,
                      [
                         rdf:type owl:Restriction ;
                         owl:onProperty :transcription ;
                         owl:minCardinality "0"^^xsd:nonNegativeInteger                      ] ;

      knora-base:resourceIcon "page.gif" ;

      rdfs:label "Seite"@de ,
                 "Page"@fr ,
                 "Page"@en ;

      rdfs:comment """Eine Seite ist ein Teil eines Buchs"""@de ,
                   """Une page est une partie d'un livre"""@fr ,
                   """A page is a part of a book"""@en ;

      knora-base:hasDefaultRestrictedViewPermission knora-base:UnknownUser ;

      knora-base:hasDefaultViewPermission knora-base:KnownUser ;

      knora-base:hasDefaultModifyPermission knora-base:ProjectMember ,
                                            knora-base:Owner .


### ###########################################
### incunabula:Sideband

:Sideband rdf:type owl:Class ;

          rdfs:subClassOf knora-base:StillImageRepresentation ,
                          [
                             rdf:type owl:Restriction ;
                             owl:onProperty :sbTitle ;
                             owl:cardinality "1"^^xsd:nonNegativeInteger                          ] ,
                          [
                             rdf:type owl:Restriction ;
                             owl:onProperty :description ;
                             owl:maxCardinality "1"^^xsd:nonNegativeInteger                          ] ,
                          [
                             rdf:type owl:Restriction ;
                             owl:onProperty :sideband_comment ;
                             owl:minCardinality "0"^^xsd:nonNegativeInteger                          ] ;

          rdfs:label "Randleiste"@de ;

          rdfs:comment """Randleistentyp"""@de ;

          knora-base:hasDefaultViewPermission knora-base:KnownUser ;

          knora-base:hasDefaultModifyPermission knora-base:ProjectMember ;

          knora-base:hasDefaultDeletePermission knora-base:Owner .


### ###########################################
### incunabula:misc

:misc rdf:type owl:Class ;

      rdfs:subClassOf knora-base:Resource,
                      [
                        rdf:type owl:Restriction ;
                        owl:onProperty :miscHasColor ;
                        owl:maxCardinality "1"^^xsd:nonNegativeInteger
                      ] ,
                      [
                        rdf:type owl:Restriction ;
                        owl:onProperty :miscHasGeometry ;
                        owl:maxCardinality "1"^^xsd:nonNegativeInteger
                      ],
                      [
                        rdf:type owl:Restriction ;
                        owl:onProperty :misHasBook ;
                        owl:maxCardinality "1"^^xsd:nonNegativeInteger
                      ] ;

      rdfs:label "Sonstiges"@de ;

      rdfs:comment "A fake resource class that only has optional properties"@en;

      knora-base:hasDefaultViewPermission knora-base:KnownUser ;

      knora-base:hasDefaultModifyPermission knora-base:ProjectMember ;

      knora-base:hasDefaultDeletePermission knora-base:Owner .



.. _Turtle: https://www.w3.org/TR/turtle/

.. _Dublin Core: http://dublincore.org/
.. 