/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3
import zio.*
import zio.json.*
import zio.test.*

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.service.IriConverter

object NotFoundSpec extends ZIOSpecDefault {
  override val spec = suite("NotFoundSpec")(
    test("NotFound.from(ResourceIri) should create a NotFound instance with the correct message and error details") {
      for {
        resourceIri <- ZIO.serviceWithZIO[IriConverter](_.asResourceIri("http://rdfh.ch/0001/abcd1234"))
        actual       = NotFound.from(resourceIri)
      } yield assertTrue(
        actual.toJsonPretty == """{
                                 |  "message" : "The resource with IRI 'http://rdfh.ch/0001/abcd1234' was not found.",
                                 |  "errors" : [
                                 |    {
                                 |      "code" : "resource_not_found",
                                 |      "message" : "The resource with IRI 'http://rdfh.ch/0001/abcd1234' was not found.",
                                 |      "details" : {
                                 |        "resourceIri" : "http://rdfh.ch/0001/abcd1234"
                                 |      }
                                 |    }
                                 |  ]
                                 |}""".stripMargin,
      )
    },
  ).provide(IriConverter.layer, StringFormatter.test)
}
