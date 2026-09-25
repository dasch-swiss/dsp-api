/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.valuemessages

import org.junit.runner.RunWith
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import java.util.UUID

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v2.responder.standoffmessages.*

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
  ): TextValueContentV2 =
    TextValueContentV2(
      ontologySchema = ApiV2Complex,
      maybeValueHasString = Some(testText),
      textValueType = TextValueType.FormattedText,
      standoff = standoff,
      mapping = mapping,
    )

  override def spec: Spec[Any, Nothing] =
    suite("TextValueContentV2.computedValueHasXml")(
      test("returns Some(xml) matching the direct renderer output for a formatted value with standoff and mapping") {
        val expectedXml = StandoffTagUtilV2.convertStandoffTagV2ToXML(testText, testStandoff, testMapping)
        assertTrue(textValue().computedValueHasXml == Some(expectedXml))
      },
      test("returns None for a value with standoff but no mapping") {
        assertTrue(textValue(mapping = None).computedValueHasXml == None)
      },
      test("returns None for a value with empty standoff") {
        assertTrue(textValue(standoff = Vector.empty).computedValueHasXml == None)
      },
    )
}
