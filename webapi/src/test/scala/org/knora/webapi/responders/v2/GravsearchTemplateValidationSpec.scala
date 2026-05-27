/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import zio.test.*
import zio.test.Assertion.*

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.v2.responder.valuemessages.FileValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.TextFileValueContentV2
import org.knora.webapi.slice.common.PlaceholderIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri

object GravsearchTemplateValidationSpec extends ZIOSpecDefault {

  private val templateIri  = ResourceIri.unsafeFrom("http://rdfh.ch/0001/a-thing")
  private val fileValueIri =
    ValueIri.unsafeFrom("http://rdfh.ch/0001/a-thing/values/mr9i2aUUJolv64V_9hYdTw")

  private def textContent(filename: String, mime: String): TextFileValueContentV2 =
    TextFileValueContentV2(ApiV2Complex, FileValueV2(filename, mime), None)

  def spec = suite("ResourcesResponderV2.validateGravsearchTemplate")(
    test("accepts a real text/plain template") {
      val result = ResourcesResponderV2.validateGravsearchTemplate(
        templateIri,
        fileValueIri,
        textContent("query.txt", "text/plain"),
      )
      assertTrue(result == Right(()))
    },
    test("rejects a placeholder file value with a placeholder-specific message") {
      val result = ResourcesResponderV2.validateGravsearchTemplate(
        templateIri,
        fileValueIri,
        textContent(PlaceholderIri.instance.value, PlaceholderIri.instance.value),
      )
      assert(result)(
        isLeft(
          equalTo(
            s"Gravsearch template $templateIri references a placeholder file value with no real asset yet",
          ),
        ),
      )
    },
    test("rejects a non text/plain MIME with the MIME-mismatch message") {
      val result = ResourcesResponderV2.validateGravsearchTemplate(
        templateIri,
        fileValueIri,
        textContent("query.pdf", "application/pdf"),
      )
      assert(result)(
        isLeft(
          equalTo(
            s"Expected $fileValueIri to be a text file referring to a Gravsearch template, " +
              s"but it has MIME type application/pdf",
          ),
        ),
      )
    },
    test("placeholder check fires before the MIME check") {
      // A placeholder with a coincidentally-text/plain MIME is still rejected as a placeholder,
      // not as a MIME mismatch. Guarantees the placeholder branch is checked first.
      val result = ResourcesResponderV2.validateGravsearchTemplate(
        templateIri,
        fileValueIri,
        textContent(PlaceholderIri.instance.value, "text/plain"),
      )
      assert(result)(
        isLeft(
          equalTo(
            s"Gravsearch template $templateIri references a placeholder file value with no real asset yet",
          ),
        ),
      )
    },
  )
}
