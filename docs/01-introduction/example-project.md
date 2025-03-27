# An Example Project

This section introduces some of the basic concepts involved in creating
ontologies for DSP projects, by means of a relatively simple example
project. Before reading this document, it will be helpful to have some
familiarity with the basic concepts explained in knora-base.

DSP-API comes with two example projects, called `incunabula` and
`images-demo`. Here we will consider the `incunabula` example, which is
a reduced version of a real research project on early printed books. It
is designed to store an image of each page of each book, as well as RDF
data about books, pages, their contents, and relationships between them.

## The Incunabula Ontology

Here we will just focus on some of the main aspects of the ontology. An
ontology file typically begins by defining prefixes for the IRIs of
other ontologies that will be referred to. First there are some prefixes
for ontologies that are very commonly used in RDF:

```
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix owl: <http://www.w3.org/2002/07/owl#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix foaf: <http://xmlns.com/foaf/0.1/> .
@prefix dcterms: <http://purl.org/dc/terms/> .
```

The `rdf`, `rdfs`, and `owl` ontologies contain basic properties that
are used to define ontology entities. The `xsd` ontology contains
definitions of literal data types such as `string` and `integer`. (For
more information about these ontologies, see the references in
knora-base.) The `foaf` ontology contains classes and properties for
representing people. The `dcterms` ontology represents [Dublin
Core](http://dublincore.org/) metadata.

Then we define prefixes for DSP ontologies:

```
@prefix knora-base: <http://www.knora.org/ontology/knora-base#> .
@prefix salsah-gui: <http://www.knora.org/ontology/salsah-gui#> .
```

The `knora-base` ontology contains DSP-API's core abstractions, and is
described in knora-base. The `salsah-gui` ontology includes properties
that DSP projects must use to enable SALSAH, DSP-API's generic virtual
research environment.

For convenience, we can use the empty prefix to refer to the
`incunabula` ontology itself:

```
@prefix : <http://www.knora.org/ontology/0803/incunabula#> .
```

However, outside the ontology file, it would make more sense to define
an `incunabula` prefix to refer to the `incunabula` ontology.

### Properties

All the content produced by a DSP project must be stored in Knora
resources (see [incunabula-resource-classes](#resource-classes)). Resources have properties
that point to different parts of their contents; for example, the
`incunabula` project contains books, which have properties like `title`.
Every property that poitns to a DSP value must be a subproperty of
`knora-base:hasValue`, and every property that points to another Knora
resource must be a subproperty of `knora-base:hasLinkTo`.

Here is the definition of the `incunabula:title` property:

```
:title rdf:type owl:ObjectProperty ;

  rdfs:subPropertyOf knora-base:hasValue, dcterms:title ;

  rdfs:label "Titel"@de ,
    "Titre"@fr ,
    "Titolo"@it ,
    "Title"@en ;

  knora-base:subjectClassConstraint :book ;

  knora-base:objectClassConstraint knora-base:TextValue ;

  salsah-gui:guiElement salsah-gui:SimpleText ;

  salsah-gui:guiAttribute "size=80" ,
    "maxlength=255" .
```

The definition of `incunabula:title` consists of a list of triples, all
of which have `:title` as their subject. To avoid repeating `:title` for
each triple, Turtle syntax allows us to use a semicolon (`;`) to
separate triples that have the same subject. Moreover, some triples also
have the same predicate; a comma (`,`) is used to avoid repeating the
predicate. The definition of `:title` says:

* `rdf:type owl:ObjectProperty`: It is an `owl:ObjectProperty`. There are
  two kinds of OWL properties: object properties and datatype properties.
  Object properties point to objects, which have IRIs and
  can have their own properties. Datatype properties point to literal
  values, such as strings and integers.
* `rdfs:subPropertyOf knora-base:hasValue, dcterms:title`: It is a
  subproperty of `knora-base:hasValue` and `dcterms:title`. Since the
  objects of this property will be Knora values, it must be a
  subproperty of `knora-base:hasValue`. To facilitate searches, we
  have also chosen to make it a subproperty of `dcterms:title`. In the
  DSP-API v2, if you do a search for resources that have a certain
  `dcterms:title`, and there is a resource with a matching
  `incunabula:title`, the search results could include that resource.
* `rdfs:label "Titel"@de`, etc.: It has the specified labels in
  various languages. These are needed, for example, by user
  interfaces, to prompt the user to enter a value.
* `knora-base:subjectClassConstraint :book`: The subject of the
  property must be an `incunabula:book`.
* `knora-base:objectClassConstraint knora-base:TextValue`: The object
  of this property must be a `knora-base:TextValue` (which is a
  subclass of `knora-base:Value`).
* `salsah-gui:guiElement salsah-gui:SimpleText`: When SALSAH asks a
  user to enter a value for this property, it should use a simple text
  field.
* `salsah-gui:guiAttribute "size=80" , "maxlength=255"`: The SALSAH
  text field for entering a value for this property should be 80
  characters wide, and should accept at most 255 characters.

The `incunabula` ontology contains several other property definitions
that are basically similar. Note that different subclasses of `Value`
are used. For example, `incunabula:pubdate`, which represents the
publication date of a book, points to a `knora-base:DateValue`. The
`DateValue` class stores a date range, with a specified degree of
precision and a preferred calendar system for display.

A property can point to a Knora resource instead of to a Knora value.
For example, in the `incunabula` ontology, there are resources
representing pages and books, and each page is part of some book. This
relationship is expressed using the property `incunabula:partOf`:

```
:partOf rdf:type owl:ObjectProperty ;

  rdfs:subPropertyOf knora-base:isPartOf ;

  rdfs:label "ist ein Teil von"@de ,
    "est un part de"@fr ,
    "e una parte di"@it ,
    "is a part of"@en ;

  rdfs:comment """Diese Property bezeichnet eine Verbindung zu einer anderen Resource, in dem ausgesagt wird, dass die vorliegende Resource ein integraler Teil der anderen Resource ist. Zum Beispiel ist eine Buchseite ein integraler Bestandteil genau eines Buches."""@de ;

  knora-base:subjectClassConstraint :page ;

  knora-base:objectClassConstraint :book ;

  salsah-gui:guiElement salsah-gui:Searchbox .
```

The key things to notice here are:

* `rdfs:subPropertyOf knora-base:isPartOf`: The `knora-base` ontology provides a generic `isPartOf` property to express
  part-whole relationships. A project may use `knora-base:isPartOf` directly, however creating a subproperty such as
  `incunabula:partOf` will allow to customize the property further, e.g. by giving it a more descriptive label.  
  It is important to note that `knora-base:isPartOf` is a subproperty of `knora-base:hasLinkTo`. Any property that
  points to a `knora-base:Resource` must be a subproperty of `knora-base:hasLinkTo`. Such a
  property is called a *link property*.
* `knora-base:objectClassConstraint :book`: The object of this property must be a member of the class `incunabula:book`,
  which, as we will see below, is a subclass of `knora-base:Resource`.
* `salsah-gui:guiElement salsah-gui:Searchbox`: When SALSAH prompts a user to select the book that a page is part of, it
  should provide a search box enabling the user to find the desired book.

Because `incunabula:partOf` is a link property, it must always
accompanied by a *link value property*, which enables Knora to store
metadata about each link that is created with the link property. This
metadata includes the date and time when the link was created, its
owner, the permissions it grants, and whether it has been deleted.
Storing this metadata allows Knora to authorise users to see or modify
the link, as well as to query a previous state of a repository in which
a deleted link had not yet been deleted. (The ability to query previous
states of a repository is planned for DSP-API version 2.)

The name of a link property and its link value property must be related
by the following naming convention: to determine the name of the link
value property, add the word `Value` to the name of the link property.
Hence, the `incunabula` ontology defines the property `partOfValue`:

```
:partOfValue rdf:type owl:ObjectProperty ;

  rdfs:subPropertyOf knora-base:isPartOfValue ;

  knora-base:subjectClassConstraint :page ;

  knora-base:objectClassConstraint knora-base:LinkValue .
```

As a link value property, `incunabula:partOfValue` must point to a
`knora-base:LinkValue`. The `LinkValue` class is an RDF *reification* of
a triple (in this case, the triple that links a page to a book). For
more details about this, see knora-base-linkvalue.

Note that the property `incunabula:hasAuthor` points to a
`knora-base:TextValue`, because the `incunabula` project represents
authors simply by their names. A more complex project could represent
each author as a resource, in which case `incunabula:hasAuthor` would
need to be a subproperty of `knora-base:hasLinkTo`.

### Resource Classes

The two main resource classes in the `incunabula` ontology are `book`
and `page`. Here is `incunabula:book`:

```
:book rdf:type owl:Class ;

  rdfs:subClassOf knora-base:Resource , [
    rdf:type owl:Restriction ;
    owl:onProperty :title ;
    owl:minCardinality "1"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "1"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :hasAuthor ;
    owl:minCardinality "0"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "2"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :publisher ;
    owl:minCardinality "0"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "3"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :publoc ;
    owl:maxCardinality "1"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "4"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :pubdate ;
    owl:maxCardinality "1"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "5"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :location ;
    owl:maxCardinality "1"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "6"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :url ;
    owl:maxCardinality "1"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "7"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :description ;
    owl:maxCardinality "1"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "2"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :physical_desc ;
    owl:maxCardinality "1"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "9"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :note ;
    owl:minCardinality "0"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "10"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :citation ;
    owl:minCardinality "0"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "5"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :book_comment ;
    owl:minCardinality "0"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "12"^^xsd:nonNegativeInteger
  ] ;

  knora-base:resourceIcon "book.gif" ;

  rdfs:label "Buch"@de ,
    "Livre"@fr ,
    "Libro"@it ,
    "Book"@en ;

  rdfs:comment """Diese Resource-Klasse beschreibt ein Buch"""@de .
```

Like every Knora resource class, `incunabula:book` is a subclass of
`knora-base:Resource`. It is also a subclass of a number of other
classes of type `owl:Restriction`, which are defined in square brackets,
using Turtle's syntax for anonymous blank nodes. Each `owl:Restriction`
specifies a cardinality for a property that is allowed in resources of
type `incunabula:book`. A cardinality is indeed a kind of restriction:
it means that a resource of this type may have, or must have, a certain
number of instances of the specified property. For example,
`incunabula:book` has cardinalities saying that a book must have at
least one title and at most one publication date. In the DSP-API
version 1, the word 'occurrence' is used instead of 'cardinality'.

The OWL cardinalities supported by Knora are described in
[OWL Cardinalities](../02-dsp-ontologies/knora-base.md#owl-cardinalities).

Note that `incunabula:book` specifies a cardinality of
`owl:minCardinality 0` on the property `incunabula:hasAuthor`. At first
glance, this might seem as if it serves no purpose, since it says that
the property is optional and can have any number of instances. You may
be wondering whether this cardinality could simply be omitted from the
definition of `incunabula:book`. However, Knora requires every property
of a resource to have some cardinality in the resource's class. This is
because Knora uses the cardinalities to determine which properties are
*possible* for instances of the class, and the DSP-API relies on this
information. If there was no cardinality for `incunabula:hasAuthor`,
Knora would not allow a book to have an author.

Each `owl:Restriction` specifying a cardinality can include the predicate
`salsah-gui:guiOrder`, which tells the SALSAH GUI the order the properties
should be displayed in.

Here is the definition of `incunabula:page`:

```
:page rdf:type owl:Class ;

  rdfs:subClassOf knora-base:StillImageRepresentation , [
    rdf:type owl:Restriction ;
    owl:onProperty :pagenum ;
    owl:maxCardinality "1"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "1"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :partOfValue ;
    owl:cardinality "1"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "2"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :partOf ;
    owl:cardinality "1"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "2"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :seqnum ;
    owl:maxCardinality "1"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "3"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :description ;
    owl:maxCardinality "1"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "2"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :citation ;
    owl:minCardinality "0"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "5"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :page_comment ;
    owl:minCardinality "0"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "6"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :origname ;
    owl:cardinality "1"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "7"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :hasLeftSidebandValue ;
    owl:maxCardinality "1"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "10"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :hasLeftSideband ;
    owl:maxCardinality "1"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "10"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :hasRightSidebandValue ;
    owl:maxCardinality "1"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "11"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :hasRightSideband ;
    owl:maxCardinality "1"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "11"^^xsd:nonNegativeInteger
  ] , [
    rdf:type owl:Restriction ;
    owl:onProperty :transcription ;
    owl:minCardinality "0"^^xsd:nonNegativeInteger ;
    salsah-gui:guiOrder "12"^^xsd:nonNegativeInteger
  ] ;

  knora-base:resourceIcon "page.gif" ;

  rdfs:label "Seite"@de ,
    "Page"@fr ,
    "Page"@en ;

  rdfs:comment """Eine Seite ist ein Teil eines Buchs"""@de ,
    """Une page est une partie d'un livre"""@fr ,
    """A page is a part of a book"""@en .
```

The `incunabula:page` class is a subclass of
`knora-base:StillImageRepresentation`, which is a subclass of
`knora-base:Representation`, which is a subclass of
`knora-base:Resource`. The class `knora-base:Representation` is used for
resources that contain metadata about files stored by Knora. Each It has
different subclasses that can hold different types of files, including
still images, audio, and video files. A given `Representation` can store
metadata about several different files, as long as they are of the same
type and are semantically equivalent, e.g. are different versions of the
same image with different colorspaces, so that coordinates in one file
will work in the other files.

In Knora, a subclass inherits the cardinalities defined in its
superclasses. Let's look at the class hierarchy of `incunabula:page`,
starting with `knora-base:Representation`:

```
:Representation rdf:type owl:Class ;

  rdfs:subClassOf :Resource , [
    rdf:type owl:Restriction ;
    owl:onProperty :hasFileValue ;
    owl:minCardinality "1"^^xsd:nonNegativeInteger
  ] ;

  rdfs:comment "A resource that can store one or more FileValues"@en .
```

This says that a `Representation` must have at least one instance of the
property `hasFileValue`, which is defined like this:

```
:hasFileValue rdf:type owl:ObjectProperty ;

  rdfs:subPropertyOf :hasValue ;

  :subjectClassConstraint :Representation ;

  :objectClassConstraint :FileValue .              
```

The subject of `hasFileValue` must be a `Representation`, and its object
must be a `FileValue`. There are different subclasses of `FileValue` for
different kinds of files, but we'll skip the details here.

This is the definition of `knora-base:StillImageRepresentation`:

```
:StillImageRepresentation rdf:type owl:Class ;

  rdfs:subClassOf :Representation , [
    rdf:type owl:Restriction ;
    owl:onProperty :hasStillImageFileValue ;
    owl:minCardinality "1"^^xsd:nonNegativeInteger
  ] ;

  rdfs:comment "A resource that can contain two-dimensional still image files"@en .
```

It must have at least one instance of the property
`hasStillImageFileValue`, which is defined as follows:

```
:hasStillImageFileValue rdf:type owl:ObjectProperty ;

  rdfs:subPropertyOf :hasFileValue ;

  :subjectClassConstraint :StillImageRepresentation ;

  :objectClassConstraint :AbstractStillImageFileValue .              
```

Because `hasStillImageFileValue` is a subproperty of `hasFileValue`, the
cardinality on `hasStillImageFileValue`, defined in the subclass
`StillImageRepresentation`, overrides the cardinality on `hasFileValue`,
defined in the superclass `Representation`. In other words, the more
general cardinality in the superclass is replaced by a more specific
cardinality in the base class. Since `incunabula:page` is a subclass of
`StillImageRepresentation`, it inherits the cardinality on
`hasStillImageFileValue`. As a result, a page must have at least one
image file value attached to it.

Here's another example of cardinality inheritance. The class
`knora-base:Resource` has a cardinality for `knora-base:seqnum`. The
idea is that resources of any type could be arranged in some sort of
sequence. As we saw above, `incunabula:page` is a subclass of
`knora-base:Resource`. But `incunabula:page` has its own cardinality for
`incunabula:seqnum`, which is a subproperty of `knora-base:seqnum`. Once
again, the subclass's cardinality on the subproperty replaces the
superclass's cardinality on the superproperty: a page is allowed to have
an `incunabula:seqnum`, but it is not allowed to have a
`knora-base:seqnum`.
