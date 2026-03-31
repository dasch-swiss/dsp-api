/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import zio.test.*

import java.time.Instant

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter

object ChangePropertyGuiElementQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val ontologyIri          = "http://www.knora.org/ontology/0001/anything".toSmartIri
  private val propertyIri          = "http://www.knora.org/ontology/0001/anything#hasText".toSmartIri
  private val linkValuePropertyIri = "http://www.knora.org/ontology/0001/anything#hasTextValue".toSmartIri
  private val guiElementIri        = "http://www.knora.org/ontology/salsah-gui#SimpleText".toSmartIri
  private val lastModDate          = Instant.parse("2023-08-01T10:30:00Z")
  private val currentTime          = Instant.parse("2023-08-02T12:00:00Z")

  private val prefixes =
    """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
      |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
      |PREFIX owl: <http://www.w3.org/2002/07/owl#>
      |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
      |PREFIX salsah-gui: <http://www.knora.org/ontology/salsah-gui#>""".stripMargin

  private val updateTimestampQuery =
    prefixes +
      """
        |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }
        |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-02T12:00:00Z"^^xsd:dateTime . } }
        |WHERE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
        |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }""".stripMargin

  override val spec: Spec[Any, Nothing] = suite("ChangePropertyGuiElementQuery")(
    test("with guiElement and guiAttributes, no link value property") {
      val actual = ChangePropertyGuiElementQuery.build(
        ontologyNamedGraphIri = ontologyIri,
        ontologyIri = ontologyIri,
        propertyIri = propertyIri,
        maybeLinkValuePropertyIri = None,
        maybeNewGuiElement = Some(guiElementIri),
        newGuiAttributes = Set("size=80"),
        lastModificationDate = lastModDate,
        currentTime = currentTime,
      )

      val deleteOldQuery = prefixes +
        """
          |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiElement ?oldGuiElement .
          |<http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiAttribute ?oldGuiAttribute . } }
          |WHERE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
          |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
          |OPTIONAL { <http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiElement ?oldGuiElement . }
          |OPTIONAL { <http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiAttribute ?oldGuiAttribute . } } }""".stripMargin

      val insertNewQuery = prefixes +
        """
          |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiElement salsah-gui:SimpleText .
          |<http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiAttribute "size=80" . } }
          |WHERE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
          |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }""".stripMargin

      val expected = deleteOldQuery + ";\n" + insertNewQuery + ";\n" + updateTimestampQuery
      assertTrue(actual.sparql == expected)
    },
    test("with guiElement and guiAttributes, with link value property") {
      val actual = ChangePropertyGuiElementQuery.build(
        ontologyNamedGraphIri = ontologyIri,
        ontologyIri = ontologyIri,
        propertyIri = propertyIri,
        maybeLinkValuePropertyIri = Some(linkValuePropertyIri),
        maybeNewGuiElement = Some(guiElementIri),
        newGuiAttributes = Set("size=80"),
        lastModificationDate = lastModDate,
        currentTime = currentTime,
      )

      val deleteOldQuery = prefixes +
        """
          |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiElement ?oldGuiElement .
          |<http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiAttribute ?oldGuiAttribute .
          |<http://www.knora.org/ontology/0001/anything#hasTextValue> salsah-gui:guiElement ?oldLinkValuePropertyGuiElement .
          |<http://www.knora.org/ontology/0001/anything#hasTextValue> salsah-gui:guiAttribute ?oldLinkValuePropertyGuiAttribute . } }
          |WHERE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
          |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
          |OPTIONAL { <http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiElement ?oldGuiElement . }
          |OPTIONAL { <http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiAttribute ?oldGuiAttribute . }
          |OPTIONAL { <http://www.knora.org/ontology/0001/anything#hasTextValue> salsah-gui:guiElement ?oldLinkValuePropertyGuiElement . }
          |OPTIONAL { <http://www.knora.org/ontology/0001/anything#hasTextValue> salsah-gui:guiAttribute ?oldLinkValuePropertyGuiAttribute . } } }""".stripMargin

      val insertNewQuery = prefixes +
        """
          |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiElement salsah-gui:SimpleText .
          |<http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiAttribute "size=80" .
          |<http://www.knora.org/ontology/0001/anything#hasTextValue> salsah-gui:guiElement salsah-gui:SimpleText .
          |<http://www.knora.org/ontology/0001/anything#hasTextValue> salsah-gui:guiAttribute "size=80" . } }
          |WHERE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
          |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }""".stripMargin

      val expected = deleteOldQuery + ";\n" + insertNewQuery + ";\n" + updateTimestampQuery
      assertTrue(actual.sparql == expected)
    },
    test("no guiElement, no guiAttributes (delete only)") {
      val actual = ChangePropertyGuiElementQuery.build(
        ontologyNamedGraphIri = ontologyIri,
        ontologyIri = ontologyIri,
        propertyIri = propertyIri,
        maybeLinkValuePropertyIri = None,
        maybeNewGuiElement = None,
        newGuiAttributes = Set.empty,
        lastModificationDate = lastModDate,
        currentTime = currentTime,
      )

      val deleteOldQuery = prefixes +
        """
          |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiElement ?oldGuiElement .
          |<http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiAttribute ?oldGuiAttribute . } }
          |WHERE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
          |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
          |OPTIONAL { <http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiElement ?oldGuiElement . }
          |OPTIONAL { <http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiAttribute ?oldGuiAttribute . } } }""".stripMargin

      val noOpInsertQuery = prefixes +
        """
          |WHERE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
          |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }""".stripMargin

      val expected = deleteOldQuery + ";\n" + noOpInsertQuery + ";\n" + updateTimestampQuery
      assertTrue(actual.sparql == expected)
    },
  )
}
