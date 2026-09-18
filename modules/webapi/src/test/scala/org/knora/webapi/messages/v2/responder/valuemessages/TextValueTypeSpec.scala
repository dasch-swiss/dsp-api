/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.valuemessages

import org.junit.runner.RunWith
import zio.*
import zio.test.*
import zio.test.Assertion.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.slice.common.domain.InternalIri

@RunWith(classOf[DspZTestJUnitRunner])
class TextValueTypeSpec extends ZIOSpecDefault {

  // The three write paths (InsertValueQueryBuilder, ResourcesRepoLive, OntologyTransformer) all derive
  // knora-base:hasTextValueType from this one mapping, so pinning it here pins every write path.
  override def spec: Spec[Any, Any] = suite("TextValueType.hasTextValueTypeIri")(
    test("maps every writable text value type to its knora-base IRI") {
      assertTrue(
        TextValueType.hasTextValueTypeIri(TextValueType.UnformattedText) == KnoraBase.UnformattedText,
        TextValueType.hasTextValueTypeIri(TextValueType.FormattedText) == KnoraBase.FormattedText,
        TextValueType.hasTextValueTypeIri(
          TextValueType.CustomFormattedText(InternalIri("http://www.knora.org/ontology/knora-base#StandardMapping")),
        ) == KnoraBase.CustomFormattedText,
      )
    },
    test("fails loud for UndefinedTextType, which no write path can produce") {
      for {
        exit <- ZIO.attempt(TextValueType.hasTextValueTypeIri(TextValueType.UndefinedTextType)).exit
      } yield assert(exit)(
        fails(
          isSubtype[IllegalArgumentException](hasMessage(containsString("Cannot persist knora-base:hasTextValueType"))),
        ),
      )
    },
  )
}
