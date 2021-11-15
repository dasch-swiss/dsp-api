/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.knora.webapi.domain

import org.knora.webapi.IRI

object OntologyDomain extends App {
  final case class OntologyInfo(name: String, projectIri: IRI, label: String, comment: String)
  final case class OntologyClass(name: String, label: String, comment: String) {
    type Tag
  }
  object OntologyClass {
    type WithTag[T] = OntologyClass { type Tag = T }
  }
  final case class OntologyProperty(name: String, label: String, comment: String, range: String) {
    type Tag
  }
  object OntologyProperty {
    type WithTag[T] = OntologyProperty { type Tag = T }
  }

  trait Cardinality {
    type ClassTag
    type PropertyTag

    def ontologyClass: OntologyClass.WithTag[ClassTag]
    def ontologyProperty: OntologyProperty.WithTag[PropertyTag]
    def cardinalityType: CardinalityType
  }
  object Cardinality {
    type WithTags[T1, T2] = Cardinality { type ClassTag = T1; type PropertyTag = T2 }

    def apply(oc: OntologyClass, op: OntologyProperty, ct: CardinalityType): WithTags[oc.Tag, op.Tag] =
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

  sealed trait Ontology[+Classes, +Properties] { self =>
    def bimap[C2, P2](f: Classes => C2, g: Properties => P2): Ontology[C2, P2] = ???

    def empty(ontoInfo: OntologyInfo): Ontology[Any, Any] = Ontology.Empty(ontoInfo)

    def withClass(ontoClass: OntologyClass): Ontology[Classes with ontoClass.Tag, Properties] =
      Ontology.WithClass[Classes, Properties, ontoClass.Tag](self, ontoClass)

    def withProperty(propertyInfo: OntologyProperty): Ontology[Classes, Properties with propertyInfo.Tag] =
      Ontology.WithProperty[Classes, Properties, propertyInfo.Tag](self, propertyInfo)

    def withCardinality(
      cardinality: Cardinality
    )(implicit
      ev1: Classes <:< cardinality.ClassTag,
      ev2: Properties <:< cardinality.PropertyTag
    ): Ontology[Classes, Properties] =
      Ontology.WithCardinality[Classes, Properties](self, cardinality)

  }
  object Ontology {
    final case class Empty(info: OntologyInfo) extends Ontology[Any, Any]

    final case class WithClass[Classes, Properties, T1](
      ontology: Ontology[Classes, Properties],
      singleClass: OntologyClass.WithTag[T1]
    ) extends Ontology[Classes with T1, Properties]

    final case class WithProperty[Classes, Properties, T2](
      ontology: Ontology[Classes, Properties],
      property: OntologyProperty.WithTag[T2]
    ) extends Ontology[Classes, Properties with T2]

    final case class WithCardinality[Classes, Properties](
      ontology: Ontology[Classes, Properties],
      cardinality: Cardinality.WithTags[_ >: Classes, _ >: Properties]
    ) extends Ontology[Classes, Properties]
  }

  def describe[Classes, Properties](ontology: Ontology[Classes, Properties]): String = ontology match {
    case Ontology.Empty(info) =>
      s"Ontology: ${info.name} (${info.projectIri})"
  }

  //trying it out
  val ontoInfo = OntologyInfo("test", "http://example.org/test", "Test", "Test")

  val classOne    = OntologyClass("ClassOne", "Class One", "Class One")
  val propertyOne = OntologyProperty("PropertyOne", "Property One", "Property One", "http://example.org/test")

  val classTwo    = OntologyClass("ClassTwo", "Class Two", "Class Two")
  val propertyTwo = OntologyProperty("PropertyTwo", "Property Two", "Property Two", "http://example.org/test")

  val cardOne = Cardinality(classOne, propertyOne, CardinalityType.MinCardinalityOne)
  val cardTwo = Cardinality(classTwo, propertyTwo, CardinalityType.MinCardinalityOne)

  val exampleOnto =
    Ontology
      .Empty(ontoInfo)
      .withClass(classOne)
      //.withClass(classTwo)
      .withProperty(propertyOne)
      //.withProperty(propertyTwo)
      .withCardinality(cardOne)
      .withCardinality(cardTwo)

//
//  val x: classOne.Tag = ???
//
//  // path dependent types
  implicitly[classOne.Tag =:= classOne.Tag]
//  implicitly[classOne.Tag =:= classTwo.Tag]
}
