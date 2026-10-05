/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.resources

import org.junit.runner.RunWith
import zio.*
import zio.test.*
import zio.test.Assertion.*

import java.util.UUID

import dsp.errors.NotFoundException
import dsp.errors.StandoffInternalException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.standoffmessages.MappingXMLtoStandoff
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffTagV2
import org.knora.webapi.messages.v2.responder.standoffmessages.XMLTag
import org.knora.webapi.messages.v2.responder.standoffmessages.XMLTagToStandoffClass
import org.knora.webapi.messages.v2.responder.valuemessages.TextValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.TextValueType
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.slice.resources.repo.model.TypeSpecificValueInfo.FormattedTextValueInfo

@RunWith(classOf[DspZTestJUnitRunner])
class GenerateFormattedTextValueInfoSpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private def xmlTag(name: String, standoffClassIri: String) =
    XMLTag(name, XMLTagToStandoffClass(standoffClassIri, dataType = None), separatorRequired = false)

  // A hermetic mapping covering exactly the root and bold tags of formattedText.
  private val coveringMapping = MappingXMLtoStandoff(
    namespace = Map(
      "noNamespace" -> Map(
        "text"   -> Map("noClass" -> xmlTag("text", OntologyConstants.Standoff.StandoffRootTag)),
        "strong" -> Map("noClass" -> xmlTag("strong", OntologyConstants.Standoff.StandoffBoldTag)),
      ),
    ),
    defaultXSLTransformation = None,
  )

  private val formattedText = TextValueContentV2(
    ontologySchema = ApiV2Complex,
    maybeValueHasString = Some("rho 19 sigma"),
    textValueType = TextValueType.FormattedText,
    valueHasLanguage = None,
    standoff = Vector(
      StandoffTagV2(
        standoffTagClassIri = OntologyConstants.Standoff.StandoffRootTag.toSmartIri,
        startPosition = 0,
        endPosition = 12,
        uuid = UUID.randomUUID(),
        originalXMLID = None,
        startIndex = 0,
      ),
      StandoffTagV2(
        standoffTagClassIri = OntologyConstants.Standoff.StandoffBoldTag.toSmartIri,
        startPosition = 4,
        endPosition = 6,
        uuid = UUID.randomUUID(),
        originalXMLID = None,
        startIndex = 1,
        startParentIndex = Some(0),
      ),
    ),
    mappingIri = Some(StandoffMappingIri.StandardMapping),
    mapping = Some(coveringMapping),
  )

  private def generate(tv: TextValueContentV2) =
    CreateResourceV2Handler.generateFormattedTextValueInfo(
      tv,
      standoffInfo = Seq.empty,
      textType = tv.textValueType,
      valueHasLanguage = tv.valueHasLanguage,
      mappingIri = StandoffMappingIri.StandardMapping,
    )

  override val spec = suite("CreateResourceV2Handler.generateFormattedTextValueInfo")(
    test("carry the canonical XML rendered from the value's standoff and mapping") {
      for {
        info <- generate(formattedText)
      } yield assertTrue(
        info match {
          case f: FormattedTextValueInfo =>
            f.valueHasXml.contains(
              "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text>rho <strong>19</strong> sigma</text>",
            )
          case _ => false
        },
      )
    },
    test("fail with StandoffInternalException, not NotFoundException, when the mapping does not cover a tag") {
      val uncovered =
        formattedText.copy(mapping = Some(MappingXMLtoStandoff(Map.empty, defaultXSLTransformation = None)))
      for {
        exit <- generate(uncovered).exit
      } yield assert(exit)(
        fails(
          isSubtype[StandoffInternalException](
            hasField("cause", _.cause, isSome(isSubtype[NotFoundException](anything))),
          ),
        ),
      )
    },
  )
}
