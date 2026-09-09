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
    val ontology = Iri.unsafeFrom(ontologyIri.toInternalSchema.toIri)
    val property = Iri.unsafeFrom(propertyIri.toInternalSchema.toIri)
    // A link property's link value property carries the same GUI element and attributes,
    // so it is cleared and re-set alongside the property itself.
    val linkProperty  = maybeLinkValuePropertyIri.map(iri => Iri.unsafeFrom(iri.toInternalSchema.toIri))
    val guiElement    = maybeNewGuiElement.map(iri => Iri.unsafeFrom(iri.toInternalSchema.toIri))
    val guiAttributes = newGuiAttributes.toList.map(Literal.string)
    val previousDate  = Literal.dateTime(lastModificationDate)
    val currentDate   = Literal.dateTime(currentTime)

    Update(
      sparql"""|PREFIX owl: <http://www.w3.org/2002/07/owl#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX salsah-gui: <http://www.knora.org/ontology/salsah-gui#>
               |
               |DELETE {
               |  GRAPH $ontology {
               |    $ontology knora-base:lastModificationDate $previousDate .
               |    $property salsah-gui:guiElement ?oldGuiElement .
               |    $property salsah-gui:guiAttribute ?oldGuiAttribute .
               |    ${linkProperty.whenSome(lp => sparql"$lp salsah-gui:guiElement ?oldLinkValuePropertyGuiElement .")}
               |    ${linkProperty.whenSome(lp =>
          sparql"$lp salsah-gui:guiAttribute ?oldLinkValuePropertyGuiAttribute .",
        )}
               |  }
               |}
               |INSERT {
               |  GRAPH $ontology {
               |    $ontology knora-base:lastModificationDate $currentDate .
               |    ${guiElement.whenSome(e => sparql"$property salsah-gui:guiElement $e .")}
               |    ${guiAttributes.map(a => sparql"$property salsah-gui:guiAttribute $a .").joinLines}
               |    ${linkProperty.whenSome(lp => guiElement.whenSome(e => sparql"$lp salsah-gui:guiElement $e ."))}
               |    ${linkProperty.whenSome(lp =>
          guiAttributes.map(a => sparql"$lp salsah-gui:guiAttribute $a .").joinLines,
        )}
               |  }
               |}
               |WHERE {
               |  GRAPH $ontology {
               |    $ontology a owl:Ontology ;
               |      knora-base:lastModificationDate $previousDate .
               |    OPTIONAL { $property salsah-gui:guiElement ?oldGuiElement . }
               |    OPTIONAL { $property salsah-gui:guiAttribute ?oldGuiAttribute . }
               |    ${linkProperty.whenSome(lp =>
          sparql"OPTIONAL { $lp salsah-gui:guiElement ?oldLinkValuePropertyGuiElement . }",
        )}
               |    ${linkProperty.whenSome(lp =>
          sparql"OPTIONAL { $lp salsah-gui:guiAttribute ?oldLinkValuePropertyGuiAttribute . }",
        )}
               |  }
               |}""".render,
    )
  }
}
