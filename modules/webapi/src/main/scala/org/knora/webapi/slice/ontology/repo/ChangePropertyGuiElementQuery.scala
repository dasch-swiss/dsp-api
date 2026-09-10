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

    /** The new `salsah-gui:guiElement` triple (if any) plus one triple per new gui attribute. */
    def insertGuiTriples(subject: Iri): Fragment =
      (guiElement.map(e => sparql"$subject salsah-gui:guiElement $e .").toList :::
        guiAttributes.map(a => sparql"$subject salsah-gui:guiAttribute $a .")).joinLines

    // A link property's link value property carries the same GUI element and attributes,
    // so it is cleared and re-set alongside the property itself. The two shapes differ too
    // much for conditional holes, so each is written out as its own complete statement.
    val deleteOldGui = linkProperty match {
      case Some(lp) =>
        sparql"""|PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                 |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |PREFIX salsah-gui: <http://www.knora.org/ontology/salsah-gui#>
                 |
                 |DELETE {
                 |  GRAPH $ontology {
                 |    $property salsah-gui:guiElement ?oldGuiElement .
                 |    $property salsah-gui:guiAttribute ?oldGuiAttribute .
                 |    $lp salsah-gui:guiElement ?oldLinkValuePropertyGuiElement .
                 |    $lp salsah-gui:guiAttribute ?oldLinkValuePropertyGuiAttribute .
                 |  }
                 |}
                 |WHERE {
                 |  GRAPH $ontology {
                 |    $ontology a owl:Ontology ;
                 |      knora-base:lastModificationDate $previousDate .
                 |    OPTIONAL { $property salsah-gui:guiElement ?oldGuiElement . }
                 |    OPTIONAL { $property salsah-gui:guiAttribute ?oldGuiAttribute . }
                 |    OPTIONAL { $lp salsah-gui:guiElement ?oldLinkValuePropertyGuiElement . }
                 |    OPTIONAL { $lp salsah-gui:guiAttribute ?oldLinkValuePropertyGuiAttribute . }
                 |  }
                 |}"""
      case None =>
        sparql"""|PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                 |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |PREFIX salsah-gui: <http://www.knora.org/ontology/salsah-gui#>
                 |
                 |DELETE {
                 |  GRAPH $ontology {
                 |    $property salsah-gui:guiElement ?oldGuiElement .
                 |    $property salsah-gui:guiAttribute ?oldGuiAttribute .
                 |  }
                 |}
                 |WHERE {
                 |  GRAPH $ontology {
                 |    $ontology a owl:Ontology ;
                 |      knora-base:lastModificationDate $previousDate .
                 |    OPTIONAL { $property salsah-gui:guiElement ?oldGuiElement . }
                 |    OPTIONAL { $property salsah-gui:guiAttribute ?oldGuiAttribute . }
                 |  }
                 |}"""
    }

    // Omitted entirely when there is nothing to set (the GUI element was only removed).
    val insertNewGui = Option.when(guiElement.isDefined || guiAttributes.nonEmpty)(
      sparql"""|PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
               |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
               |PREFIX owl: <http://www.w3.org/2002/07/owl#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX salsah-gui: <http://www.knora.org/ontology/salsah-gui#>
               |
               |INSERT {
               |  GRAPH $ontology {
               |    ${insertGuiTriples(property)}
               |    ${linkProperty.whenSome(insertGuiTriples)}
               |  }
               |}
               |WHERE {
               |  GRAPH $ontology {
               |    $ontology a owl:Ontology ;
               |      knora-base:lastModificationDate $previousDate .
               |  }
               |}""",
    )

    val updateLastModificationDate =
      sparql"""|PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
               |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
               |PREFIX owl: <http://www.w3.org/2002/07/owl#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX salsah-gui: <http://www.knora.org/ontology/salsah-gui#>
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

    val statements = List(Some(deleteOldGui), insertNewGui, Some(updateLastModificationDate)).flatten
    Update(Fragment.join(statements, Fragment.raw(";\n")).render)
  }
}
