/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import java.time.Instant

import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object ChangePropertyGuiElementQuery {

  private val prefixes =
    sparql"""|PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
             |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
             |PREFIX owl: <http://www.w3.org/2002/07/owl#>
             |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
             |PREFIX salsah-gui: <http://www.knora.org/ontology/salsah-gui#>"""

  def build(
    ontologyIri: OntologyIri,
    propertyIri: PropertyIri,
    maybeLinkValuePropertyIri: Option[PropertyIri],
    maybeNewGuiElement: Option[SmartIri],
    newGuiAttributes: Set[String],
    lastModificationDate: Instant,
    currentTime: Instant,
  ): Update = {
    val ontology      = Iri.unsafeFrom(ontologyIri.toInternalSchema.toIri)
    val property      = Iri.unsafeFrom(propertyIri.toInternalSchema.toIri)
    val linkProperty  = maybeLinkValuePropertyIri.map(iri => Iri.unsafeFrom(iri.toInternalSchema.toIri))
    val guiElement    = maybeNewGuiElement.map(iri => Iri.unsafeFrom(iri.toInternalSchema.toIri))
    val guiAttributes = newGuiAttributes.toList.map(Literal.string)
    val previousDate  = Literal.dateTime(lastModificationDate)
    val currentDate   = Literal.dateTime(currentTime)

    val parts = List(
      Some(deleteOld(ontology, property, linkProperty, previousDate)),
      Option.when(guiElement.isDefined || guiAttributes.nonEmpty)(
        insertNew(ontology, property, linkProperty, guiElement, guiAttributes, previousDate),
      ),
      Some(updateTimestamp(ontology, previousDate, currentDate)),
    ).flatten

    Update(Fragment.join(parts, Fragment.raw(";\n")).render)
  }

  private def deleteOld(
    ontology: Iri,
    property: Iri,
    linkProperty: Option[Iri],
    previousDate: Literal,
  ): Fragment = {
    def deleteTriples(subject: Iri, elementVar: Variable, attributeVar: Variable): Fragment =
      sparql"""|$subject salsah-gui:guiElement $elementVar .
               |$subject salsah-gui:guiAttribute $attributeVar ."""

    def optionalPatterns(subject: Iri, elementVar: Variable, attributeVar: Variable): Fragment =
      sparql"""|OPTIONAL { $subject salsah-gui:guiElement $elementVar . }
               |OPTIONAL { $subject salsah-gui:guiAttribute $attributeVar . }"""

    val oldGuiElement            = Variable("oldGuiElement")
    val oldGuiAttribute          = Variable("oldGuiAttribute")
    val oldLinkValueGuiElement   = Variable("oldLinkValuePropertyGuiElement")
    val oldLinkValueGuiAttribute = Variable("oldLinkValuePropertyGuiAttribute")

    sparql"""|$prefixes
             |
             |DELETE {
             |  GRAPH $ontology {
             |    ${deleteTriples(property, oldGuiElement, oldGuiAttribute)}
             |    ${linkProperty.whenSome(deleteTriples(_, oldLinkValueGuiElement, oldLinkValueGuiAttribute))}
             |  }
             |}
             |WHERE {
             |  GRAPH $ontology {
             |    $ontology a owl:Ontology ;
             |      knora-base:lastModificationDate $previousDate .
             |    ${optionalPatterns(property, oldGuiElement, oldGuiAttribute)}
             |    ${linkProperty.whenSome(optionalPatterns(_, oldLinkValueGuiElement, oldLinkValueGuiAttribute))}
             |  }
             |}"""
  }

  private def insertNew(
    ontology: Iri,
    property: Iri,
    linkProperty: Option[Iri],
    guiElement: Option[Iri],
    guiAttributes: List[Literal],
    previousDate: Literal,
  ): Fragment = {
    def insertTriples(subject: Iri): Fragment =
      (guiElement.map(iri => sparql"$subject salsah-gui:guiElement $iri .").toList :::
        guiAttributes.map(attr => sparql"$subject salsah-gui:guiAttribute $attr .")).joinLines

    sparql"""|$prefixes
             |
             |INSERT {
             |  GRAPH $ontology {
             |    ${insertTriples(property)}
             |    ${linkProperty.whenSome(insertTriples)}
             |  }
             |}
             |WHERE {
             |  GRAPH $ontology {
             |    $ontology a owl:Ontology ;
             |      knora-base:lastModificationDate $previousDate .
             |  }
             |}"""
  }

  private def updateTimestamp(ontology: Iri, previousDate: Literal, currentDate: Literal): Fragment =
    sparql"""|$prefixes
             |
             |DELETE {
             |  GRAPH $ontology {
             |    $ontology knora-base:lastModificationDate $previousDate .
             |  }
             |}
             |INSERT {
             |  GRAPH $ontology {
             |    $ontology knora-base:lastModificationDate $currentDate .
             |  }
             |}
             |WHERE {
             |  GRAPH $ontology {
             |    $ontology a owl:Ontology ;
             |      knora-base:lastModificationDate $previousDate .
             |  }
             |}"""
}
