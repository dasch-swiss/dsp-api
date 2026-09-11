/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.apache.jena.query.DatasetFactory
import org.apache.jena.rdf.model.Model
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.update.UpdateAction
import org.apache.jena.update.UpdateFactory
import org.junit.runner.RunWith
import zio.test.*

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.Instant

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * The query builder emits a single `DELETE … INSERT … WHERE` statement, whereas the legacy
 * implementation emitted three statements separated by `;`. The shapes therefore differ on purpose;
 * equivalence is asserted at the data level by running both against identical in-memory datasets.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ChangePropertyGuiElementQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val ontologyIri =
    OntologyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)
  private val propertyIri          = PropertyIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#hasText".toSmartIri)
  private val linkValuePropertyIri =
    PropertyIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#hasTextValue".toSmartIri)
  private val guiElementIri = "http://www.knora.org/ontology/salsah-gui#SimpleText".toSmartIri
  private val lastModDate   = Instant.parse("2023-08-01T10:30:00Z")
  private val currentTime   = Instant.parse("2023-08-02T12:00:00Z")

  // ---------------------------------------------------------------------------------------------
  // Legacy fixtures: the three-statement rendering the RDF4J-based builder used to produce.
  // ---------------------------------------------------------------------------------------------

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

  private val legacyDeleteOldWithoutLinkValueProperty = prefixes +
    """
      |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiElement ?oldGuiElement .
      |<http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiAttribute ?oldGuiAttribute . } }
      |WHERE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
      |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
      |OPTIONAL { <http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiElement ?oldGuiElement . }
      |OPTIONAL { <http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiAttribute ?oldGuiAttribute . } } }""".stripMargin

  private val legacyDeleteOldWithLinkValueProperty = prefixes +
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

  private val legacyInsertNewWithoutLinkValueProperty = prefixes +
    """
      |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiElement salsah-gui:SimpleText .
      |<http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiAttribute "size=80" . } }
      |WHERE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
      |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }""".stripMargin

  private val legacyInsertNewWithLinkValueProperty = prefixes +
    """
      |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiElement salsah-gui:SimpleText .
      |<http://www.knora.org/ontology/0001/anything#hasText> salsah-gui:guiAttribute "size=80" .
      |<http://www.knora.org/ontology/0001/anything#hasTextValue> salsah-gui:guiElement salsah-gui:SimpleText .
      |<http://www.knora.org/ontology/0001/anything#hasTextValue> salsah-gui:guiAttribute "size=80" . } }
      |WHERE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
      |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }""".stripMargin

  /** New gui element and attribute, no link value property. */
  private val legacySetGui =
    legacyDeleteOldWithoutLinkValueProperty + ";\n" + legacyInsertNewWithoutLinkValueProperty + ";\n" +
      updateTimestampQuery

  /** New gui element and attribute, with link value property. */
  private val legacySetGuiWithLinkValueProperty =
    legacyDeleteOldWithLinkValueProperty + ";\n" + legacyInsertNewWithLinkValueProperty + ";\n" + updateTimestampQuery

  /** Nothing to set, no link value property. */
  private val legacyRemoveGui = legacyDeleteOldWithoutLinkValueProperty + ";\n" + updateTimestampQuery

  /** Nothing to set, with link value property. */
  private val legacyRemoveGuiWithLinkValueProperty =
    legacyDeleteOldWithLinkValueProperty + ";\n" + updateTimestampQuery

  // ---------------------------------------------------------------------------------------------
  // Data-level equivalence helpers.
  // ---------------------------------------------------------------------------------------------

  private val ontologyGraph = "http://www.knora.org/ontology/0001/anything"

  private val trigPrefixes =
    """@prefix owl: <http://www.w3.org/2002/07/owl#> .
      |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
      |@prefix knora-base: <http://www.knora.org/ontology/knora-base#> .
      |@prefix salsah-gui: <http://www.knora.org/ontology/salsah-gui#> .
      |""".stripMargin

  private def oldGuiSettings(subject: String): String =
    s"""|  <$subject> salsah-gui:guiElement salsah-gui:Textarea ;
        |    salsah-gui:guiAttribute "size=40", "maxlength=255" .
        |""".stripMargin

  /**
   * An ontology graph holding the precondition triples, optionally the old gui settings of the
   * property and its link value property, and unrelated triples that must survive the update.
   */
  private def trigFixture(
    modificationDate: String = "2023-08-01T10:30:00Z",
    propertyHasOldGui: Boolean = true,
    linkValuePropertyHasOldGui: Boolean = false,
  ): String =
    trigPrefixes +
      s"""|<$ontologyGraph> {
          |  <$ontologyGraph> a owl:Ontology ;
          |    rdfs:label "The anything ontology" ;
          |    knora-base:lastModificationDate "$modificationDate"^^xsd:dateTime .
          |  <$ontologyGraph#hasText> a owl:ObjectProperty ;
          |    rdfs:label "has text" .
          |  <$ontologyGraph#hasTextValue> a owl:ObjectProperty .
          |  <$ontologyGraph#hasInteger> a owl:ObjectProperty ;
          |    salsah-gui:guiElement salsah-gui:Spinbox ;
          |    salsah-gui:guiAttribute "min=0" .
          |""".stripMargin +
      (if (propertyHasOldGui) oldGuiSettings(s"$ontologyGraph#hasText") else "") +
      (if (linkValuePropertyHasOldGui) oldGuiSettings(s"$ontologyGraph#hasTextValue") else "") +
      "}\n"

  /** Load `trig`, run `update` on it and return the resulting ontology graph. */
  private def execute(update: String, trig: String): Model = {
    val dataset = DatasetFactory.create()
    RDFDataMgr.read(dataset, new ByteArrayInputStream(trig.getBytes(StandardCharsets.UTF_8)), Lang.TRIG)
    UpdateAction.parseExecute(update, dataset)
    dataset.getNamedModel(ontologyGraph)
  }

  private def ontologyGraphOf(trig: String): Model = {
    val dataset = DatasetFactory.create()
    RDFDataMgr.read(dataset, new ByteArrayInputStream(trig.getBytes(StandardCharsets.UTF_8)), Lang.TRIG)
    dataset.getNamedModel(ontologyGraph)
  }

  private def buildQuery(
    maybeLinkValuePropertyIri: Option[PropertyIri],
    maybeNewGuiElement: Option[SmartIri],
    newGuiAttributes: Set[String],
  ): Update =
    ChangePropertyGuiElementQuery.build(
      ontologyIri = ontologyIri,
      propertyIri = propertyIri,
      maybeLinkValuePropertyIri = maybeLinkValuePropertyIri,
      maybeNewGuiElement = maybeNewGuiElement,
      newGuiAttributes = newGuiAttributes,
      lastModificationDate = lastModDate,
      currentTime = currentTime,
    )

  /** Run the legacy and the new rendering against identical datasets and compare the outcome. */
  private def assertSameData(legacy: String, actual: Update, trig: String) = {
    val legacyResult = execute(legacy, trig)
    val actualResult = execute(actual.sparql, trig)
    assertTrue(
      actualResult.isIsomorphicWith(legacyResult),
      // guards against both updates being no-ops, e.g. because the precondition never matched
      !actualResult.isIsomorphicWith(ontologyGraphOf(trig)),
      UpdateFactory.create(actual.sparql).getOperations.size == 1,
    )
  }

  override val spec: Spec[Any, Nothing] = suite("ChangePropertyGuiElementQuery")(
    test("with guiElement and guiAttributes, no link value property") {
      val actual = buildQuery(None, Some(guiElementIri), Set("size=80"))
      assertSameData(legacySetGui, actual, trigFixture())
    },
    test("with guiElement and guiAttributes, with link value property") {
      val actual = buildQuery(Some(linkValuePropertyIri), Some(guiElementIri), Set("size=80"))
      assertSameData(
        legacySetGuiWithLinkValueProperty,
        actual,
        trigFixture(linkValuePropertyHasOldGui = true),
      )
    },
    test("no guiElement, no guiAttributes (delete only)") {
      val actual = buildQuery(None, None, Set.empty)
      assertSameData(legacyRemoveGui, actual, trigFixture())
    },
    test("no guiElement, no guiAttributes (delete only), with link value property") {
      val actual = buildQuery(Some(linkValuePropertyIri), None, Set.empty)
      assertSameData(
        legacyRemoveGuiWithLinkValueProperty,
        actual,
        trigFixture(linkValuePropertyHasOldGui = true),
      )
    },
    test("property without any old gui settings (unbound OPTIONALs)") {
      val actual = buildQuery(None, Some(guiElementIri), Set("size=80"))
      assertSameData(legacySetGui, actual, trigFixture(propertyHasOldGui = false))
    },
    test("mismatching lastModificationDate leaves the data untouched") {
      val trig         = trigFixture(modificationDate = "2020-01-01T00:00:00Z")
      val actual       = buildQuery(None, Some(guiElementIri), Set("size=80"))
      val original     = ontologyGraphOf(trig)
      val legacyResult = execute(legacySetGui, trig)
      val actualResult = execute(actual.sparql, trig)
      assertTrue(
        legacyResult.isIsomorphicWith(original),
        actualResult.isIsomorphicWith(original),
        actualResult.isIsomorphicWith(legacyResult),
      )
    },
    test("renders a single statement and omits link value property variables when there is none") {
      val actual = buildQuery(None, Some(guiElementIri), Set("size=80"))
      assertTrue(
        actual.sparql.linesIterator.filter(_.contains(";")).forall(_.endsWith("a owl:Ontology ;")),
        UpdateFactory.create(actual.sparql).getOperations.size == 1,
        !actual.sparql.contains("LinkValueProperty"),
      )
    },
  )
}
