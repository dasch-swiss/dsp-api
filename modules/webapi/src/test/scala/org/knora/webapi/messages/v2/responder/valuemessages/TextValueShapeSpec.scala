/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.valuemessages

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.slice.common.domain.InternalIri

@RunWith(classOf[DspZTestJUnitRunner])
class TextValueShapeSpec extends ZIOSpecDefault {

  private val valueIri      = "http://rdfh.ch/0001/thing/values/abc"
  private val customMapping = "http://rdfh.ch/projects/0001/mappings/custom"

  private def derive(
    hasStoredXml: Boolean = false,
    hasStandoff: Boolean = false,
    mappingIri: Option[String] = None,
    storedType: Option[String] = None,
  ) = TextValueShape.derive(valueIri, hasStoredXml, hasStandoff, mappingIri, storedType)

  val spec = suite("TextValueShape.derive")(
    test("neither stored XML nor standoff gives unformatted text without a mapping") {
      assertTrue(
        derive(mappingIri = Some(KnoraBase.StandardMapping)) == Right(
          TextValueShape(TextValueType.UnformattedText, None),
        ),
      )
    },
    test("standoff with the standard mapping gives formatted text") {
      assertTrue(
        derive(hasStandoff = true, mappingIri = Some(KnoraBase.StandardMapping)) ==
          Right(TextValueShape(TextValueType.FormattedText, Some(StandoffMappingIri.StandardMapping))),
      )
    },
    test("standoff with a custom mapping gives custom formatted text") {
      assertTrue(
        derive(hasStandoff = true, mappingIri = Some(customMapping)) ==
          Right(
            TextValueShape(
              TextValueType.CustomFormattedText(InternalIri(customMapping)),
              Some(StandoffMappingIri.unsafeFrom(customMapping)),
            ),
          ),
      )
    },
    test("standoff without a mapping gives unformatted text without a mapping") {
      assertTrue(derive(hasStandoff = true) == Right(TextValueShape(TextValueType.UnformattedText, None)))
    },
    test("a stored hasTextValueType takes precedence over the inferred type") {
      assertTrue(
        derive(hasStandoff = true, mappingIri = Some(customMapping), storedType = Some(KnoraBase.FormattedText))
          .map(_.textValueType) == Right(TextValueType.FormattedText),
      )
    },
    test("a stored CustomFormattedText type uses the mapping IRI") {
      assertTrue(
        derive(hasStandoff = true, mappingIri = Some(customMapping), storedType = Some(KnoraBase.CustomFormattedText))
          .map(_.textValueType) == Right(TextValueType.CustomFormattedText(InternalIri(customMapping))),
      )
    },
    test("an undefined stored type falls back to the inferred type") {
      assertTrue(
        derive(
          hasStandoff = true,
          mappingIri = Some(KnoraBase.StandardMapping),
          storedType = Some(KnoraBase.UndefinedTextType),
        ).map(_.textValueType) == Right(TextValueType.FormattedText),
      )
    },
    test("stored XML without standoff is formatted text with the mapping from valueHasMapping") {
      assertTrue(
        derive(hasStoredXml = true, mappingIri = Some(KnoraBase.StandardMapping)) ==
          Right(TextValueShape(TextValueType.FormattedText, Some(StandoffMappingIri.StandardMapping))),
      )
    },
    test("stored XML without a mapping fails naming the value IRI") {
      assertTrue(derive(hasStoredXml = true).left.map(_.getMessage.contains(valueIri)) == Left(true))
    },
    test("an invalid mapping IRI fails") {
      assertTrue(derive(hasStandoff = true, mappingIri = Some("not-an-iri")).isLeft)
    },
  )
}
