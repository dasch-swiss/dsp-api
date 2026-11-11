/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.valuemessages

import org.junit.runner.RunWith
import zio.Runtime
import zio.Unsafe
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import java.util.UUID

import dsp.errors.AssertionException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.TestDataFactory
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.rdf.JsonLDObject
import org.knora.webapi.messages.util.rdf.JsonLDString
import org.knora.webapi.messages.util.rdf.JsonLDValue
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v2.responder.standoffmessages.*
import org.knora.webapi.slice.common.StandoffMappingIri

@RunWith(classOf[DspZTestJUnitRunner])
class TextValueContentV2Spec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val testUuid = UUID.fromString("12345678-90ab-cdef-1234-567890abcdef")
  private val testText = "Hello"

  private def xmlTag(name: String, standoffClassIri: String) =
    XMLTag(name, XMLTagToStandoffClass(standoffClassIri, dataType = None), separatorRequired = false)

  // A hermetic mapping covering exactly the "text" tag used by testStandoff below.
  private val testMapping: MappingXMLtoStandoff =
    MappingXMLtoStandoff(
      namespace = Map(
        "noNamespace" -> Map(
          "text" -> Map("noClass" -> xmlTag("text", OntologyConstants.Standoff.StandoffRootTag)),
        ),
      ),
      defaultXSLTransformation = None,
    )

  private val testStandoff: Seq[StandoffTagV2] = Vector(
    StandoffTagV2(
      standoffTagClassIri = sf.toSmartIri(OntologyConstants.Standoff.StandoffRootTag),
      uuid = testUuid,
      originalXMLID = None,
      startPosition = 0,
      endPosition = testText.length,
      startIndex = 0,
    ),
  )

  private def textValue(
    standoff: Seq[StandoffTagV2] = testStandoff,
    mapping: Option[MappingXMLtoStandoff] = Some(testMapping),
    textValueType: TextValueType = TextValueType.FormattedText,
  ): TextValueContentV2 =
    TextValueContentV2(
      ontologySchema = ApiV2Complex,
      maybeValueHasString = Some(testText),
      textValueType = textValueType,
      standoff = standoff,
      mapping = mapping,
    )

  private val mappingIri = StandoffMappingIri.StandardMapping

  private val appConfig: AppConfig =
    Unsafe.unsafe(implicit u => Runtime.default.unsafe.run(AppConfig.parseConfig).getOrThrowFiberFailure())

  private val storedXmlLiteral = "<text>stored literal</text>"

  private def render(content: TextValueContentV2): Map[String, JsonLDValue] =
    content.toJsonLDValue(ApiV2Complex, TestDataFactory.someProjectADM, appConfig, Set.empty) match {
      case JsonLDObject(value) => value
      case other               => throw new AssertionError(s"Expected JsonLDObject, got $other")
    }

  private def formatted(
    storedXml: Option[String],
    standoff: Seq[StandoffTagV2] = Vector.empty,
    xslt: Option[String] = None,
  ): Map[String, JsonLDValue] =
    render(
      textValue(standoff = standoff).copy(
        mappingIri = Some(mappingIri),
        xslt = xslt,
        storedXml = storedXml,
      ),
    )

  private val identityXslt =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
      |  <xsl:template match="/text"><div class="stored"><xsl:value-of select="."/></div></xsl:template>
      |</xsl:stylesheet>""".stripMargin

  private val toJsonLDValueSuite = suite("TextValueContentV2.toJsonLDValue")(
    test("stored XML without standoff is returned as textValueAsXml with the mapping") {
      val out = formatted(Some(storedXmlLiteral))
      assertTrue(
        out(OntologyConstants.KnoraApiV2Complex.TextValueAsXml) == JsonLDString(storedXmlLiteral),
        out.contains(OntologyConstants.KnoraApiV2Complex.TextValueHasMapping),
        !out.contains(OntologyConstants.KnoraApiV2Complex.ValueAsString),
      )
    },
    test("standoff without stored XML renders XML built from the standoff") {
      val expected = StandoffTagUtilV2.convertStandoffTagV2ToXML(testText, testStandoff, testMapping)
      val out      = formatted(None, standoff = testStandoff)
      assertTrue(out(OntologyConstants.KnoraApiV2Complex.TextValueAsXml) == JsonLDString(expected))
    },
    test("stored XML wins over standoff") {
      val out = formatted(Some(storedXmlLiteral), standoff = testStandoff)
      assertTrue(out(OntologyConstants.KnoraApiV2Complex.TextValueAsXml) == JsonLDString(storedXmlLiteral))
    },
    test("the XSLT is applied to the stored XML") {
      val out  = formatted(Some(storedXmlLiteral), xslt = Some(identityXslt))
      val html = out.get(OntologyConstants.KnoraApiV2Complex.TextValueAsHtml).collect { case JsonLDString(s) => s }
      assertTrue(
        out(OntologyConstants.KnoraApiV2Complex.TextValueAsXml) == JsonLDString(storedXmlLiteral),
        html.exists(_.contains("stored literal")),
        html.exists(_.contains("class=\"stored\"")),
      )
    },
    test("unformatted text without stored XML renders valueAsString") {
      val out = render(textValue(standoff = Vector.empty, textValueType = TextValueType.UnformattedText))
      assertTrue(
        out(OntologyConstants.KnoraApiV2Complex.ValueAsString) == JsonLDString(testText),
        !out.contains(OntologyConstants.KnoraApiV2Complex.TextValueAsXml),
      )
    },
  )

  override def spec: Spec[Any, Nothing] =
    suite("TextValueContentV2")(
      toJsonLDValueSuite,
      computedValueHasXmlSuite,
      standoffLinkTargetsSuite,
      prepareForSparqlInsertSuite,
    )

  private val computedValueHasXmlSuite =
    suite("TextValueContentV2.computedValueHasXml")(
      test("returns Some(xml) matching the direct renderer output for a formatted value with standoff and mapping") {
        val expectedXml = StandoffTagUtilV2.convertStandoffTagV2ToXML(testText, testStandoff, testMapping)
        assertTrue(textValue().computedValueHasXml == Some(expectedXml))
      },
      test("returns None for a value with standoff but no mapping") {
        assertTrue(textValue(mapping = None).computedValueHasXml == None)
      },
      test("returns None for an unformatted text value even when it carries standoff and a mapping") {
        assertTrue(textValue(textValueType = TextValueType.UnformattedText).computedValueHasXml == None)
      },
      test("returns None for a value with empty standoff") {
        assertTrue(textValue(standoff = Vector.empty).computedValueHasXml == None)
      },
    )

  private val linkTarget = "http://rdfh.ch/0001/linkTarget"

  private val linkTag: StandoffTagV2 =
    StandoffTagV2(
      standoffTagClassIri = sf.toSmartIri(OntologyConstants.KnoraBase.StandoffLinkTag),
      dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
      uuid = testUuid,
      originalXMLID = None,
      startPosition = 0,
      endPosition = testText.length,
      startIndex = 0,
      attributes = Seq(
        StandoffTagIriAttributeV2(sf.toSmartIri(OntologyConstants.KnoraBase.StandoffTagHasLink), linkTarget),
      ),
    )

  private val servedValue: TextValueContentV2 =
    textValue(standoff = Vector.empty).copy(mappingIri = Some(mappingIri), storedXml = Some(storedXmlLiteral))

  private val servedWithoutMapping: TextValueContentV2 = servedValue.copy(mappingIri = None)

  private val linkAttribute =
    StandoffTagIriAttributeV2(sf.toSmartIri(OntologyConstants.KnoraBase.StandoffTagHasLink), linkTarget)

  private def assertServedFailure(failure: Option[AssertionException], mappingPart: String) = {
    val message = failure.map(_.message).getOrElse("")
    assertTrue(
      failure.isDefined,
      message.contains(mappingPart),
      message.contains("serve-stored-XML"),
      message.contains("StoredXmlServing"),
      !message.contains(testText),
      !message.contains(storedXmlLiteral),
    )
  }

  private val standoffLinkTargetsSuite =
    suite("TextValueContentV2 link targets")(
      test("standoffLinkTagTargetResourceIris fails for a served value, naming the mapping and no text") {
        assertTrue(servedValue.isServedFromStore) &&
        assertServedFailure(servedValue.standoffLinkTagTargetResourceIris.left.toOption, mappingIri.value)
      },
      test("standoffLinkTagIriAttributes fails for a served value, naming the mapping and no text") {
        assertServedFailure(servedValue.standoffLinkTagIriAttributes.left.toOption, mappingIri.value)
      },
      test("a served value without a mapping fails without naming one") {
        assertServedFailure(servedWithoutMapping.standoffLinkTagTargetResourceIris.left.toOption, "without a mapping")
      },
      test("a value with standoff gives its link targets") {
        val withLink = textValue(standoff = Vector(linkTag)).copy(storedXml = Some(storedXmlLiteral))
        assertTrue(!withLink.isServedFromStore, withLink.standoffLinkTagTargetResourceIris == Right(Set(linkTarget)))
      },
      test("a value with standoff gives the IRI attributes of its link tags only, with their targetExists flag") {
        val missingTarget = linkAttribute.copy(value = "http://rdfh.ch/0001/missing", targetExists = false)
        val otherTag      = testStandoff.head.copy(attributes = Seq(linkAttribute.copy(value = "http://rdfh.ch/0001/other")))
        val value         = textValue(standoff = Vector(linkTag.copy(attributes = Seq(linkAttribute, missingTarget)), otherTag))
        assertTrue(value.standoffLinkTagIriAttributes == Right(Set(linkAttribute, missingTarget)))
      },
      test("an unformatted value gives no targets") {
        val unformatted = textValue(standoff = Vector.empty, textValueType = TextValueType.UnformattedText)
        assertTrue(unformatted.standoffLinkTagTargetResourceIris == Right(Set.empty[String]))
      },
      test("a formatted value without standoff and without stored XML gives no targets") {
        assertTrue(textValue(standoff = Vector.empty).standoffLinkTagTargetResourceIris == Right(Set.empty[String]))
      },
    )

  private val prepareForSparqlInsertSuite =
    suite("TextValueContentV2.prepareForSparqlInsert")(
      test("preparing a served value for insert fails with the served-value assertion") {
        val failure =
          scala.util.Try(servedValue.prepareForSparqlInsert("http://rdfh.ch/0001/res/value")).failed.toOption
        assertServedFailure(failure.collect { case e: AssertionException => e }, mappingIri.value)
      },
      test("a value with standoff is prepared for insert") {
        assertTrue(textValue().prepareForSparqlInsert("http://rdfh.ch/0001/res/value").size == testStandoff.size)
      },
    )
}
