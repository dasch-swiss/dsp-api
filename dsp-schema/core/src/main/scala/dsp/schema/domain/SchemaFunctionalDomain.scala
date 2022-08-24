/*
 * Copyright © 2021 - 2022 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.schema.domain

import zio.prelude.Validation

object SchemaDomain extends App {
  // implicitly["".type =:= "".type]

  type IRI         = String
  type UserID      = String
  type UserProfile = String
  type SchemaID    = String
  type Schema      = String

  final case class OntologyInfo(name: String, projectIri: IRI, label: String, comment: String)
  final case class OntologyClass[A <: Singleton with String](name: A, label: String, comment: String) { self =>
    type Tag = A

    override def equals(that: Any): Boolean =
      that match {
        case that: OntologyClass[_] => self.name == that.name
        case _                      => false
      }
    override def hashCode: Int = name.hashCode
  }
  final case class OntologyProperty[A <: Singleton with String](
    name: A,
    label: String,
    comment: String,
    range: String
  ) { self =>
    type Tag = A

    override def equals(that: Any): Boolean =
      that match {
        case that: OntologyProperty[_] => self.name == that.name
        case _                         => false
      }

    override def hashCode: Int = name.hashCode
  }

  trait Cardinality { self =>
    type ClassTag <: Singleton with String
    type PropertyTag <: Singleton with String

    def ontologyClass: OntologyClass[ClassTag]
    def ontologyProperty: OntologyProperty[PropertyTag]
    def cardinalityType: CardinalityType
  }
  object Cardinality {
    type WithTags[T1, T2] = Cardinality { type ClassTag = T1; type PropertyTag = T2 }

    def apply[A <: Singleton with String, B <: Singleton with String](
      oc: OntologyClass[A],
      op: OntologyProperty[B],
      ct: CardinalityType
    ): WithTags[oc.Tag, op.Tag] =
      new Cardinality {
        type ClassTag    = oc.Tag
        type PropertyTag = op.Tag

        val ontologyClass    = oc
        val ontologyProperty = op
        val cardinalityType  = ct
      }
  }

  sealed trait CardinalityType
  object CardinalityType {
    case object MaxCardinalityOne  extends CardinalityType
    case object MinCardinalityOne  extends CardinalityType
    case object MinCardinalityZero extends CardinalityType
  }

  sealed trait Ontology[Classes, Properties] { self =>

    def withClass[Tag <: Singleton with String](ontoClass: OntologyClass[Tag])(implicit
      ev: NotSubtypeOf[Classes, Tag]
    ): Ontology[Classes with Tag, Properties] =
      Ontology.WithClass[Classes, Properties, Tag](self, ontoClass)

    def withProperty[Tag <: Singleton with String](propertyInfo: OntologyProperty[Tag])(implicit
      ev: NotSubtypeOf[Properties, Tag]
    ): Ontology[Classes, Properties with Tag] =
      Ontology.WithProperty[Classes, Properties, Tag](self, propertyInfo)

    def withCardinality(
      cardinality: Cardinality
    )(implicit
      ev1: Classes <:< cardinality.ClassTag,
      ev2: Properties <:< cardinality.PropertyTag
    ): Ontology[Classes, Properties] =
      Ontology.WithCardinality[Classes, Properties, cardinality.ClassTag, cardinality.PropertyTag](
        self,
        cardinality
      )

    lazy val classes: Set[OntologyClass[_ <: Singleton with String]] =
      self match {
        case _: Ontology.Empty                         => Set.empty
        case Ontology.WithClass(ontology, singleClass) => Set(singleClass) ++ ontology.classes
        case Ontology.WithProperty(ontology, _)        => ontology.classes
        case Ontology.WithCardinality(ontology, _)     => ontology.classes
      }

    lazy val properties: Set[OntologyProperty[_ <: Singleton with String]] =
      self match {
        case _: Ontology.Empty                           => Set.empty
        case Ontology.WithClass(ontology, _)             => ontology.properties
        case Ontology.WithProperty(ontology, singleProp) => Set(singleProp) ++ ontology.properties
        case Ontology.WithCardinality(ontology, _)       => ontology.properties
      }

    lazy val cardinalities: Set[Cardinality] =
      self match {
        case _: Ontology.Empty                               => Set.empty
        case Ontology.WithClass(ontology, _)                 => ontology.cardinalities
        case Ontology.WithProperty(ontology, _)              => ontology.cardinalities
        case Ontology.WithCardinality(ontology, cardinality) => Set(cardinality) ++ ontology.cardinalities
      }

    def withClassV[Tag <: Singleton with String](
      ontoClass: OntologyClass[Tag]
    ): Validation[String, Ontology[Classes with Tag, Properties]] =
      if (classes.contains(ontoClass)) {
        Validation.fail(s"Class ${ontoClass.name} already exists in ontology")
      } else {
        Validation.succeed(withClass[Tag](ontoClass))
      }

    def withPropertyV[Tag <: Singleton with String](
      propertyInfo: OntologyProperty[Tag]
    ): Validation[String, Ontology[Classes, Properties with Tag]] =
      if (properties.contains(propertyInfo)) {
        Validation.fail(s"Property ${propertyInfo.name} already exists in ontology")
      } else {
        Validation.succeed(withProperty[Tag](propertyInfo))
      }

    def withCardinalityV(
      cardinality: Cardinality
    ): Validation[String, Ontology[Classes, Properties]] = {
      def checkClasses: Validation[String, Unit] =
        if (!classes.contains(cardinality.ontologyClass)) {
          Validation.fail(s"Class ${cardinality.ontologyClass.name} does not exist in ontology")
        } else {
          Validation.succeed(())
        }

      def checkProperties: Validation[String, Unit] =
        if (!properties.contains(cardinality.ontologyProperty)) {
          Validation.fail(s"Property ${cardinality.ontologyProperty.name} does not exist in ontology")
        } else {
          Validation.succeed(())
        }

      def checkCardinality: Validation[String, Unit] =
        if (
          cardinalities.exists(c =>
            c.ontologyClass == cardinality.ontologyClass && c.ontologyProperty == cardinality.ontologyProperty
          )
        ) {
          Validation.fail(
            s"Cardinality ${cardinality.ontologyClass.name} ${cardinality.ontologyProperty.name} already exists in ontology"
          )
        } else {
          Validation.succeed(())
        }

      (checkClasses &> checkProperties &> checkCardinality).as {
        Ontology.WithCardinality[Classes, Properties, cardinality.ClassTag, cardinality.PropertyTag](
          self,
          cardinality
        )
      }
    }

  }
  object Ontology {
    def empty(ontoInfo: OntologyInfo): Ontology[Any, Any] = Ontology.Empty(ontoInfo)

    private final case class Empty(info: OntologyInfo) extends Ontology[Any, Any]

    private final case class WithClass[Classes, Properties, T1 <: Singleton with String](
      ontology: Ontology[Classes, Properties],
      singleClass: OntologyClass[T1]
    ) extends Ontology[Classes with T1, Properties]

    private final case class WithProperty[Classes, Properties, T2 <: Singleton with String](
      ontology: Ontology[Classes, Properties],
      property: OntologyProperty[T2]
    ) extends Ontology[Classes, Properties with T2]

    private final case class WithCardinality[Classes, Properties, Class1, Property1](
      ontology: Ontology[Classes, Properties],
      cardinality: Cardinality.WithTags[Class1, Property1]
    ) extends Ontology[Classes, Properties]
  }

  trait NotSubtypeOf[A, B]
  object NotSubtypeOf {
    implicit def notSubtypeOf[A, B]: NotSubtypeOf[A, B] = new NotSubtypeOf[A, B] {}

    implicit def isSubtypeOf1[A, B >: A]: NotSubtypeOf[A, B] = new NotSubtypeOf[A, B] {}
    implicit def isSubtypeOf2[A, B >: A]: NotSubtypeOf[A, B] = new NotSubtypeOf[A, B] {}
  }

  // trying it out
  val ontoInfo = OntologyInfo("test", "http://example.org/test", "Test", "Test")

  val classOne    = OntologyClass("ClassOne", "Class One", "Class One")
  val propertyOne = OntologyProperty("PropertyOne", "Property One", "Property One", "http://example.org/test")

  val classTwo    = OntologyClass("ClassTwo", "Class Two", "Class Two")
  val propertyTwo = OntologyProperty("PropertyTwo", "Property Two", "Property Two", "http://example.org/test")

  val cardOne   = Cardinality(classOne, propertyOne, CardinalityType.MinCardinalityOne)
  val cardTwo   = Cardinality(classTwo, propertyTwo, CardinalityType.MinCardinalityOne)
  val cardThree = Cardinality(classOne, propertyTwo, CardinalityType.MinCardinalityOne)
  val cardFour  = Cardinality(classTwo, propertyOne, CardinalityType.MinCardinalityOne)

  val exampleOnto: Ontology[Any with "ClassOne" with "ClassTwo", Any with "PropertyOne"] =
    Ontology
      .empty(ontoInfo)
      .withClass(classOne)
      .withClass(classTwo)
      .withProperty(propertyOne)
      // .withProperty(propertyOne)
      // .withProperty(propertyTwo)
      .withCardinality(cardOne)
      .withCardinality(cardOne)

  // TODO: check that at compile time the same class or property is not added twice (uniqueness is defined by the name)
  // TODO: check that runtime validation doesn't allow adding a cardinality with the same class and property combination twice
  // TODO: add unit tests doing all these tests
  // TODO: add the describe method
  // TODO: in the next session, do a runtime creation of the ontology by using the describe string
  // TODO: sketch out the onion layers for functional domain (includes domain entities?), ontology service, ontology API, and ontology repository
  // TODO: Bazel vs SBT

  // Authorization functional domain
  // subject verb object
  // user/role read id

  // OntoResponder
  //

  // OntoAPI v1 / v2 / v3 (could be different SBT projects per API version (at a later time could make sense))
  // calls into OntoResponder (thin wrapper around the functional domain)
  // calls into Functional Domain holds all entities and operations
  // returns the entity to OntoResponder
  // OntoResponder return read to API
  //      "        writes through OntoRepo delta event and sends back answer through API

  // OntoRepo (interfaces) + OntoResponder + FD (one SBT project)

  // OntoRepo (Trait) Type definition never depends on other traits
  //  + save   => EventStoreService.storeEvent()
  //  + read: OntologyEntity
  //  + search => SearchService.find()

  // SearchService (concrete implementation) - SBT project
  //  + find

  // EventStoreService (concrete implementation) - SBT project

//
//  val x: classOne.Tag = ???
//
//  // path dependent types
  implicitly[classOne.Tag =:= classOne.Tag]
  // implicitly[classOne.Tag =:= classTwo.Tag]
}
