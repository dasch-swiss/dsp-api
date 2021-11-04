/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.knora.webapi.domain

import org.knora.webapi.IRI

object OntologyDomain extends App {
  final case class OntologyInfo(name: String, projectIri: IRI, label: String, comment: String)
  final case class OntologyClass(name: String, label: String, comment: String, properties: Seq[OntologyProperty])
  type OntologyProperty = String
  type OntologyDataType = String

  sealed trait Ontology { self =>

    def +(other: Ontology): Ontology = Ontology.Then(self, other)

  }
  object Ontology {
    final case class Empty(info: OntologyInfo) extends Ontology
    final case class Then(first: Ontology, second: Ontology) extends Ontology
    final case class AddClasses(classes: Set[OntologyClass]) extends Ontology
    final case class AddProperties(properties: Set[OntologyProperty]) extends Ontology

    def apply(info: OntologyInfo): Ontology = Empty(info)
    def addClasses(classes: Set[OntologyClass]): Ontology = AddClasses(classes)
    def addProperties(properties: Set[OntologyProperty]): Ontology = AddProperties(properties)

  }

  def describe(ontology: Ontology): String = ontology match {
    case Ontology.Empty(name)               => s"Ontology $name"
    case Ontology.Then(first, second)       => s"Ontology ${describe(first)} then ${describe(second)}"
    case Ontology.AddClasses(classes)       => s"Ontology with classes ${classes.mkString(", ")}"
    case Ontology.AddProperties(properties) => s"Ontology with properties ${properties.mkString(", ")}"
  }

  //trying it out
  val ontology: Ontology = Ontology(OntologyInfo("fu", "http://example.org/projects/1234", "Foo", "Bar")) +
    Ontology.addClasses(Set(OntologyClass("Foo", "Foo", "Foo", Seq("http://example.org/ontology/Foo#hasFoo")))) +
    Ontology.addProperties(Set("http://example.org/ontology/Foo#hasFoo"))
  println(describe(ontology))
}
