/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import org.junit.runner.RunWith
import zio.test.*

import java.util.UUID

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.standoffmessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.slice.admin.domain.service.LegalInfoService
import org.knora.webapi.slice.common.ResourceIri

@RunWith(classOf[DspZTestJUnitRunner])
class ValueContentValidatorSpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  // Text values never reach the legal info service.
  private val validator = ValueContentValidator(LegalInfoService(null, null, null))

  private val sameProject  = "http://rdfh.ch/0001/cCmJT4M1TJO3Q8YpbDFzSg"
  private val otherProject = "http://rdfh.ch/0002/cCmJT4M1TJO3Q8YpbDFzSg"
  private val hasLink      = sf.toSmartIri(OntologyConstants.KnoraBase.StandoffTagHasLink)
  private val otherIriProp = sf.toSmartIri(OntologyConstants.KnoraBase.StandoffTagHasInternalReference)
  private val text         = "Hello"

  private def linkValue(attributes: StandoffTagAttributeV2*): TextValueContentV2 =
    TextValueContentV2(
      ontologySchema = ApiV2Complex,
      maybeValueHasString = Some(text),
      textValueType = TextValueType.FormattedText,
      standoff = Vector(
        StandoffTagV2(
          standoffTagClassIri = sf.toSmartIri(OntologyConstants.KnoraBase.StandoffLinkTag),
          dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
          uuid = UUID.fromString("12345678-90ab-cdef-1234-567890abcdef"),
          originalXMLID = None,
          startPosition = 0,
          endPosition = text.length,
          startIndex = 0,
          attributes = attributes,
        ),
      ),
    )

  private def check(value: TextValueContentV2) =
    validator.validate(
      CreateValueV2(
        resourceIri = ResourceIri.unsafeFrom(sameProject),
        resourceClassIri = sf.toSmartIri("http://api.knora.org/ontology/knora-api/v2#Resource"),
        propertyIri = sf.toSmartIri("http://api.knora.org/ontology/knora-api/v2#hasText"),
        valueContent = value,
      ),
    )

  override def spec: Spec[Any, Nothing] =
    suite("ValueContentValidator standoff links")(
      test("a hasLink attribute to another project is rejected") {
        check(linkValue(StandoffTagIriAttributeV2(hasLink, otherProject))).exit.map(exit => assertTrue(exit.isFailure))
      },
      test("a hasLink attribute within the project is accepted") {
        check(linkValue(StandoffTagIriAttributeV2(hasLink, sameProject))).exit.map(exit => assertTrue(exit.isSuccess))
      },
      test("an IRI attribute other than hasLink pointing to another project is not checked") {
        val value = linkValue(
          StandoffTagIriAttributeV2(hasLink, sameProject),
          StandoffTagIriAttributeV2(otherIriProp, otherProject),
        )
        check(value).exit.map(exit => assertTrue(exit.isSuccess))
      },
    )
}
