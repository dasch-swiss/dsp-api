/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.store.triplestoremessages

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json.*

import dsp.errors.BadRequestException

/**
 * This spec is used to test 'ListAdminMessages'.
 */
class TriplestoreMessagesSpec extends AnyWordSpecLike with Matchers with TriplestoreJsonProtocol {

  "Conversion from case class to JSON and back" should {

    "work for a 'StringLiteralV2' without language iso" in {

      val string = StringLiteralV2.from("stringwithoutlang", None)
      val json   = string.toJson.compactPrint

      json should be("{\"value\":\"stringwithoutlang\"}")

      val converted: StringLiteralV2 = json.parseJson.convertTo[StringLiteralV2]

      converted should be(string)
    }

    "work for a 'StringLiteralV2' with language iso" in {

      val string = StringLiteralV2.from("stringwithlang", Some("de"))
      val json   = string.toJson.compactPrint

      json should be("{\"value\":\"stringwithlang\",\"language\":\"de\"}")

      val converted = json.parseJson.convertTo[StringLiteralV2]

      converted should be(string)
    }
  }

  "Creating a `StringLiteralV2`" should {
    "fail when language iso is given but value is missing" in {
      val caught = intercept[BadRequestException](
        StringLiteralV2.from("", Some("de")),
      )
      assert(caught.getMessage === "String value is missing.")
    }
  }
}
